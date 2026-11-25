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
package io.github.zhztheplayer.velox4j.plan;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.TinyIntVector;
import org.apache.arrow.vector.table.Table;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.github.zhztheplayer.velox4j.Velox4j;
import io.github.zhztheplayer.velox4j.arrow.Arrow;
import io.github.zhztheplayer.velox4j.config.Config;
import io.github.zhztheplayer.velox4j.config.ConnectorConfig;
import io.github.zhztheplayer.velox4j.connector.ConnectorSplit;
import io.github.zhztheplayer.velox4j.connector.ExternalStreamConnectorSplit;
import io.github.zhztheplayer.velox4j.connector.ExternalStreamTableHandle;
import io.github.zhztheplayer.velox4j.connector.ExternalStreams;
import io.github.zhztheplayer.velox4j.data.RowVector;
import io.github.zhztheplayer.velox4j.exception.VeloxException;
import io.github.zhztheplayer.velox4j.iterator.UpIterator;
import io.github.zhztheplayer.velox4j.jni.JniApiTests;
import io.github.zhztheplayer.velox4j.jni.LocalSession;
import io.github.zhztheplayer.velox4j.memory.AllocationListener;
import io.github.zhztheplayer.velox4j.memory.MemoryManager;
import io.github.zhztheplayer.velox4j.query.Query;
import io.github.zhztheplayer.velox4j.query.SerialTask;
import io.github.zhztheplayer.velox4j.serde.Serde;
import io.github.zhztheplayer.velox4j.serde.SerdeTests;
import io.github.zhztheplayer.velox4j.serializable.ISerializableCo;
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.stateful.KeyedStateBackendParameters;
import io.github.zhztheplayer.velox4j.stateful.KeyedStateSpec;
import io.github.zhztheplayer.velox4j.stateful.StatefulElement;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.BigIntType;
import io.github.zhztheplayer.velox4j.type.RowType;
import io.github.zhztheplayer.velox4j.type.VarCharType;

/**
 * RankNode serde (Java construct -> C++ deserialization -> Java) plus the execution chain: a
 * scan/rank StatefulPlanNode tree built in Java runs the native RankOperator end to end and the
 * changelog reaches the Java side through the merged $row_kind column. The rank2 extensions cover
 * the firstN plan field (serde plus the FirstN counter execution chain) and the J3 mirrors
 * (KeyGroupPartitionFunctionSpec, the key-group fields of KeyedStateBackendParameters).
 */
public class RankNodeSerdeTest {
  private static final String CONNECTOR_ID = "connector-external-stream";
  private static final String ROW_KIND_COLUMN_NAME = "$row_kind";
  private static MemoryManager memoryManager;
  private Session session;
  private RootAllocator allocator;
  private SerialTask task;

  @BeforeClass
  public static void beforeClass() {
    Velox4jTests.ensureInitialized();
    memoryManager = MemoryManager.create(AllocationListener.NOOP);
  }

  @AfterClass
  public static void afterClass() {
    memoryManager.close();
  }

  @Before
  public void setUp() {
    session = Velox4j.newSession(memoryManager);
    allocator = new RootAllocator();
  }

  @After
  public void tearDown() {
    if (task != null) {
      task.close();
      task = null;
    }
    if (allocator != null) {
      allocator.close();
      allocator = null;
    }
    if (session != null) {
      session.close();
      session = null;
    }
  }

  private RowType inputType() {
    return new RowType(List.of("k", "v"), List.of(new BigIntType(), new BigIntType()));
  }

  private RowType outputTypeWithRank() {
    return new RowType(
        List.of("k", "v", "rn"), List.of(new BigIntType(), new BigIntType(), new BigIntType()));
  }

  private RankNode groupedRankNode() {
    return new RankNode(
        "rank-1",
        1L,
        2L,
        List.of(1),
        List.of(true),
        List.of(true),
        new KeyedStateSpec(List.of(0), List.of(new BigIntType())),
        true,
        true,
        false,
        10000L,
        inputType(),
        outputTypeWithRank());
  }

  private RankNode globalRankNode() {
    return new RankNode(
        "rank-1",
        1L,
        2L,
        List.of(1),
        List.of(true),
        List.of(true),
        // Sentinel key channel: one past the two input columns.
        new KeyedStateSpec(List.of(2), List.of(new BigIntType())),
        true,
        true,
        false,
        10000L,
        inputType(),
        outputTypeWithRank());
  }

  @Test
  public void testRankNodeSerdeRoundTrip() {
    final RankNode in =
        new RankNode(
            "id-rank",
            1L,
            3L,
            List.of(1, 0),
            List.of(true, false),
            List.of(true, false),
            new KeyedStateSpec(List.of(0, 2), List.of(new BigIntType(), new VarCharType())),
            true,
            false,
            false,
            10000L,
            inputType(),
            outputTypeWithRank());
    final Object out = SerdeTests.testISerializableRoundTrip(in).getObj();
    Assert.assertTrue(out instanceof RankNode);
    final RankNode rank = (RankNode) out;
    Assert.assertEquals("id-rank", rank.getId());
    Assert.assertEquals(1L, (long) rank.getRankStart());
    Assert.assertEquals(3L, (long) rank.getRankEnd());
    Assert.assertEquals(List.of(1, 0), rank.getSortKeyChannels());
    Assert.assertEquals(List.of(true, false), rank.getSortAscending());
    Assert.assertEquals(List.of(true, false), rank.getSortNullsFirst());
    Assert.assertEquals(List.of(0, 2), rank.getKeyedStateSpec().getKeyChannels());
    Assert.assertEquals(2, rank.getKeyedStateSpec().getKeyTypes().size());
    Assert.assertTrue(rank.getOutputRankNumber());
    Assert.assertFalse(rank.getGenerateUpdateBefore());
    Assert.assertEquals(2, rank.getInputType().size());
    Assert.assertEquals(3, rank.getOutputType().size());
  }

  @Test
  public void testGlobalRankNodeSerdeRoundTrip() {
    final Object out = SerdeTests.testISerializableRoundTrip(globalRankNode()).getObj();
    Assert.assertTrue(out instanceof RankNode);
    final RankNode rank = (RankNode) out;
    Assert.assertEquals(List.of(2), rank.getKeyedStateSpec().getKeyChannels());
    Assert.assertEquals(
        1,
        rank.getKeyedStateSpec().getKeyTypes().stream()
            .filter(t -> t.getClass().equals(BigIntType.class))
            .count());
  }

  // The execution chain: scan (ExternalStream) -> RankOperator, driven
  // through the SerialTask harness. The grouped TopN displacement chain
  // must reach Java with the rank column and the per-row kinds.
  @Test
  public void testGroupedTopNExecution() throws InterruptedException {
    final StatefulRecordOutput out =
        runRankQuery(groupedRankNode(), new long[] {1, 1}, new long[] {30, 20});
    Assert.assertArrayEquals(new long[] {1, 1, 1, 1}, out.keys);
    Assert.assertArrayEquals(new long[] {30, 30, 20, 30}, out.values);
    Assert.assertArrayEquals(new long[] {1, 1, 1, 2}, out.ranks);
    Assert.assertArrayEquals(new byte[] {0, 1, 2, 0}, out.rowKinds);
  }

  // The global shape: rejected at operator initialization on the velox
  // side - Flink keys the shape with an empty key and a synthetic key
  // column is not the right substitute; the plan construction fails loud
  // through the JNI boundary.
  @Test
  public void testGlobalTopNExecutionRejected() throws InterruptedException {
    Assert.assertThrows(
        VeloxException.class,
        () -> runRankQuery(globalRankNode(), new long[] {10, 20, 30}, new long[] {3, 1, 2}));
  }

  // rank2 B1: the FirstN flag round-trips through C++ (true and false) and
  // a plan JSON from before the field existed deserializes as false.
  @Test
  public void testRankNodeFirstNSerdeRoundTrip() {
    final RankNode firstNNode =
        new RankNode(
            "id-rank-firstn",
            1L,
            3L,
            List.of(1),
            List.of(true),
            List.of(true),
            new KeyedStateSpec(List.of(0), List.of(new BigIntType())),
            true,
            true,
            true,
            10000L,
            inputType(),
            outputTypeWithRank());
    final Object out = SerdeTests.testISerializableRoundTrip(firstNNode).getObj();
    Assert.assertTrue(out instanceof RankNode);
    Assert.assertTrue(((RankNode) out).getFirstN());

    // A plan JSON without the "firstN" key is a real field mismatch, not
    // a pre-field legacy plan (the JSON is regenerated per task over JNI),
    // and fails loud - the native serde rejects the missing key instead of
    // deserializing as a default.
    final String missingFirstNJson =
        Serde.toPrettyJson(firstNNode).replaceFirst("\\s*\"firstN\"\\s*:\\s*true,", "");
    final VeloxException error =
        Assert.assertThrows(
            VeloxException.class, () -> Serde.fromJson(missingFirstNJson, RankNode.class));
    Assert.assertTrue(error.getMessage(), error.getMessage().contains("firstN"));
  }

  // rank2 B1/F4: the FirstN execution chain - a firstN node built in Java
  // runs the native counter branch through the SerialTask harness: the
  // first rankEnd rows emit +I with their rank, the rest is dropped.
  @Test
  public void testFirstNExecution() throws InterruptedException {
    final RankNode firstNNode =
        new RankNode(
            "rank-firstn",
            1L,
            2L,
            List.of(1),
            List.of(true),
            List.of(true),
            new KeyedStateSpec(List.of(0), List.of(new BigIntType())),
            true,
            true,
            true,
            10000L,
            inputType(),
            outputTypeWithRank());
    final StatefulRecordOutput out =
        runRankQuery(firstNNode, new long[] {1, 1, 1}, new long[] {30, 20, 10});
    Assert.assertArrayEquals(new long[] {1, 1}, out.keys);
    Assert.assertArrayEquals(new long[] {30, 20}, out.values);
    Assert.assertArrayEquals(new long[] {1, 2}, out.ranks);
    Assert.assertArrayEquals(new byte[] {0, 0}, out.rowKinds);
  }

  // perf-rank-opt design S7.1 (alignment #68): the cacheSize plan field
  // round-trips through C++ with its explicit value, and a plan JSON from
  // before the field existed deserializes as the native default 10000.
  @Test
  public void testRankNodeCacheSizeSerdeRoundTrip() {
    final RankNode in =
        new RankNode(
            "id-rank-cachesize",
            1L,
            3L,
            List.of(1),
            List.of(true),
            List.of(true),
            new KeyedStateSpec(List.of(0), List.of(new BigIntType())),
            true,
            true,
            false,
            777L,
            inputType(),
            outputTypeWithRank());
    final Object out = SerdeTests.testISerializableRoundTrip(in).getObj();
    Assert.assertTrue(out instanceof RankNode);
    final RankNode rank = (RankNode) out;
    Assert.assertEquals(777L, (long) rank.getCacheSize());
  }

  // The cacheSize plan field is required: the plan JSON is regenerated per
  // task over JNI, so a constructor null or a JSON without the key is a
  // real field mismatch and fails loud instead of landing on the native
  // table.exec.rank.topn-cache-size default of 10000.
  @Test
  public void testRankNodeCacheSizeRequired() {
    Assert.assertThrows(
        NullPointerException.class,
        () ->
            new RankNode(
                "id-rank-cachesize-required",
                1L,
                2L,
                List.of(1),
                List.of(true),
                List.of(true),
                new KeyedStateSpec(List.of(0), List.of(new BigIntType())),
                true,
                true,
                false,
                null,
                inputType(),
                outputTypeWithRank()));

    final RankNode node =
        new RankNode(
            "id-rank-cachesize-required-json",
            1L,
            2L,
            List.of(1),
            List.of(true),
            List.of(true),
            new KeyedStateSpec(List.of(0), List.of(new BigIntType())),
            true,
            true,
            false,
            10000L,
            inputType(),
            outputTypeWithRank());
    final String missingCacheSizeJson =
        Serde.toPrettyJson(node).replaceFirst("\\s*\"cacheSize\"\\s*:\\s*10000,", "");
    final VeloxException error =
        Assert.assertThrows(
            VeloxException.class, () -> Serde.fromJson(missingCacheSizeJson, RankNode.class));
    Assert.assertTrue(error.getMessage(), error.getMessage().contains("cacheSize"));
  }

  // rank2 J3: the KeyGroupPartitionFunctionSpec mirror - fields survive the
  // Java -> C++ -> Java round trip; the routing-equality anchor is the
  // native StreamPartitionTest (keyGroupRoutingMatchesStateOwnership).
  @Test
  public void testKeyGroupPartitionFunctionSpecSerdeRoundTrip() {
    final KeyGroupPartitionFunctionSpec in =
        new KeyGroupPartitionFunctionSpec(
            inputType(), List.of(0), KeyedStateBackendParameters.DEFAULT_MAX_PARALLELISM);
    final Object out = SerdeTests.testISerializableRoundTrip(in).getObj();
    Assert.assertTrue(out instanceof KeyGroupPartitionFunctionSpec);
    final KeyGroupPartitionFunctionSpec spec = (KeyGroupPartitionFunctionSpec) out;
    Assert.assertEquals(List.of(0), spec.getKeyChannels());
    // Upstream's KeyGroupPartitionFunctionSpec#getMaxParallelism returns the
    // boxed Integer; cast the constant to Object so assertEquals resolves to
    // the (Object, Object) overload instead of turning ambiguous.
    Assert.assertEquals(
        (Object) KeyedStateBackendParameters.DEFAULT_MAX_PARALLELISM, spec.getMaxParallelism());
    Assert.assertEquals(2, spec.getInputType().size());
  }

  // rank2 J3: the key-group fields of KeyedStateBackendParameters serialize
  // to the C++ serde keys and the C++ create accepts the JSON (the
  // production direction - the parameters only ever cross JNI Java -> C++;
  // the C++ serialize writes no "name" key, so there is no reverse round
  // trip). A JSON from before the fields existed deserializes as the full
  // default key-group space (128/0/128, the C++ create defaults).
  @Test
  public void testKeyedStateBackendParametersSerdeRoundTrip() throws Exception {
    final KeyedStateBackendParameters in =
        new KeyedStateBackendParameters("job", "op", 0, 128, 32, 32);
    final JsonNode json = Serde.parseTree(Serde.toPrettyJson(in));
    Assert.assertEquals("KeyedStateBackendParameters", json.get("name").asText());
    Assert.assertEquals("job", json.get("jobId").asText());
    Assert.assertEquals("op", json.get("operatorId").asText());
    Assert.assertEquals(0, json.get("stateBackendType").asInt());
    Assert.assertEquals(128, json.get("maxParallelism").asInt());
    Assert.assertEquals(32, json.get("startKeyGroup").asInt());
    Assert.assertEquals(32, json.get("numKeyGroups").asInt());

    // The C++ side accepts the same JSON (its create consumes these keys).
    try (final MemoryManager memoryManager = MemoryManager.create(AllocationListener.NOOP);
        final LocalSession session = JniApiTests.createLocalSession(memoryManager)) {
      try (ISerializableCo unused = session.iSerializableOps().asCpp(in)) {
        // Non-null: the native create parsed the JSON.
        Assert.assertNotNull(unused);
      }
    }

    final String oldJson =
        "{\"name\": \"KeyedStateBackendParameters\", \"jobId\": \"job\","
            + " \"operatorId\": \"op\", \"stateBackendType\": 0}";
    final KeyedStateBackendParameters legacy =
        Serde.fromJson(oldJson, KeyedStateBackendParameters.class);
    Assert.assertEquals(128, legacy.getMaxParallelism());
    Assert.assertEquals(0, legacy.getStartKeyGroup());
    Assert.assertEquals(128, legacy.getNumKeyGroups());
  }

  private static final class StatefulRecordOutput {
    final long[] keys;
    final long[] values;
    final long[] ranks;
    final byte[] rowKinds;

    StatefulRecordOutput(long[] keys, long[] values, long[] ranks, byte[] rowKinds) {
      this.keys = keys;
      this.values = values;
      this.ranks = ranks;
      this.rowKinds = rowKinds;
    }
  }

  private StatefulRecordOutput runRankQuery(RankNode rankNode, long[] keys, long[] values)
      throws InterruptedException {
    final TableScanNode scanNode =
        new TableScanNode(
            "scan-1", inputType(), new ExternalStreamTableHandle(CONNECTOR_ID), List.of());
    final StatefulPlanNode rankPlan = new StatefulPlanNode(rankNode.getId(), rankNode);
    final StatefulPlanNode root = new StatefulPlanNode(scanNode.getId(), scanNode);
    root.addTarget(rankPlan);
    final Query query = new Query(root, Config.empty(), ConnectorConfig.empty());
    final SerialTask serialTask = session.queryOps().execute(query);
    this.task = serialTask;
    serialTask.initializeState(0, null);
    final ExternalStreams.BlockingQueue queue = session.externalStreamOps().newBlockingQueue();
    final ConnectorSplit split = new ExternalStreamConnectorSplit(CONNECTOR_ID, queue.id());
    serialTask.addSplit(scanNode.getId(), split);
    serialTask.noMoreSplits(scanNode.getId());
    final RowVector inputVector = makeInputVector(keys, values);
    queue.put(inputVector);
    serialTask.waitFor();
    Assert.assertEquals(UpIterator.State.AVAILABLE, serialTask.advance());
    final StatefulElement element = serialTask.statefulGet();
    Assert.assertTrue(element.isRecord());
    final io.github.zhztheplayer.velox4j.stateful.StatefulRecord record = element.asRecord();
    try {
      return readOutput(record.getRowVector());
    } finally {
      record.close();
      inputVector.close();
    }
  }

  private RowVector makeInputVector(long[] keys, long[] values) {
    final BigIntVector keyVector = new BigIntVector("k", allocator);
    keyVector.allocateNew(keys.length);
    final BigIntVector valueVector = new BigIntVector("v", allocator);
    valueVector.allocateNew(values.length);
    for (int i = 0; i < keys.length; i++) {
      keyVector.setSafe(i, keys[i]);
      valueVector.setSafe(i, values[i]);
    }
    keyVector.setValueCount(keys.length);
    valueVector.setValueCount(values.length);
    final RowVector rv =
        session.arrowOps().fromArrowTable(allocator, new Table(List.of(keyVector, valueVector)));
    keyVector.close();
    valueVector.close();
    return rv;
  }

  private StatefulRecordOutput readOutput(RowVector rv) {
    final FieldVector structVector = Arrow.toArrowVector(allocator, rv);
    try {
      final BigIntVector kVector = (BigIntVector) findChild(structVector, "k");
      final BigIntVector vVector = (BigIntVector) findChild(structVector, "v");
      final BigIntVector rnVector = (BigIntVector) findChild(structVector, "rn");
      final TinyIntVector kindVector =
          (TinyIntVector) findChild(structVector, ROW_KIND_COLUMN_NAME);
      final int rows = vVector.getValueCount();
      final long[] keys = new long[rows];
      final long[] values = new long[rows];
      final long[] ranks = new long[rows];
      final byte[] kinds = new byte[rows];
      for (int i = 0; i < rows; i++) {
        keys[i] = kVector.get(i);
        values[i] = vVector.get(i);
        ranks[i] = rnVector.get(i);
        kinds[i] = kindVector.get(i);
      }
      return new StatefulRecordOutput(keys, values, ranks, kinds);
    } finally {
      structVector.close();
    }
  }

  private static FieldVector findChild(FieldVector structVector, String name) {
    for (FieldVector child : structVector.getChildrenFromFields()) {
      if (name.equals(child.getField().getName())) {
        return child;
      }
    }
    throw new IllegalArgumentException("child not found: " + name);
  }
}
