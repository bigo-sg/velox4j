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

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import io.github.zhztheplayer.velox4j.connector.ConnectorSplit;
import io.github.zhztheplayer.velox4j.connector.NexmarkConfiguration;
import io.github.zhztheplayer.velox4j.connector.NexmarkConnectorSplit;
import io.github.zhztheplayer.velox4j.connector.NexmarkGeneratorConfig;
import io.github.zhztheplayer.velox4j.connector.NexmarkParallelSplit;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;

public class NexmarkConnectorSplitSerdeTest {
  @BeforeClass
  public static void beforeClass() {
    Velox4jTests.ensureInitialized();
  }

  @Test
  public void testNexmarkConfiguration() {
    final NexmarkConfiguration in = newSampleNexmarkConfiguration();
    SerdeTests.testJavaBeanRoundTrip(in);
  }

  @Test
  public void testNexmarkGeneratorConfig() {
    final NexmarkGeneratorConfig in = newSampleNexmarkGeneratorConfig(1L, 5000L);
    SerdeTests.testJavaBeanRoundTrip(in);
  }

  @Test
  public void testNexmarkGeneratorConfigSerializesMaxEventsKey() {
    final NexmarkGeneratorConfig config = newSampleNexmarkGeneratorConfig(1L, 5000L);
    final String json = SerdeTests.testJavaBeanRoundTrip(config).getJson();
    final com.fasterxml.jackson.databind.JsonNode tree = Serde.parseTree(json);
    Assert.assertTrue("expected maxEvents key in JSON", tree.has("maxEvents"));
    Assert.assertEquals(5000L, tree.get("maxEvents").asLong());
  }

  @Test
  public void testNexmarkGeneratorConfigDoesNotLeakMaxEventsOrZero() {
    final NexmarkGeneratorConfig config = newSampleNexmarkGeneratorConfig(1L, 5000L);
    final String json = SerdeTests.testJavaBeanRoundTrip(config).getJson();
    final com.fasterxml.jackson.databind.JsonNode tree = Serde.parseTree(json);
    Assert.assertFalse(
        "Java field name maxEventsOrZero must not leak into JSON", tree.has("maxEventsOrZero"));
  }

  @Test
  public void testNexmarkConnectorSplit() {
    final ConnectorSplit split =
        new NexmarkConnectorSplit("connector-nexmark", newSampleNexmarkGeneratorConfig(1L, 5000L));
    SerdeTests.testISerializableRoundTrip(split);
  }

  @Test
  public void testGetSubtaskSplitReturnsPerIndexSplit() {
    final NexmarkConnectorSplit subtask0 =
        new NexmarkConnectorSplit("connector-nexmark", newSampleNexmarkGeneratorConfig(1L, 5000L));
    final NexmarkConnectorSplit subtask1 =
        new NexmarkConnectorSplit(
            "connector-nexmark", newSampleNexmarkGeneratorConfig(5001L, 5000L));
    final NexmarkParallelSplit parallel =
        new NexmarkParallelSplit("connector-nexmark", List.of(subtask0, subtask1));

    final ConnectorSplit s0 = parallel.getSubtaskSplit(0, 2);
    final ConnectorSplit s1 = parallel.getSubtaskSplit(1, 2);

    Assert.assertSame(subtask0, s0);
    Assert.assertSame(subtask1, s1);
  }

  @Test(expected = IllegalStateException.class)
  public void testGetSubtaskSplitFailsOnParallelismMismatch() {
    final NexmarkConnectorSplit subtask0 =
        new NexmarkConnectorSplit("connector-nexmark", newSampleNexmarkGeneratorConfig(1L, 5000L));
    final NexmarkConnectorSplit subtask1 =
        new NexmarkConnectorSplit(
            "connector-nexmark", newSampleNexmarkGeneratorConfig(5001L, 5000L));
    final NexmarkParallelSplit parallel =
        new NexmarkParallelSplit("connector-nexmark", List.of(subtask0, subtask1));

    parallel.getSubtaskSplit(0, 4);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void testGetSubtaskSplitFailsOnBadIndex() {
    final NexmarkConnectorSplit subtask0 =
        new NexmarkConnectorSplit("connector-nexmark", newSampleNexmarkGeneratorConfig(1L, 5000L));
    final NexmarkConnectorSplit subtask1 =
        new NexmarkConnectorSplit(
            "connector-nexmark", newSampleNexmarkGeneratorConfig(5001L, 5000L));
    final NexmarkParallelSplit parallel =
        new NexmarkParallelSplit("connector-nexmark", List.of(subtask0, subtask1));

    parallel.getSubtaskSplit(5, 2);
  }

  @Test(expected = NullPointerException.class)
  public void testGetSubtaskSplitFailsOnNullSubtasks() {
    final NexmarkParallelSplit parallel = new NexmarkParallelSplit("connector-nexmark", null);

    parallel.getSubtaskSplit(0, 1);
  }

  private static NexmarkConfiguration newSampleNexmarkConfiguration() {
    return new NexmarkConfiguration(
        0L,
        1,
        "SQUARE",
        10000000,
        10000000,
        "PER_SECOND",
        600,
        0,
        240,
        false,
        false,
        1,
        3,
        46,
        200,
        500,
        100,
        2,
        4,
        4,
        10L,
        5L,
        0L,
        100,
        1000,
        3L,
        0.1,
        1L);
  }

  private static NexmarkGeneratorConfig newSampleNexmarkGeneratorConfig(
      long firstEventId, long maxEvents) {
    return new NexmarkGeneratorConfig(
        newSampleNexmarkConfiguration(), 1_700_000_000_000L, firstEventId, maxEvents, 1L);
  }
}
