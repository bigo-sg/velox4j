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

#include "DownIterator.h"
#include "velox4j/jni/JniCommon.h"
#include "velox4j/lifecycle/ObjectStore.h"

namespace velox4j {
using namespace facebook::velox;

namespace {
const char* kClassName = "io/github/zhztheplayer/velox4j/iterator/DownIterator";
} // namespace

void DownIteratorJniWrapper::mapFields() {}

const char* DownIteratorJniWrapper::getCanonicalName() const {
  return kClassName;
}

void DownIteratorJniWrapper::initialize(JNIEnv* env) {
  JavaClass::setClass(env);

  cacheMethod(env, "advance", kTypeInt, nullptr);
  cacheMethod(env, "waitFor", kTypeVoid, nullptr);
  cacheMethod(env, "get", kTypeLong, nullptr);
  cacheMethod(env, "close", kTypeVoid, nullptr);

  registerNativeMethods(env);
}

DownIterator::DownIterator(JNIEnv* env, jobject ref) : ExternalStream() {
  ref_ = env->NewGlobalRef(ref);
  waitExecutor_ = std::make_unique<folly::IOThreadPoolExecutor>(1);
}

DownIterator::~DownIterator() {
  try {
    close();
    getLocalJNIEnv()->DeleteGlobalRef(ref_);
  } catch (const std::exception& ex) {
    LOG(WARNING)
        << "Unable to destroy the global reference to the Java side down iterator: "
        << ex.what();
  }
}

std::optional<RowVectorPtr> DownIterator::read(ContinueFuture& future) {
  VELOX_CHECK(!closed_);
  // Stateful task will cal operators one by one, so need not to set the future.
  // Only need to return data or null.
  const State state = advance();
  switch (state) {
    case State::AVAILABLE: {
      auto vector = get();
      VELOX_CHECK_NOT_NULL(vector);
      return vector;
    }
    case State::BLOCKED: {
      return std::nullopt;
    }
    case State::FINISHED: {
      return nullptr;
    }
  }
  VELOX_FAIL(
      "Unrecognizable state: {}", std::to_string(static_cast<int32_t>(state)));
  return std::nullopt;
}

void DownIterator::close() {
  bool expected = false;
  if (!closed_.compare_exchange_strong(expected, true)) {
    return;
  }
  auto* env = getLocalJNIEnv();
  static const auto* clazz = jniClassRegistry()->get(kClassName);
  static jmethodID methodId = clazz->getMethod("close");
  env->CallVoidMethod(ref_, methodId);
  checkException(env);
  waitExecutor_->join();
}

void DownIterator::wait() {
  auto* env = getLocalJNIEnv();
  static const auto* clazz = jniClassRegistry()->get(kClassName);
  static jmethodID methodId = clazz->getMethod("waitFor");
  env->CallVoidMethod(ref_, methodId);
  checkException(env);
}

DownIterator::State DownIterator::advance() {
  auto* env = getLocalJNIEnv();
  static const auto* clazz = jniClassRegistry()->get(kClassName);
  static jmethodID methodId = clazz->getMethod("advance");
  const auto state = static_cast<State>(env->CallIntMethod(ref_, methodId));
  checkException(env);
  return state;
}

RowVectorPtr DownIterator::get() {
  auto* env = getLocalJNIEnv();
  static const auto* clazz = jniClassRegistry()->get(kClassName);
  static jmethodID methodId = clazz->getMethod("get");
  const jlong rvId = env->CallLongMethod(ref_, methodId);
  checkException(env);
  return ObjectStore::retrieve<RowVector>(rvId);
}
} // namespace velox4j
