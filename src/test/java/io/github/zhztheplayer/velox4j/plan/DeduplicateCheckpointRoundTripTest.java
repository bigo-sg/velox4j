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

import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.TinyIntVector;
import org.apache.arrow.vector.VarBinaryVector;
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
import io.github.zhztheplayer.velox4j.iterator.UpIterator;
import io.github.zhztheplayer.velox4j.memory.AllocationListener;
import io.github.zhztheplayer.velox4j.memory.MemoryManager;
import io.github.zhztheplayer.velox4j.query.Query;
import io.github.zhztheplayer.velox4j.query.SerialTask;
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.stateful.StatefulElement;
import io.github.zhztheplayer.velox4j.stateful.StatefulRecord;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.BigIntType;
import io.github.zhztheplayer.velox4j.type.RowType;
import io.github.zhztheplayer.velox4j.type.VarbinaryType;

/**
 * Round-trip of Deduplicate state through the real JNI checkpoint channel. The checkpoint bytes are
 * not text: a valid stream contains 0x00 and 0x80 bytes, and the payload column below adds bytes
 * from the modified-UTF-8 danger zone.
 */
public class DeduplicateCheckpointRoundTripTest {
  private static final String CONNECTOR_ID = "connector-external-stream";
  private static final String ROW_KIND_COLUMN_NAME = "$row_kind";
  private static final byte[] HOSTILE_PAYLOAD = {
    (byte) 0xC0, (byte) 0x80, (byte) 0xED, (byte) 0xA0, (byte) 0x80, (byte) 0xFF, (byte) 0xFE
  };
  private static MemoryManager memoryManager;
  private Session session;
  private RootAllocator allocator;

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
    if (allocator != null) {
      allocator.close();
      allocator = null;
    }
    if (session != null) {
      session.close();
      session = null;
    }
  }

  @Test
  public void testCheckpointRoundTripPreservesDedupState() throws InterruptedException {
    final StreamRankNode plan = dedupPlan();

    final TaskHarness first = new TaskHarness(plan);
    final TaskHarness continuous = new TaskHarness(plan);
    try {
      assertOutputsEqual(first.feed(inputBatch1()), continuous.feed(inputBatch1()));

      final byte[][] records = first.task.snapshotState(1L);
      Assert.assertEquals("one record per stateful operator chain", 1, records.length);
      final byte[] record = records[0];
      Assert.assertTrue(
          "checkpoint record must contain 0x00 bytes (key-group labels)",
          indexOf(record, (byte) 0x00) >= 0);
      Assert.assertTrue(
          "checkpoint record must contain the invalid-start byte 0x80 (maxParallelism label)",
          indexOf(record, (byte) 0x80) >= 0);
      Assert.assertTrue(
          "checkpoint record must contain the planted hostile payload bytes",
          indexOfSubsequence(record, HOSTILE_PAYLOAD) >= 0);

      final TaskHarness restored = new TaskHarness(plan);
      try {
        restored.task.initializeState(0L, null, records);
        assertOutputsEqual(restored.feed(inputBatch2()), continuous.feed(inputBatch2()));

        final byte[][] restoredRecords = restored.task.snapshotState(2L);
        final byte[][] continuousRecords = continuous.task.snapshotState(2L);
        Assert.assertEquals(continuousRecords.length, restoredRecords.length);
        for (int i = 0; i < continuousRecords.length; i++) {
          Assert.assertArrayEquals(
              "re-serialized checkpoint must be byte-identical after restore",
              continuousRecords[i],
              restoredRecords[i]);
        }
      } finally {
        restored.close();
      }
    } finally {
      first.close();
      continuous.close();
    }
  }

  @Test
  public void testNullCheckpointRecordsOnFreshStartBehaveLikeEmpty() throws InterruptedException {
    final StreamRankNode plan = dedupPlan();
    final TaskHarness emptyStart = new TaskHarness(plan, false);
    final TaskHarness nullStart = new TaskHarness(plan, true);
    try {
      assertOutputsEqual(emptyStart.feed(inputBatch1()), nullStart.feed(inputBatch1()));
      final byte[][] emptyRecords = emptyStart.task.snapshotState(1L);
      final byte[][] nullRecords = nullStart.task.snapshotState(1L);
      Assert.assertEquals(emptyRecords.length, nullRecords.length);
      for (int i = 0; i < emptyRecords.length; i++) {
        Assert.assertArrayEquals(emptyRecords[i], nullRecords[i]);
      }
    } finally {
      emptyStart.close();
      nullStart.close();
    }
  }

  private StreamRankNode dedupPlan() {
    final RowType type = inputType();
    final DeduplicateNode dedup =
        new DeduplicateNode("dedup-1", List.of(), type, 0L, -1, true, true, true);
    return new StreamRankNode(
        "rank-1", List.of(), new HashPartitionFunctionSpec(type, List.of(0)), dedup, type);
  }

  private RowType inputType() {
    return new RowType(
        List.of("k", "v", "p"), List.of(new BigIntType(), new BigIntType(), new VarbinaryType()));
  }

  // Batch one: two new keys, each carrying the hostile payload.
  private RowVector inputBatch1() {
    return makeInputVector(
        new long[] {1, 2}, new long[] {30, 20}, new byte[][] {HOSTILE_PAYLOAD, HOSTILE_PAYLOAD});
  }

  // Batch two: both keys already have state, so keep-last emits -U/+U.
  private RowVector inputBatch2() {
    return makeInputVector(
        new long[] {1, 2}, new long[] {10, 5}, new byte[][] {HOSTILE_PAYLOAD, new byte[] {1, 2}});
  }

  private RowVector makeInputVector(long[] keys, long[] values, byte[][] payloads) {
    final BigIntVector keyVector = new BigIntVector("k", allocator);
    keyVector.allocateNew(keys.length);
    final BigIntVector valueVector = new BigIntVector("v", allocator);
    valueVector.allocateNew(values.length);
    final VarBinaryVector payloadVector = new VarBinaryVector("p", allocator);
    payloadVector.allocateNew(payloads.length);
    for (int i = 0; i < keys.length; i++) {
      keyVector.setSafe(i, keys[i]);
      valueVector.setSafe(i, values[i]);
      payloadVector.setSafe(i, payloads[i], 0, payloads[i].length);
    }
    keyVector.setValueCount(keys.length);
    valueVector.setValueCount(values.length);
    payloadVector.setValueCount(payloads.length);
    final RowVector rv =
        session
            .arrowOps()
            .fromArrowTable(allocator, new Table(List.of(keyVector, valueVector, payloadVector)));
    keyVector.close();
    valueVector.close();
    payloadVector.close();
    return rv;
  }

  private static final class DedupOutput {
    final long[] keys;
    final long[] values;
    final byte[] rowKinds;
    final byte[][] payloads;

    DedupOutput(long[] keys, long[] values, byte[] rowKinds, byte[][] payloads) {
      this.keys = keys;
      this.values = values;
      this.rowKinds = rowKinds;
      this.payloads = payloads;
    }
  }

  private static void assertOutputsEqual(DedupOutput expected, DedupOutput actual) {
    Assert.assertArrayEquals(expected.keys, actual.keys);
    Assert.assertArrayEquals(expected.values, actual.values);
    Assert.assertArrayEquals(expected.rowKinds, actual.rowKinds);
    Assert.assertEquals(expected.payloads.length, actual.payloads.length);
    for (int i = 0; i < expected.payloads.length; i++) {
      Assert.assertArrayEquals(expected.payloads[i], actual.payloads[i]);
    }
  }

  private static int indexOf(byte[] haystack, byte needle) {
    for (int i = 0; i < haystack.length; i++) {
      if (haystack[i] == needle) {
        return i;
      }
    }
    return -1;
  }

  private static int indexOfSubsequence(byte[] haystack, byte[] needle) {
    outer:
    for (int i = 0; i + needle.length <= haystack.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  /** One SerialTask over the scan -> StreamRankNode(DeduplicateNode) plan. */
  private final class TaskHarness {
    final TableScanNode scanNode;
    final SerialTask task;
    final ExternalStreams.BlockingQueue queue;

    TaskHarness(StreamRankNode rankNode) {
      this(rankNode, false);
    }

    TaskHarness(StreamRankNode rankNode, boolean nullCheckpointRecords) {
      scanNode =
          new TableScanNode(
              "scan-1", inputType(), new ExternalStreamTableHandle(CONNECTOR_ID), List.of());
      final StatefulPlanNode rankPlan = new StatefulPlanNode(rankNode.getId(), rankNode);
      final StatefulPlanNode root = new StatefulPlanNode(scanNode.getId(), scanNode);
      root.addTarget(rankPlan);
      final Query query = new Query(root, Config.empty(), ConnectorConfig.empty());
      task = session.queryOps().execute(query);
      if (nullCheckpointRecords) {
        task.initializeState(0L, null, null);
      } else {
        task.initializeState(0L, null);
      }
      queue = session.externalStreamOps().newBlockingQueue();
      final ConnectorSplit split = new ExternalStreamConnectorSplit(CONNECTOR_ID, queue.id());
      task.addSplit(scanNode.getId(), split);
    }

    DedupOutput feed(RowVector inputVector) throws InterruptedException {
      queue.put(inputVector);
      task.waitFor();
      Assert.assertEquals(UpIterator.State.AVAILABLE, task.advance());
      final StatefulElement element = task.statefulGet();
      Assert.assertTrue(element.isRecord());
      final StatefulRecord record = element.asRecord();
      try {
        return readOutput(record.getRowVector());
      } finally {
        record.close();
        inputVector.close();
      }
    }

    void close() {
      task.noMoreSplits(scanNode.getId());
      task.close();
    }
  }

  private DedupOutput readOutput(RowVector rv) {
    final FieldVector structVector = Arrow.toArrowVector(allocator, rv);
    try {
      final BigIntVector kVector = (BigIntVector) findChild(structVector, "k");
      final BigIntVector vVector = (BigIntVector) findChild(structVector, "v");
      final VarBinaryVector pVector = (VarBinaryVector) findChild(structVector, "p");
      final TinyIntVector kindVector =
          (TinyIntVector) findChild(structVector, ROW_KIND_COLUMN_NAME);
      final int rows = vVector.getValueCount();
      final long[] keys = new long[rows];
      final long[] values = new long[rows];
      final byte[] kinds = new byte[rows];
      final byte[][] payloads = new byte[rows][];
      for (int i = 0; i < rows; i++) {
        keys[i] = kVector.get(i);
        values[i] = vVector.get(i);
        kinds[i] = kindVector.get(i);
        payloads[i] = pVector.get(i);
      }
      return new DedupOutput(keys, values, kinds, payloads);
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
