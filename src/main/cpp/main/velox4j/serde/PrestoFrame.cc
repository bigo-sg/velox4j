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

#include "velox4j/serde/PrestoFrame.h"

#include <folly/json/json.h>

#include "velox/common/encode/Base64.h"
#include "velox/common/memory/ByteStream.h"
#include "velox/common/memory/Memory.h"
#include "velox/common/serialization/DeserializationRegistry.h"
#include "velox/common/serialization/Serializable.h"
#include "velox/core/Expressions.h"
#include "velox/core/PlanNode.h"
#include "velox/serializers/PrestoSerializer.h"
#include "velox/type/Type.h"
#include "velox/type/Variant.h"
#include "velox/vector/ComplexVector.h"

namespace velox4j {
namespace serde {

using namespace facebook::velox;

namespace {

serializer::presto::PrestoVectorSerde::PrestoOptions losslessOptions() {
  serializer::presto::PrestoVectorSerde::PrestoOptions options;
  // Presto's shuffle format truncates timestamps to millisecond precision by
  // default; keep the full (seconds, nanos) precision instead.
  options.useLosslessTimestamp = true;
  return options;
}

// Same as core ValuesNode::create, except that the "data" field is read in
// the presto frame format: the Java side builds the field via
// BaseVectors.serializeAll, which routes through the JNI baseVectorSerialize
// implementation.
core::PlanNodePtr createValuesNode(const folly::dynamic& obj, void* context) {
  std::vector<core::PlanNodePtr> sources;
  if (obj.count("sources")) {
    sources = ISerializable::deserialize<std::vector<core::PlanNode>>(
        obj["sources"], context);
  }
  VELOX_CHECK_EQ(0, sources.size(), "ValuesNode expects no sources");

  auto* pool = static_cast<memory::MemoryPool*>(context);
  std::vector<RowVectorPtr> values;
  // The Java side base64-encodes the frame bytes into the JSON text field.
  for (auto& vector : decodePrestoFrames(
           encoding::Base64::decode(obj["data"].asString()), pool)) {
    values.push_back(std::dynamic_pointer_cast<RowVector>(std::move(vector)));
  }

  return std::make_shared<core::ValuesNode>(
      obj["id"].asString(),
      std::move(values),
      obj["parallelizable"].asBool(),
      obj["repeatTimes"].asInt());
}

// Same as core ConstantTypedExpr::create, except that the "valueVector" field
// is read in the presto frame format.
core::TypedExprPtr createConstantTypedExpr(
    const folly::dynamic& obj,
    void* context) {
  if (obj.count("value")) {
    auto type = ISerializable::deserialize<Type>(obj["type"]);
    return std::make_shared<core::ConstantTypedExpr>(
        std::move(type), variant::create(obj["value"]));
  }

  auto* pool = static_cast<memory::MemoryPool*>(context);
  auto vectors = decodePrestoFrames(
      encoding::Base64::decode(obj["valueVector"].asString()), pool);
  VELOX_CHECK_EQ(1, vectors.size(), "Expected exactly one constant vector");
  return std::make_shared<core::ConstantTypedExpr>(std::move(vectors[0]));
}

} // namespace

void writePrestoFrame(const VectorPtr& vector, std::ostream& out) {
  static const auto kSerde =
      std::make_shared<serializer::presto::PrestoVectorSerde>();
  const auto options = losslessOptions();

  const std::string typeJson = folly::toJson(vector->type()->serialize());
  const int32_t typeJsonLen = static_cast<int32_t>(typeJson.size());
  out.write(reinterpret_cast<const char*>(&typeJsonLen), sizeof(int32_t));
  out.write(typeJson.data(), typeJsonLen);

  // The batch serializer preserves input encodings (dictionary, RLE) and
  // serializes each child over one bulk row range. serializeSingleColumn
  // instead builds flat streams and decomposes row vectors into single-row
  // ranges, which flattens dictionaries and walks every row separately.
  // The batch path requires a RowVector, so non-row vectors ride in a
  // single-column wrapper row (mirrored on the read side below).
  RowVectorPtr row;
  if (vector->type()->isRow()) {
    row = std::dynamic_pointer_cast<RowVector>(vector);
    VELOX_CHECK_NOT_NULL(row, "ROW-typed vector is not a RowVector");
  } else {
    const auto wrapperType = ROW({"c0"}, {vector->type()});
    row = std::make_shared<RowVector>(
        vector->pool(),
        wrapperType,
        BufferPtr(nullptr),
        vector->size(),
        std::vector<VectorPtr>{vector});
  }

  auto serializer = kSerde->createBatchSerializer(
      &memory::deprecatedSharedLeafPool(), &options);
  std::ostringstream payloadStream;
  OStreamOutputStream outputStream(&payloadStream);
  Scratch scratch;
  IndexRange ranges[1] = {IndexRange{0, row->size()}};
  serializer->serialize(
      row, folly::Range<const IndexRange*>(ranges, 1), scratch, &outputStream);

  const std::string payload = payloadStream.str();
  const int32_t payloadLen = static_cast<int32_t>(payload.size());
  out.write(reinterpret_cast<const char*>(&payloadLen), sizeof(int32_t));
  out.write(payload.data(), payloadLen);
}

VectorPtr readPrestoFrame(
    const char*& pos,
    const char* const end,
    memory::MemoryPool* pool) {
  static const auto kSerde =
      std::make_shared<serializer::presto::PrestoVectorSerde>();
  const auto options = losslessOptions();

  const auto readInt32 = [&](int32_t& value) {
    VELOX_CHECK_LE(
        pos + sizeof(int32_t), end, "Truncated framed vector payload");
    std::memcpy(&value, pos, sizeof(int32_t));
    pos += sizeof(int32_t);
  };

  int32_t typeJsonLen = 0;
  readInt32(typeJsonLen);
  VELOX_CHECK_LE(pos + typeJsonLen, end, "Truncated framed vector payload");
  const auto type =
      Type::create(folly::parseJson(std::string(pos, typeJsonLen)));
  pos += typeJsonLen;

  int32_t payloadLen = 0;
  readInt32(payloadLen);
  VELOX_CHECK_LE(pos + payloadLen, end, "Truncated framed vector payload");
  ByteRange byteRange{
      reinterpret_cast<uint8_t*>(const_cast<char*>(pos)),
      static_cast<int64_t>(payloadLen),
      0};
  BufferInputStream stream(std::vector<ByteRange>{byteRange});
  pos += payloadLen;

  VectorPtr vector;
  if (type->isRow()) {
    RowVectorPtr row;
    kSerde->deserialize(&stream, pool, asRowType(type), &row, &options);
    vector = row;
  } else {
    // Mirror of the wrapper row written by writePrestoFrame for non-row
    // types: deserialize as a single-column row and unwrap the child.
    const auto wrapperType = ROW({"c0"}, {type});
    RowVectorPtr row;
    kSerde->deserialize(&stream, pool, wrapperType, &row, &options);
    VELOX_CHECK_NOT_NULL(row, "Missing wrapper row for non-row vector");
    vector = row->childAt(0);
  }
  return vector;
}

std::vector<VectorPtr> decodePrestoFrames(
    const std::string& binary,
    memory::MemoryPool* pool) {
  std::vector<VectorPtr> vectors;
  const char* pos = binary.data();
  const char* const end = binary.data() + binary.size();
  while (pos < end) {
    vectors.push_back(readPrestoFrame(pos, end, pool));
  }
  return vectors;
}

void registerPlanVectorSerdeOverrides() {
  auto& registry = DeserializationWithContextRegistryForSharedPtr();
  registry.Register("ValuesNode", createValuesNode);
  registry.Register("ConstantTypedExpr", createConstantTypedExpr);
}

} // namespace serde
} // namespace velox4j
