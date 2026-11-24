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
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import io.github.zhztheplayer.velox4j.memory.AllocationListener;
import io.github.zhztheplayer.velox4j.memory.MemoryManager;
import io.github.zhztheplayer.velox4j.plan.DeduplicateNode;
import io.github.zhztheplayer.velox4j.plan.HashPartitionFunctionSpec;
import io.github.zhztheplayer.velox4j.plan.StreamRankNode;
import io.github.zhztheplayer.velox4j.stateful.KeyedStateBackendParameters;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.RowType;

public class DeduplicateNodeSerdeTest {
  private static MemoryManager memoryManager;

  @BeforeClass
  public static void beforeClass() throws Exception {
    Velox4jTests.ensureInitialized();
    memoryManager = MemoryManager.create(AllocationListener.NOOP);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    memoryManager.close();
  }

  @Test
  public void testDeduplicateNodeRoundTrip() {
    final RowType outputType = SerdeTests.newSampleOutputType();
    final DeduplicateNode node =
        new DeduplicateNode("id-1", List.of(), outputType, 0L, 0, true, true, false);
    SerdeTests.testISerializableRoundTrip(node);
  }

  @Test
  public void testStreamRankNodeWithHashPartitionSpecRoundTrip() {
    final RowType outputType = SerdeTests.newSampleOutputType();
    final DeduplicateNode dedup =
        new DeduplicateNode("id-1", List.of(), outputType, 0L, -1, true, true, true);
    final StreamRankNode rank =
        new StreamRankNode(
            "id-2",
            List.of(),
            new HashPartitionFunctionSpec(outputType, List.of(0)),
            dedup,
            outputType);
    SerdeTests.testISerializableRoundTrip(rank);
  }

  @Test
  public void testKeyedStateBackendParametersKeyGroups() {
    final KeyedStateBackendParameters in =
        new KeyedStateBackendParameters("job", "op", 0, 128, 32, 32);
    final JsonNode json = Serde.parseTree(Serde.toPrettyJson(in));
    Assert.assertEquals("KeyedStateBackendParameters", json.get("name").asText());
    Assert.assertEquals(128, json.get("maxParallelism").asInt());
    Assert.assertEquals(32, json.get("startKeyGroup").asInt());
    Assert.assertEquals(32, json.get("numKeyGroups").asInt());

    final KeyedStateBackendParameters legacy =
        Serde.fromJson(
            "{\"name\": \"KeyedStateBackendParameters\", \"jobId\": \"job\","
                + " \"operatorId\": \"op\", \"stateBackendType\": 0}",
            KeyedStateBackendParameters.class);
    Assert.assertEquals(128, legacy.getMaxParallelism());
    Assert.assertEquals(0, legacy.getStartKeyGroup());
    Assert.assertEquals(128, legacy.getNumKeyGroups());
  }
}
