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

#include <gtest/gtest.h>

#include "velox/common/base/tests/GTestUtils.h"
#include "velox/vector/tests/utils/VectorTestBase.h"
#include "velox4j/test/Init.h"

namespace velox4j {
namespace {

using namespace facebook::velox;
using namespace facebook::velox::test;

class PrestoFrameTest : public testing::Test, public VectorTestBase {
 protected:
  static void SetUpTestCase() {
    testingEnsureInitializedForSpark();
  }

  VectorPtr roundTrip(const VectorPtr& input) {
    std::ostringstream out;
    serde::writePrestoFrame(input, out);
    const auto bytes = out.str();
    auto frames = serde::decodePrestoFrames(bytes, pool());
    VELOX_CHECK_EQ(frames.size(), 1);
    return frames.front();
  }
};

TEST_F(PrestoFrameTest, rowVectorRoundTrip) {
  const auto input = makeRowVector(
      {"id", "name", "score"},
      {makeFlatVector<int64_t>({1, 2, 3}),
       makeFlatVector<std::string>({"a", "bb", "ccc"}),
       makeFlatVector<double>({1.5, 2.5, 3.5})});
  const auto output = roundTrip(input);
  ASSERT_TRUE(output->type()->isRow());
  assertEqualVectors(input, output);
}

TEST_F(PrestoFrameTest, dictionaryInputRoundTrip) {
  const auto base = makeFlatVector<int64_t>({1, 2, 3, 4});
  const auto dictionary = BaseVector::wrapInDictionary(
      nullptr, makeIndices(std::vector<vector_size_t>{3, 1, 3, 1}), 4, base);
  const auto input = makeRowVector(
      {"id", "dict"}, {makeFlatVector<int64_t>({1, 2, 3, 4}), dictionary});

  const auto output = roundTrip(input);
  ASSERT_EQ(output->size(), 4);
  assertEqualVectors(input, output);
}

TEST_F(PrestoFrameTest, nonRowVectorUsesWrapperFrame) {
  const auto input = makeFlatVector<int64_t>({10, 20, 30});
  const auto output = roundTrip(input);
  ASSERT_FALSE(output->type()->isRow());
  assertEqualVectors(input, output);
}

TEST_F(PrestoFrameTest, emptyVectorRoundTrip) {
  const auto input =
      makeRowVector({"id"}, {makeFlatVector<int64_t>(std::vector<int64_t>{})});
  const auto output = roundTrip(input);
  ASSERT_EQ(output->size(), 0);
  assertEqualVectors(input, output);
}

TEST_F(PrestoFrameTest, multipleFramesDecodeInOrder) {
  const auto first = makeRowVector({"id"}, {makeFlatVector<int64_t>({1, 2})});
  const auto second =
      makeRowVector({"id"}, {makeFlatVector<int64_t>({3, 4, 5})});

  std::ostringstream out;
  serde::writePrestoFrame(first, out);
  serde::writePrestoFrame(second, out);

  const auto frames = serde::decodePrestoFrames(out.str(), pool());
  ASSERT_EQ(frames.size(), 2);
  assertEqualVectors(first, frames[0]);
  assertEqualVectors(second, frames[1]);
}

TEST_F(PrestoFrameTest, truncatedFrameFails) {
  const auto input =
      makeRowVector({"id"}, {makeFlatVector<int64_t>({1, 2, 3})});
  std::ostringstream out;
  serde::writePrestoFrame(input, out);
  const auto bytes = out.str();
  const char* pos = bytes.data();
  EXPECT_THROW(
      serde::readPrestoFrame(pos, bytes.data() + bytes.size() - 1, pool()),
      VeloxException);
}

TEST_F(PrestoFrameTest, timestampNanosLossless) {
  const auto input = makeRowVector(
      {"ts"},
      {makeFlatVector<Timestamp>(
          {Timestamp(1, 123456789), Timestamp(2, 987654321)})});
  const auto output = roundTrip(input);
  auto* timestamps =
      output->as<RowVector>()->childAt(0)->asFlatVector<Timestamp>();
  EXPECT_EQ(timestamps->valueAt(0), Timestamp(1, 123456789));
  EXPECT_EQ(timestamps->valueAt(1), Timestamp(2, 987654321));
}

} // namespace
} // namespace velox4j
