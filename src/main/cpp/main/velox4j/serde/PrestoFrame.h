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

#ifndef VELOX4J_SERDE_PRESTOFRAME_H_
#define VELOX4J_SERDE_PRESTOFRAME_H_

#include <ostream>
#include <string>
#include <vector>

#include "velox/vector/BaseVector.h"

namespace velox4j {
namespace serde {

// Serializes 'vector' into 'out' as a self-describing frame:
// [int32 typeJsonLen][typeJson][int32 payloadLen][presto payload]. The payload
// is a complete presto page written by PrestoVectorSerde's batch serializer
// with lossless timestamps: it preserves input encodings (dictionary, RLE) and
// serializes each child over bulk row ranges. This replaces the former
// VectorSaver-based debug-oriented encoding across the JNI boundary.
void writePrestoFrame(
    const facebook::velox::VectorPtr& vector,
    std::ostream& out);

// Reads one frame written by writePrestoFrame starting at 'pos' and advances
// 'pos' past the consumed frame. Throws if the input is truncated.
facebook::velox::VectorPtr readPrestoFrame(
    const char*& pos,
    const char* const end,
    facebook::velox::memory::MemoryPool* pool);

// Decodes frames from the binary input until it is exhausted, returning one
// vector per frame.
std::vector<facebook::velox::VectorPtr> decodePrestoFrames(
    const std::string& binary,
    facebook::velox::memory::MemoryPool* pool);

// Re-registers the "ValuesNode" and "ConstantTypedExpr" deserializers with the
// velox deserialization registry so that vector payloads embedded in plan JSON
// ("data" / "valueVector" fields) are read in the presto frame format written
// by writePrestoFrame. Must be called after core::PlanNode::registerSerDe()
// and core::ITypedExpr::registerSerDe() so that the overrides win. Velox core
// itself stays untouched while both sides of the boundary share one format.
void registerPlanVectorSerdeOverrides();

} // namespace serde
} // namespace velox4j

#endif // VELOX4J_SERDE_PRESTOFRAME_H_
