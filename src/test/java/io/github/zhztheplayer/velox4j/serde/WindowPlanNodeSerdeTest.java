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
package io.github.zhztheplayer.velox4j.serde;

import java.util.List;

import org.junit.*;

import io.github.zhztheplayer.velox4j.aggregate.AggregateStep;
import io.github.zhztheplayer.velox4j.expression.FieldAccessTypedExpr;
import io.github.zhztheplayer.velox4j.plan.AggregationNode;
import io.github.zhztheplayer.velox4j.plan.EmptyNode;
import io.github.zhztheplayer.velox4j.plan.HashPartitionFunctionSpec;
import io.github.zhztheplayer.velox4j.plan.StreamWindowAggregationNode;
import io.github.zhztheplayer.velox4j.plan.StreamWindowPartitionFunctionSpec;
import io.github.zhztheplayer.velox4j.stateful.KeyedStateSpec;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.BigIntType;
import io.github.zhztheplayer.velox4j.type.RowType;

/**
 * Round trips the TVF window aggregation plan node and its satellite serializables through both the
 * Java JSON serde and the native C++ deserializer: windowType/size/step/offset ride the node for
 * all four window types (HOP=0, TUMBLE=1, SESSION=2, CUMULATIVE=3), and the KeyedStateSpec must
 * survive as a registered ISerializable.
 */
public class WindowPlanNodeSerdeTest {
  @BeforeClass
  public static void beforeClass() {
    Velox4jTests.ensureInitialized();
  }

  // BigInt-only columns: the C++ serde normalizes TIMESTAMP precision, so a
  // pure serde test keeps types out of the comparison surface.
  private static final RowType INPUT_TYPE =
      new RowType(
          List.of("bidder", "ts", "price"),
          List.of(new BigIntType(), new BigIntType(), new BigIntType()));

  private static StreamWindowAggregationNode newNode(int windowType) {
    final FieldAccessTypedExpr key = FieldAccessTypedExpr.create(new BigIntType(), "bidder");
    final AggregationNode aggregation =
        new AggregationNode(
            "agg-id",
            AggregateStep.SINGLE,
            List.of(key),
            List.of(),
            List.of("cnt"),
            List.of(SerdeTests.newSampleAggregate()),
            false,
            List.of(new EmptyNode(INPUT_TYPE)),
            null,
            List.of());
    return new StreamWindowAggregationNode(
        "window-agg-id",
        aggregation,
        new HashPartitionFunctionSpec(INPUT_TYPE, List.of(0)),
        new StreamWindowPartitionFunctionSpec(INPUT_TYPE, 1, 3600000L, 60000L, 0L, windowType),
        new KeyedStateSpec(List.of(0, 1), List.of(new BigIntType(), new BigIntType())),
        "UTC",
        3600000L,
        60000L,
        0L,
        windowType,
        new RowType(
            List.of("bidder", "w_start", "w_end", "cnt"),
            List.of(new BigIntType(), new BigIntType(), new BigIntType(), new BigIntType())),
        true,
        1,
        1,
        2);
  }

  @Test
  public void testStreamWindowAggregationNodeHop() {
    SerdeTests.testISerializableRoundTrip(newNode(0));
  }

  @Test
  public void testStreamWindowAggregationNodeTumble() {
    SerdeTests.testISerializableRoundTrip(newNode(1));
  }

  @Test
  public void testStreamWindowAggregationNodeSession() {
    // SESSION: gap rides the size field, step/offset unused (0).
    SerdeTests.testISerializableRoundTrip(newNode(2));
  }

  @Test
  public void testStreamWindowAggregationNodeCumulative() {
    SerdeTests.testISerializableRoundTrip(newNode(3));
  }

  @Test
  public void testStreamWindowPartitionFunctionSpec() {
    SerdeTests.testISerializableRoundTrip(
        new StreamWindowPartitionFunctionSpec(INPUT_TYPE, 1, 3600000L, 60000L, 0L, 0));
  }

  @Test
  public void testSessionGapRidesSizeField() {
    // The SESSION translation maps gap onto the size field; the node
    // must carry it without any special casing.
    final StreamWindowAggregationNode node = newNode(2);
    Assert.assertEquals(3600000L, (long) node.getSize());
    Assert.assertEquals(2, (int) node.getWindowType());
  }
}
