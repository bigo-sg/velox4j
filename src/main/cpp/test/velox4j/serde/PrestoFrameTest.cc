/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "velox4j/serde/PrestoFrame.h"
#include <gtest/gtest.h>
#include <velox/vector/tests/utils/VectorTestBase.h>
#include "velox4j/test/Init.h"

namespace velox4j {
using namespace facebook::velox;
using namespace facebook::velox::test;

class PrestoFrameTest : public testing::Test, public test::VectorTestBase {
 protected:
  static void SetUpTestCase() {
    testingEnsureInitializedForSpark();
  }

  // Native counterpart of the Java-side round-trip tests: the comparison is
  // velox's own recursive value equality, not a string dump after an Arrow
  // conversion.
  VectorPtr roundTripOnce(const VectorPtr& vector) {
    std::ostringstream out;
    serde::writePrestoFrame(vector, out);
    auto decoded = serde::decodePrestoFrames(out.str(), pool());
    EXPECT_EQ(1, decoded.size());
    return decoded.size() == 1 ? decoded[0] : nullptr;
  }

  void testRoundTrip(const VectorPtr& vector) {
    assertEqualVectors(vector, roundTripOnce(vector));
  }

  // Shuffle-edge-shaped row: flat, nullable, dictionary-encoded (random
  // index stream) and constant columns in one row vector.
  RowVectorPtr makeShuffleRow(vector_size_t rows) {
    constexpr vector_size_t kDictCardinality = 8;
    auto baseVarchar = makeFlatVector<std::string>(
        {"red",
         "blue",
         "green",
         "yellow",
         "purple",
         "orange",
         "white",
         "black"});
    auto dictVarchar = BaseVector::wrapInDictionary(
        nullptr,
        makeIndices(
            rows,
            [](auto row) { return (row * 7 + row / 3) % kDictCardinality; }),
        rows,
        baseVarchar);
    auto dictInt = BaseVector::wrapInDictionary(
        nullptr,
        makeIndices(
            rows,
            [](auto row) { return (row * 5 + row / 7) % kDictCardinality; }),
        rows,
        makeFlatVector<int32_t>({0, 1, 2, 3, 4, 5, 6, 7}));

    std::vector<std::string> flatVarchar;
    for (vector_size_t i = 0; i < rows; i++) {
      flatVarchar.push_back("row_" + std::to_string(i));
    }

    std::vector<VectorPtr> children = {
        makeFlatVector<int64_t>(rows, [](auto row) { return row * 31 + 7; }),
        makeFlatVector<double>(
            rows,
            [](auto row) { return row * 0.25; },
            [](auto row) { return row % 9 == 0; }),
        makeFlatVector<std::string>(flatVarchar),
        dictInt,
        dictVarchar,
        BaseVector::createConstant(BIGINT(), int64_t{123}, rows, pool()),
    };
    return makeRowVector(children);
  }
};

TEST_F(PrestoFrameTest, roundTripRowVector) {
  testRoundTrip(makeShuffleRow(64));
  testRoundTrip(makeShuffleRow(1'000));
}

// The batch serializer keeps the input encodings where the format has
// something to gain: variable-width dictionaries (varchar) and constants are
// rebuilt as such on the read side. Dictionaries over small fixed-width
// types (e.g. int32, where a dictionary index is as wide as the value) are
// flattened by design. The Java-side equality tests cannot see any of this
// (the Arrow conversion may flatten dictionaries before comparing).
TEST_F(PrestoFrameTest, roundTripPreservesEncodings) {
  auto row = makeShuffleRow(64);
  auto decoded = roundTripOnce(row);
  ASSERT_NE(nullptr, decoded);
  const auto& decodedRow = decoded->as<RowVector>();
  ASSERT_EQ(
      VectorEncoding::Simple::DICTIONARY, decodedRow->childAt(4)->encoding());
  ASSERT_EQ(
      VectorEncoding::Simple::CONSTANT, decodedRow->childAt(5)->encoding());
}

// Non-row top-level vectors ride in a single-column wrapper row; the read
// side must unwrap them back to the original type and encoding.
TEST_F(PrestoFrameTest, roundTripNonRowVector) {
  testRoundTrip(makeFlatVector<int32_t>({1, 2, 3, 4, 5}));
  testRoundTrip(BaseVector::wrapInDictionary(
      nullptr,
      makeIndices(16, [](auto row) { return (row * 3 + 1) % 4; }),
      16,
      makeFlatVector<std::string>({"a", "bb", "ccc", "dddd"})));
  testRoundTrip(makeNullableFlatVector<bool>(
      {true, std::nullopt, false, true, std::nullopt}));
}

// useLosslessTimestamp on both sides: sub-millisecond nanos must survive,
// including through the wrapper row for the non-row top-level vector.
TEST_F(PrestoFrameTest, roundTripTimestampNanos) {
  testRoundTrip(makeNullableFlatVector<Timestamp>(
      {Timestamp{1, 123'456'789},
       std::nullopt,
       Timestamp{-1, 987'654'321},
       Timestamp{0, 999'999'999}}));
  testRoundTrip(makeRowVector(std::vector<VectorPtr>{
      makeNullableFlatVector<Timestamp>(
          {Timestamp{12, 17'123'456},
           Timestamp{-1, 17'123'456},
           Timestamp{0, 17'123'456}}),
  }));
}

TEST_F(PrestoFrameTest, roundTripMultipleFrames) {
  auto first = makeShuffleRow(32);
  auto second = makeRowVector(std::vector<VectorPtr>{
      makeNullableFlatVector<int32_t>({1, std::nullopt, 3}),
      makeFlatVector<std::string>({"x", "y", "z"}),
  });

  std::ostringstream out;
  serde::writePrestoFrame(first, out);
  serde::writePrestoFrame(second, out);

  auto decoded = serde::decodePrestoFrames(out.str(), pool());
  ASSERT_EQ(2, decoded.size());
  assertEqualVectors(first, decoded[0]);
  assertEqualVectors(second, decoded[1]);
}

TEST_F(PrestoFrameTest, decodeEmptyInput) {
  auto decoded = serde::decodePrestoFrames("", pool());
  ASSERT_TRUE(decoded.empty());
}

// Canonical (sorted-key) type headers make frames byte-deterministic: the
// same type must emit identical bytes across writePrestoFrame calls and
// across type instances (a decode/encode cycle parses a fresh instance).
TEST_F(PrestoFrameTest, framesAreByteDeterministic) {
  auto vector = makeShuffleRow(32);

  std::ostringstream first;
  serde::writePrestoFrame(vector, first);
  std::ostringstream second;
  serde::writePrestoFrame(vector, second);
  ASSERT_EQ(first.str(), second.str());

  auto decoded = serde::decodePrestoFrames(first.str(), pool());
  ASSERT_EQ(1, decoded.size());
  std::ostringstream third;
  serde::writePrestoFrame(decoded[0], third);
  ASSERT_EQ(first.str(), third.str());
}

} // namespace velox4j
