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

import com.fasterxml.jackson.databind.JsonNode;

import io.github.zhztheplayer.velox4j.plan.EmptyNode;
import io.github.zhztheplayer.velox4j.plan.HashPartitionFunctionSpec;
import io.github.zhztheplayer.velox4j.plan.NestedLoopJoinNode;
import io.github.zhztheplayer.velox4j.plan.PlanNode;
import io.github.zhztheplayer.velox4j.plan.StreamWindowJoinNode;
import io.github.zhztheplayer.velox4j.serializable.ISerializableRegistry;
import io.github.zhztheplayer.velox4j.serde.Serde;
import io.github.zhztheplayer.velox4j.type.IntegerType;
import io.github.zhztheplayer.velox4j.type.RowType;

import org.junit.Assert;
import org.junit.Test;

public class StreamWindowJoinNodeSerdeTest {
  @Test
  public void testRoundTrip() throws Exception {
    ISerializableRegistry.registerAll();
    final RowType inputType =
        new RowType(
            List.of("k", "ws", "we", "v"),
            List.of(new IntegerType(), new IntegerType(), new IntegerType(), new IntegerType()));
    final RowType outputType =
        new RowType(
            List.of("k", "ws", "we", "v", "k0", "ws0", "we0", "v0"),
            List.of(
                new IntegerType(), new IntegerType(), new IntegerType(), new IntegerType(),
                new IntegerType(), new IntegerType(), new IntegerType(), new IntegerType()));
    final PlanNode probe =
        new NestedLoopJoinNode(
            "id-probe",
            io.github.zhztheplayer.velox4j.join.JoinType.INNER,
            null,
            new EmptyNode(inputType),
            new EmptyNode(inputType),
            outputType);
    final StreamWindowJoinNode node =
        new StreamWindowJoinNode(
            "id-1",
            new EmptyNode(inputType),
            new EmptyNode(inputType),
            new HashPartitionFunctionSpec(inputType, List.of(0, 2)),
            new HashPartitionFunctionSpec(inputType, List.of(0, 2)),
            (NestedLoopJoinNode) probe,
            outputType,
            128,
            2,
            2,
            List.of(0, 2),
            List.of(0, 2),
            0);
    final String json = Serde.toPrettyJson(node);
    final JsonNode tree = Serde.parseTree(json);
    Assert.assertTrue(tree.has("leftKeyChannels"));
    Assert.assertTrue(tree.has("rightKeyChannels"));
    Assert.assertTrue(tree.has("shiftTimeZone"));
    // Deserializing a full plan tree in pure Java needs the abstract-base
    // polymorphic path for PartitionFunctionSpec fields, which the baseline
    // serde does not register (production serializes Java -> JSON -> the
    // C++ plan parser, never the reverse); this test pins the wire format.
    Assert.assertEquals(2, tree.get("leftKeyChannels").size());
    Assert.assertEquals(0, tree.get("leftKeyChannels").get(0).asInt());
    Assert.assertEquals(2, tree.get("leftKeyChannels").get(1).asInt());
    Assert.assertEquals(2, tree.get("rightKeyChannels").size());
    Assert.assertEquals(0, tree.get("rightKeyChannels").get(0).asInt());
    Assert.assertEquals(2, tree.get("rightKeyChannels").get(1).asInt());
    Assert.assertEquals(0, tree.get("shiftTimeZone").asInt());
    Assert.assertEquals(2, tree.get("leftWindowEndIndex").asInt());
    Assert.assertEquals(128, tree.get("numPartitions").asInt());
    Assert.assertEquals(
        "StreamWindowJoinNode", tree.get("name").asText());
  }

}
