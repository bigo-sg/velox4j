/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "JniWrapper.h"
#include <folly/json/json.h>
#include <velox/common/memory/Memory.h>
#include <velox/core/PlanNode.h>
#include <velox/exec/TableWriter.h>
#include <velox/experimental/stateful/StreamElement.h>

#include "JniCommon.h"
#include "JniError.h"
#include "velox4j/arrow/Arrow.h"
#include "velox4j/connector/ExternalStream.h"
#include "velox4j/eval/Evaluator.h"
#include "velox4j/iterator/BlockingQueue.h"
#include "velox4j/iterator/DownIterator.h"
#include "velox4j/lifecycle/Session.h"
#include "velox4j/query/QueryExecutor.h"
#include "velox4j/query/StatefulQueryExecutor.h"
#include "velox4j/serde/PrestoFrame.h"

namespace velox4j {
using namespace facebook::velox;

namespace {
const char* kClassName = "io/github/zhztheplayer/velox4j/jni/JniWrapper";
const bool stateful = true;

class JniNativeCallbackBridge : public stateful::NativeCallbackBridge {
 public:
  JniNativeCallbackBridge(JNIEnv* env, jobject callbackTarget) {
    VELOX_CHECK_NOT_NULL(callbackTarget, "Native callback target is null");
    callbackTarget_ = env->NewGlobalRef(callbackTarget);
    VELOX_CHECK_NOT_NULL(
        callbackTarget_, "Failed to create native callback target global ref");

    jclass callbackTargetClass = env->GetObjectClass(callbackTarget);
    VELOX_CHECK_NOT_NULL(
        callbackTargetClass, "Failed to get native callback target class");
    onProcessingTime_ =
        env->GetMethodID(callbackTargetClass, "onProcessingTime", "(J)V");
    env->DeleteLocalRef(callbackTargetClass);
    VELOX_CHECK_NOT_NULL(
        onProcessingTime_,
        "Failed to find NativeCallbackTarget.onProcessingTime(long)");
  }

  ~JniNativeCallbackBridge() override {
    if (callbackTarget_ != nullptr) {
      JNIEnv* env = getLocalJNIEnv();
      env->DeleteGlobalRef(callbackTarget_);
      callbackTarget_ = nullptr;
    }
  }

  void onProcessingTime(int64_t timestamp) override {
    JNIEnv* env = getLocalJNIEnv();
    env->CallVoidMethod(callbackTarget_, onProcessingTime_, timestamp);
    checkException(env);
  }

 private:
  jobject callbackTarget_{nullptr};
  jmethodID onProcessingTime_{nullptr};
};

Session* sessionOf(JNIEnv* env, jobject javaThis) {
  static const auto* clazz = jniClassRegistry()->get(kClassName);
  static jmethodID methodId = clazz->getMethod("sessionId");
  const jlong sessionId = env->CallLongMethod(javaThis, methodId);
  checkException(env);
  return ObjectStore::retrieve<Session>(sessionId).get();
}

// Checkpoint records are raw bytes (the keyed-state checkpoint stream), not
// text: a JNI string is modified UTF-8 and cannot carry them losslessly - a
// 0x00 byte ends the string and stray bytes are dropped by the decoder,
// which corrupted every savepoint restore through the old jstring channel.
// The records therefore cross as one flat byte array: int32 record count,
// then per record int32 length + payload, little-endian like the checkpoint
// stream itself.
void appendLeInt32(std::string& out, int32_t value) {
  const auto bits = static_cast<uint32_t>(value);
  for (int i = 0; i < 4; ++i) {
    out.push_back(static_cast<char>((bits >> (8 * i)) & 0xFF));
  }
}

jbyteArray toFramedByteArray(
    JNIEnv* env,
    const std::vector<std::string>& records) {
  std::string framed;
  appendLeInt32(framed, static_cast<int32_t>(records.size()));
  for (const auto& record : records) {
    appendLeInt32(framed, static_cast<int32_t>(record.size()));
    framed.append(record.data(), record.size());
  }
  jbyteArray array = env->NewByteArray(static_cast<jsize>(framed.size()));
  VELOX_CHECK_NOT_NULL(array, "Failed to allocate the checkpoint-record array");
  env->SetByteArrayRegion(
      array,
      0,
      static_cast<jsize>(framed.size()),
      reinterpret_cast<const jbyte*>(framed.data()));
  return array;
}

std::vector<std::string> fromFramedByteArray(JNIEnv* env, jbyteArray framed) {
  std::vector<std::string> records;
  if (framed == nullptr) {
    return records;
  }
  const auto safe = getByteArrayElementsSafe(env, framed);
  const auto* data = reinterpret_cast<const char*>(safe.elems());
  const auto size = static_cast<size_t>(safe.length());
  size_t offset = 0;
  const auto readLeInt32 = [&]() {
    VELOX_CHECK_LE(
        offset + sizeof(int32_t),
        size,
        "Corrupt checkpoint-record framing: truncated length field");
    uint32_t value = 0;
    for (size_t i = 0; i < sizeof(int32_t); ++i) {
      value |=
          static_cast<uint32_t>(static_cast<unsigned char>(data[offset + i]))
          << (8 * i);
    }
    offset += sizeof(int32_t);
    return value;
  };
  const auto count = readLeInt32();
  records.reserve(count);
  for (uint32_t i = 0; i < count; ++i) {
    const auto length = readLeInt32();
    VELOX_CHECK_LE(
        offset + length,
        size,
        "Corrupt checkpoint-record framing: record exceeds the array");
    records.emplace_back(data + offset, length);
    offset += length;
  }
  VELOX_CHECK_EQ(
      offset,
      size,
      "Corrupt checkpoint-record framing: trailing bytes after the last record");
  return records;
}

jlong createEvaluator(JNIEnv* env, jobject javaThis, jstring evalJson) {
  JNI_METHOD_START
  auto session = sessionOf(env, javaThis);
  spotify::jni::JavaString jExprJson{env, evalJson};
  auto evaluator =
      std::make_shared<Evaluator>(session->memoryManager(), jExprJson.get());
  return sessionOf(env, javaThis)->objectStore()->save(evaluator);
  JNI_METHOD_END(-1L)
}

jlong evaluatorEval(
    JNIEnv* env,
    jobject javaThis,
    jlong evaluatorId,
    jlong selectivityVectorId,
    jlong rvId) {
  JNI_METHOD_START
  auto evaluator = ObjectStore::retrieve<Evaluator>(evaluatorId);
  auto selectivityVector =
      ObjectStore::retrieve<SelectivityVector>(selectivityVectorId);
  auto input = ObjectStore::retrieve<RowVector>(rvId);
  return sessionOf(env, javaThis)
      ->objectStore()
      ->save(evaluator->eval(*selectivityVector, *input));
  JNI_METHOD_END(-1L)
}

jlong createQueryExecutor(JNIEnv* env, jobject javaThis, jstring queryJson) {
  JNI_METHOD_START
  auto session = sessionOf(env, javaThis);
  spotify::jni::JavaString jQueryJson{env, queryJson};
  auto querySerdePool = session->memoryManager()->getVeloxPool(
      fmt::format("Query Serde Memory Pool"), memory::MemoryPool::Kind::kLeaf);
  // Keep the pool alive until the task is finished.
  auto queryDynamic = folly::parseJson(jQueryJson.get());
  auto query = ISerializable::deserialize<Query>(queryDynamic, querySerdePool);
  // auto exec = std::make_shared<QueryExecutor>(session->memoryManager(),
  // query);
  if (stateful) {
    auto exec = std::make_shared<StatefulQueryExecutor>(
        session->memoryManager(), query);
    return sessionOf(env, javaThis)->objectStore()->save(exec);
  } else {
    auto exec =
        std::make_shared<QueryExecutor>(session->memoryManager(), query);
    return sessionOf(env, javaThis)->objectStore()->save(exec);
  }
  JNI_METHOD_END(-1L)
}

jlong queryExecutorExecute(
    JNIEnv* env,
    jobject javaThis,
    jlong queryExecutorId) {
  JNI_METHOD_START
  if (stateful) {
    auto exec = ObjectStore::retrieve<StatefulQueryExecutor>(queryExecutorId);
    return sessionOf(env, javaThis)
        ->objectStore()
        ->save<StatefulSerialTask>(exec->execute());
  } else {
    auto exec = ObjectStore::retrieve<QueryExecutor>(queryExecutorId);
    return sessionOf(env, javaThis)
        ->objectStore()
        ->save<SerialTask>(exec->execute());
  }
  JNI_METHOD_END(-1L)
}

jlong upIteratorGet(JNIEnv* env, jobject javaThis, jlong itrId) {
  JNI_METHOD_START
  auto itr = ObjectStore::retrieve<UpIterator>(itrId);
  return sessionOf(env, javaThis)->objectStore()->save(itr->get());
  JNI_METHOD_END(-1L)
}

jobject statefulTaskGet(JNIEnv* env, jobject javaThis, jlong itrId) {
  JNI_METHOD_START
  auto itr = ObjectStore::retrieve<StatefulSerialTask>(itrId);
  std::string outNodeId;
  stateful::StreamElementPtr element = itr->statefulGet();
  if (element->isWatermark()) {
    auto watermark = std::static_pointer_cast<stateful::Watermark>(element);

    jclass resultClass = env->FindClass(
        "io/github/zhztheplayer/velox4j/stateful/StatefulWatermark");
    jmethodID constructor =
        env->GetMethodID(resultClass, "<init>", "(Ljava/lang/String;J)V");

    jstring id = env->NewStringUTF(watermark->nodeId().c_str());
    jobject result =
        env->NewObject(resultClass, constructor, id, watermark->timestamp());

    env->DeleteLocalRef(id);
    env->DeleteLocalRef(resultClass);
    return result;
  } else if (element->isWatermarkStatus()) {
    auto watermarkStatus =
        std::static_pointer_cast<stateful::WatermarkStatus>(element);

    jclass resultClass = env->FindClass(
        "io/github/zhztheplayer/velox4j/stateful/StatefulWatermarkStatus");
    jmethodID constructor =
        env->GetMethodID(resultClass, "<init>", "(Ljava/lang/String;Z)V");

    jstring id = env->NewStringUTF(watermarkStatus->nodeId().c_str());
    jobject result =
        env->NewObject(resultClass, constructor, id, watermarkStatus->idle());

    env->DeleteLocalRef(id);
    env->DeleteLocalRef(resultClass);
    return result;
  } else {
    VELOX_CHECK(element->isRecord());
    auto record = std::static_pointer_cast<stateful::StreamRecord>(element);
    auto merged = record->toMergedRowVector();
    jlong rvId = sessionOf(env, javaThis)->objectStore()->save(merged);

    jclass resultClass = env->FindClass(
        "io/github/zhztheplayer/velox4j/stateful/StatefulRecord");
    jmethodID constructor =
        env->GetMethodID(resultClass, "<init>", "(Ljava/lang/String;JJZI)V");

    jstring id = env->NewStringUTF(record->nodeId().c_str());
    jobject result = env->NewObject(
        resultClass,
        constructor,
        id,
        rvId,
        record->timestamp(),
        record->hasTimestamp(),
        record->key());

    env->DeleteLocalRef(id);
    env->DeleteLocalRef(resultClass);
    return result;
  }
  JNI_METHOD_END(nullptr)
}

void bindNativeCallbackTarget(
    JNIEnv* env,
    jobject javaThis,
    jlong itrId,
    jobject callbackTarget) {
  JNI_METHOD_START
  auto itr = ObjectStore::retrieve<StatefulSerialTask>(itrId);
  if (callbackTarget == nullptr) {
    itr->setNativeCallbackBridge(nullptr);
    return;
  }
  itr->setNativeCallbackBridge(
      std::make_shared<JniNativeCallbackBridge>(env, callbackTarget));
  JNI_METHOD_END()
}

void unbindNativeCallbackTarget(JNIEnv* env, jobject javaThis, jlong itrId) {
  JNI_METHOD_START
  auto itr = ObjectStore::retrieve<StatefulSerialTask>(itrId);
  itr->setNativeCallbackBridge(nullptr);
  JNI_METHOD_END()
}

void notifyIndexedWatermark(
    JNIEnv* env,
    jobject javaThis,
    jlong itrId,
    jlong watermark,
    jint index) {
  JNI_METHOD_START
  auto itr = ObjectStore::retrieve<StatefulSerialTask>(itrId);
  itr->notifyWatermark(watermark, index);
  JNI_METHOD_END()
}

void notifyWatermark(
    JNIEnv* env,
    jobject javaThis,
    jlong itrId,
    jlong watermark) {
  JNI_METHOD_START
  auto itr = ObjectStore::retrieve<StatefulSerialTask>(itrId);
  itr->notifyWatermark(watermark);
  JNI_METHOD_END()
}

void notifyIndexedWatermarkStatus(
    JNIEnv* env,
    jobject javaThis,
    jlong itrId,
    jboolean idle,
    jint index) {
  JNI_METHOD_START
  auto itr = ObjectStore::retrieve<StatefulSerialTask>(itrId);
  itr->notifyWatermarkStatus(idle, index);
  JNI_METHOD_END()
}

void notifyWatermarkStatus(
    JNIEnv* env,
    jobject javaThis,
    jlong itrId,
    jboolean idle) {
  JNI_METHOD_START
  auto itr = ObjectStore::retrieve<StatefulSerialTask>(itrId);
  itr->notifyWatermarkStatus(idle);
  JNI_METHOD_END()
}

void initializeState(
    JNIEnv* env,
    jobject javaThis,
    jlong itrId,
    jlong context,
    jstring keyedStateBackendConfigString,
    jbyteArray checkpointRecords) {
  JNI_METHOD_START
  auto itr = ObjectStore::retrieve<StatefulSerialTask>(itrId);
  spotify::jni::JavaString jTypeJson{env, keyedStateBackendConfigString};
  std::vector<std::string> records =
      fromFramedByteArray(env, checkpointRecords);
  itr->initializeState(context, jTypeJson.get(), std::move(records));
  JNI_METHOD_END()
}

jbyteArray
snapshotState(JNIEnv* env, jobject javaThis, jlong itrId, jlong context) {
  JNI_METHOD_START
  auto itr = ObjectStore::retrieve<StatefulSerialTask>(itrId);
  std::vector<std::string> snapshots = itr->snapshotState(context);
  return toFramedByteArray(env, snapshots);
  JNI_METHOD_END(nullptr)
}

jbyteArray snapshotSourceState(JNIEnv* env, jobject javaThis, jlong itrId) {
  JNI_METHOD_START
  auto itr = ObjectStore::retrieve<StatefulSerialTask>(itrId);
  std::vector<std::string> states = itr->snapshotSourceState();
  return toFramedByteArray(env, states);
  JNI_METHOD_END(nullptr)
}

jobject notifyCheckpointComplete(
    JNIEnv* env,
    jobject javaThis,
    jlong itrId,
    jlong checkpointId) {
  JNI_METHOD_START
  auto itr = ObjectStore::retrieve<StatefulSerialTask>(itrId);
  std::vector<std::string> committed =
      itr->notifyCheckpointComplete(checkpointId);
  jclass stringClass = env->FindClass("java/lang/String");
  jobjectArray result =
      env->NewObjectArray(committed.size(), stringClass, nullptr);
  for (size_t i = 0; i < committed.size(); ++i) {
    jstring str = env->NewStringUTF(committed[i].c_str());
    env->SetObjectArrayElement(result, i, str);
    env->DeleteLocalRef(str);
  }
  env->DeleteLocalRef(stringClass);
  return result;
  JNI_METHOD_END(nullptr)
}

void notifyCheckpointAborted(
    JNIEnv* env,
    jobject javaThis,
    jlong itrId,
    jlong checkpointId) {
  JNI_METHOD_START
  auto itr = ObjectStore::retrieve<StatefulSerialTask>(itrId);
  itr->notifyCheckpointAborted(checkpointId);
  JNI_METHOD_END()
}

jlong createExternalStreamFromDownIterator(
    JNIEnv* env,
    jobject javaThis,
    jobject itrRef) {
  JNI_METHOD_START
  auto es = std::make_shared<DownIterator>(env, itrRef);
  return sessionOf(env, javaThis)->objectStore()->save(es);
  JNI_METHOD_END(-1L)
}

jlong createBlockingQueue(JNIEnv* env, jobject javaThis) {
  JNI_METHOD_START
  auto queue = std::make_shared<BlockingQueue>();
  return sessionOf(env, javaThis)->objectStore()->save(queue);
  JNI_METHOD_END(-1L)
}

jlong createEmptyBaseVector(JNIEnv* env, jobject javaThis, jstring typeJson) {
  JNI_METHOD_START
  // TODO Session memory pool.
  auto session = sessionOf(env, javaThis);
  auto serdePool = session->memoryManager()->getVeloxPool(
      "Serde Memory Pool", memory::MemoryPool::Kind::kLeaf);
  spotify::jni::JavaString jTypeJson{env, typeJson};
  auto dynamic = folly::parseJson(jTypeJson.get());
  auto type = ISerializable::deserialize<Type>(dynamic, serdePool);
  auto vectorPool = session->memoryManager()->getVeloxPool(
      "BaseVector Memory Pool", memory::MemoryPool::Kind::kLeaf);
  auto vector = BaseVector::create(type, 0, vectorPool);
  return session->objectStore()->save(vector);
  JNI_METHOD_END(-1L)
}

jlong arrowToBaseVector(
    JNIEnv* env,
    jobject javaThis,
    jlong cSchema,
    jlong cArray) {
  JNI_METHOD_START
  // TODO Session memory pool.
  auto session = sessionOf(env, javaThis);
  auto pool = session->memoryManager()->getVeloxPool(
      "Arrow Import Memory Pool", memory::MemoryPool::Kind::kLeaf);
  auto vector = fromArrowToBaseVector(
      pool,
      reinterpret_cast<struct ArrowSchema*>(cSchema),
      reinterpret_cast<struct ArrowArray*>(cArray));
  return session->objectStore()->save(vector);
  JNI_METHOD_END(-1L)
}

jlongArray
baseVectorDeserialize(JNIEnv* env, jobject javaThis, jbyteArray serialized) {
  JNI_METHOD_START
  auto session = sessionOf(env, javaThis);
  const jsize len = env->GetArrayLength(serialized);
  std::string binary(static_cast<size_t>(len), '\0');
  env->GetByteArrayRegion(
      serialized, 0, len, reinterpret_cast<jbyte*>(binary.data()));
  auto pool = session->memoryManager()->getVeloxPool(
      "Decoding Memory Pool", memory::MemoryPool::Kind::kLeaf);
  std::vector<ObjectHandle> vids{};
  // Presto columnar replacement for the former VectorSaver-based decoding;
  // reads the frames written by serde::writePrestoFrame.
  for (const auto& vector : serde::decodePrestoFrames(binary, pool)) {
    const ObjectHandle vid = session->objectStore()->save(vector);
    vids.push_back(vid);
  }
  const jsize nVectors = static_cast<jsize>(vids.size());
  const jlongArray& out = env->NewLongArray(nVectors);
  env->SetLongArrayRegion(out, 0, nVectors, vids.data());
  return out;
  JNI_METHOD_END(nullptr)
}

jlong baseVectorWrapInConstant(
    JNIEnv* env,
    jobject javaThis,
    jlong vid,
    jint length,
    jint index) {
  JNI_METHOD_START
  auto vector = ObjectStore::retrieve<BaseVector>(vid);
  auto constVector = BaseVector::wrapInConstant(length, index, vector);
  return sessionOf(env, javaThis)->objectStore()->save(constVector);
  JNI_METHOD_END(-1)
}

jlong baseVectorSlice(
    JNIEnv* env,
    jobject javaThis,
    jlong vid,
    jint offset,
    jint length) {
  JNI_METHOD_START
  auto vector = ObjectStore::retrieve<BaseVector>(vid);
  auto slicedVector = vector->slice(offset, length);
  return sessionOf(env, javaThis)->objectStore()->save(slicedVector);
  JNI_METHOD_END(-1)
}

jlong baseVectorLoadedVector(JNIEnv* env, jobject javaThis, jlong vid) {
  JNI_METHOD_START
  auto vector = ObjectStore::retrieve<BaseVector>(vid);
  auto loadedVector = BaseVector::loadedVectorShared(vector);
  return sessionOf(env, javaThis)->objectStore()->save(loadedVector);
  JNI_METHOD_END(-1)
}

jlong createSelectivityVector(JNIEnv* env, jobject javaThis, jint length) {
  JNI_METHOD_START
  auto vector =
      std::make_shared<SelectivityVector>(static_cast<vector_size_t>(length));
  return sessionOf(env, javaThis)->objectStore()->save(vector);
  JNI_METHOD_END(-1)
}

jstring tableWriteTraitsOutputTypeWithAggregationNode(
    JNIEnv* env,
    jobject javaThis,
    jstring aggregationNodeJson) {
  JNI_METHOD_START
  auto session = sessionOf(env, javaThis);
  spotify::jni::JavaString jJson{env, aggregationNodeJson};
  auto dynamic = folly::parseJson(jJson.get());
  auto serdePool = session->memoryManager()->getVeloxPool(
      "Serde Memory Pool", memory::MemoryPool::Kind::kLeaf);
  auto aggregationNode = std::const_pointer_cast<core::AggregationNode>(
      ISerializable::deserialize<core::AggregationNode>(dynamic, serdePool));
  auto type = exec::TableWriteTraits::outputType(aggregationNode);
  auto serializedDynamic = type->serialize();
  auto typeJson = folly::toPrettyJson(serializedDynamic);
  return env->NewStringUTF(typeJson.data());
  JNI_METHOD_END(nullptr)
}

jlong iSerializableAsCpp(JNIEnv* env, jobject javaThis, jstring json) {
  JNI_METHOD_START
  auto session = sessionOf(env, javaThis);
  auto serdePool = session->memoryManager()->getVeloxPool(
      "Serde Memory Pool", memory::MemoryPool::Kind::kLeaf);
  spotify::jni::JavaString jJson{env, json};
  auto dynamic = folly::parseJson(jJson.get());
  auto deserialized = std::const_pointer_cast<ISerializable>(
      ISerializable::deserialize<ISerializable>(dynamic, serdePool));
  return session->objectStore()->save(deserialized);
  JNI_METHOD_END(-1)
}

jlong variantAsCpp(JNIEnv* env, jobject javaThis, jstring json) {
  JNI_METHOD_START
  auto session = sessionOf(env, javaThis);
  spotify::jni::JavaString jJson{env, json};
  auto dynamic = folly::parseJson(jJson.get());
  auto deserialized = variant::create(dynamic);
  return session->objectStore()->save(std::make_shared<variant>(deserialized));
  JNI_METHOD_END(-1)
}

class ExternalStreamAsUpIterator : public UpIterator {
 public:
  explicit ExternalStreamAsUpIterator(const std::shared_ptr<ExternalStream>& es)
      : es_(es) {}

  State advance() override {
    VELOX_CHECK_NULL(pending_);
    ContinueFuture future = ContinueFuture::makeEmpty();
    auto out = es_->read(future);
    if (out == std::nullopt) {
      VELOX_CHECK(future.valid());
      // Do not wait for the future to be fulfilled, just return.
      return State::BLOCKED;
    }
    VELOX_CHECK(!future.valid());
    if (out == nullptr) {
      return State::FINISHED;
    }
    pending_ = out.value();
    return State::AVAILABLE;
  }

  void wait() override {
    VELOX_CHECK_NULL(pending_);
    VELOX_NYI("Not implemented: {}", __func__);
  }

  RowVectorPtr get() override {
    VELOX_CHECK_NOT_NULL(
        pending_,
        "ExternalStreamAsUpIterator: No pending row vector to return. Make "
        "sure the iterator is available via member function advance() first");
    auto out = pending_;
    pending_ = nullptr;
    return out;
  }

 private:
  const std::shared_ptr<ExternalStream> es_;
  RowVectorPtr pending_{nullptr};
};

jlong createUpIteratorWithExternalStream(
    JNIEnv* env,
    jobject javaThis,
    jlong id) {
  JNI_METHOD_START
  auto es = ObjectStore::retrieve<ExternalStream>(id);
  return sessionOf(env, javaThis)
      ->objectStore()
      ->save(std::make_shared<ExternalStreamAsUpIterator>(es));
  JNI_METHOD_END(-1L)
}
} // namespace

void JniWrapper::mapFields() {}

const char* JniWrapper::getCanonicalName() const {
  return kClassName;
}

void JniWrapper::initialize(JNIEnv* env) {
  JavaClass::setClass(env);

  cacheMethod(env, "sessionId", kTypeLong, nullptr);
  addNativeMethod(
      "createEvaluator",
      (void*)createEvaluator,
      kTypeLong,
      kTypeString,
      nullptr);
  addNativeMethod(
      "evaluatorEval",
      (void*)evaluatorEval,
      kTypeLong,
      kTypeLong,
      kTypeLong,
      kTypeLong,
      nullptr);
  addNativeMethod(
      "createQueryExecutor",
      (void*)createQueryExecutor,
      kTypeLong,
      kTypeString,
      nullptr);
  addNativeMethod(
      "queryExecutorExecute",
      (void*)queryExecutorExecute,
      kTypeLong,
      kTypeLong,
      nullptr);
  addNativeMethod(
      "upIteratorGet", (void*)upIteratorGet, kTypeLong, kTypeLong, nullptr);
  addNativeMethod(
      "statefulTaskGet",
      (void*)statefulTaskGet,
      "io/github/zhztheplayer/velox4j/stateful/StatefulElement",
      kTypeLong,
      nullptr);
  addNativeMethod(
      "bindNativeCallbackTarget",
      (void*)bindNativeCallbackTarget,
      kTypeVoid,
      kTypeLong,
      "io/github/zhztheplayer/velox4j/stateful/NativeCallbackTarget",
      nullptr);
  addNativeMethod(
      "unbindNativeCallbackTarget",
      (void*)unbindNativeCallbackTarget,
      kTypeVoid,
      kTypeLong,
      nullptr);
  addNativeMethod(
      "notifyIndexedWatermark",
      (void*)notifyIndexedWatermark,
      kTypeVoid,
      kTypeLong,
      kTypeLong,
      kTypeInt,
      nullptr);
  addNativeMethod(
      "notifyWatermark",
      (void*)notifyWatermark,
      kTypeVoid,
      kTypeLong,
      kTypeLong,
      nullptr);
  addNativeMethod(
      "notifyIndexedWatermarkStatus",
      (void*)notifyIndexedWatermarkStatus,
      kTypeVoid,
      kTypeLong,
      kTypeBool,
      kTypeInt,
      nullptr);
  addNativeMethod(
      "notifyWatermarkStatus",
      (void*)notifyWatermarkStatus,
      kTypeVoid,
      kTypeLong,
      kTypeBool,
      nullptr);
  addNativeMethod(
      "initializeState",
      (void*)initializeState,
      kTypeVoid,
      kTypeLong,
      kTypeLong,
      kTypeString,
      kTypeArray(kTypeByte),
      nullptr);
  addNativeMethod(
      "snapshotState",
      (void*)snapshotState,
      kTypeArray(kTypeByte),
      kTypeLong,
      kTypeLong,
      nullptr);
  addNativeMethod(
      "snapshotSourceState",
      (void*)snapshotSourceState,
      kTypeArray(kTypeByte),
      kTypeLong,
      nullptr);
  addNativeMethod(
      "notifyCheckpointComplete",
      (void*)notifyCheckpointComplete,
      "[Ljava/lang/String;",
      kTypeLong,
      kTypeLong,
      nullptr);
  addNativeMethod(
      "notifyCheckpointAborted",
      (void*)notifyCheckpointAborted,
      kTypeVoid,
      kTypeLong,
      kTypeLong,
      nullptr);
  addNativeMethod(
      "createExternalStreamFromDownIterator",
      (void*)createExternalStreamFromDownIterator,
      kTypeLong,
      "io/github/zhztheplayer/velox4j/iterator/DownIterator",
      nullptr);
  addNativeMethod(
      "createBlockingQueue", (void*)createBlockingQueue, kTypeLong, nullptr);
  addNativeMethod(
      "createEmptyBaseVector",
      (void*)createEmptyBaseVector,
      kTypeLong,
      kTypeString,
      nullptr);
  addNativeMethod(
      "arrowToBaseVector",
      (void*)arrowToBaseVector,
      kTypeLong,
      kTypeLong,
      kTypeLong,
      nullptr);
  addNativeMethod(
      "baseVectorDeserialize",
      (void*)baseVectorDeserialize,
      kTypeArray(kTypeLong),
      kTypeArray(kTypeByte),
      nullptr);
  addNativeMethod(
      "baseVectorWrapInConstant",
      (void*)baseVectorWrapInConstant,
      kTypeLong,
      kTypeLong,
      kTypeInt,
      kTypeInt,
      nullptr);
  addNativeMethod(
      "baseVectorSlice",
      (void*)baseVectorSlice,
      kTypeLong,
      kTypeLong,
      kTypeInt,
      kTypeInt,
      nullptr);
  addNativeMethod(
      "baseVectorLoadedVector",
      (void*)baseVectorLoadedVector,
      kTypeLong,
      kTypeLong,
      nullptr);
  addNativeMethod(
      "createSelectivityVector",
      (void*)createSelectivityVector,
      kTypeLong,
      kTypeInt,
      nullptr);
  addNativeMethod(
      "tableWriteTraitsOutputTypeWithAggregationNode",
      (void*)tableWriteTraitsOutputTypeWithAggregationNode,
      kTypeString,
      kTypeString,
      nullptr);
  addNativeMethod(
      "iSerializableAsCpp",
      (void*)iSerializableAsCpp,
      kTypeLong,
      kTypeString,
      nullptr);
  addNativeMethod(
      "variantAsCpp", (void*)variantAsCpp, kTypeLong, kTypeString, nullptr);
  addNativeMethod(
      "createUpIteratorWithExternalStream",
      (void*)createUpIteratorWithExternalStream,
      kTypeLong,
      kTypeLong,
      nullptr);

  registerNativeMethods(env);
}

} // namespace velox4j
