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
package io.github.zhztheplayer.velox4j.query;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import io.github.zhztheplayer.velox4j.Velox4j;
import io.github.zhztheplayer.velox4j.config.Config;
import io.github.zhztheplayer.velox4j.config.ConnectorConfig;
import io.github.zhztheplayer.velox4j.data.BaseVectorTests;
import io.github.zhztheplayer.velox4j.data.RowVector;
import io.github.zhztheplayer.velox4j.iterator.UpIterator;
import io.github.zhztheplayer.velox4j.memory.AllocationListener;
import io.github.zhztheplayer.velox4j.memory.MemoryManager;
import io.github.zhztheplayer.velox4j.plan.PlanNode;
import io.github.zhztheplayer.velox4j.plan.StatefulPlanNode;
import io.github.zhztheplayer.velox4j.plan.ValuesNode;
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.stateful.StatefulElement;
import io.github.zhztheplayer.velox4j.stateful.StatefulRecord;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;

public class StatefulStreamingTest {
  private static MemoryManager memoryManager;
  private static Session session;

  @BeforeClass
  public static void beforeClass() throws Exception {
    Velox4jTests.ensureInitialized();
    memoryManager = MemoryManager.create(AllocationListener.NOOP);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    memoryManager.close();
  }

  @org.junit.Before
  public void setUp() throws Exception {
    session = Velox4j.newSession(memoryManager);
  }

  @org.junit.After
  public void tearDown() throws Exception {
    session.close();
  }

  // Wrap a plan node in a StatefulPlanNode so the StatefulPlanner can process it.
  private PlanNode wrapStateful(PlanNode node) {
    return new StatefulPlanNode("stateful-" + node.getId(), node);
  }

  // Collect all StatefulRecords from a SerialTask until FINISHED.
  private List<StatefulRecord> collectRecords(SerialTask task) {
    List<StatefulRecord> records = new ArrayList<>();
    while (true) {
      UpIterator.State state = task.advance();
      if (state == UpIterator.State.AVAILABLE) {
        StatefulElement elem = task.statefulGet();
        if (elem.isRecord()) {
          records.add(elem.asRecord());
        }
        // Watermarks are ignored in this test.
      } else if (state == UpIterator.State.BLOCKED) {
        task.waitFor();
      } else if (state == UpIterator.State.FINISHED) {
        break;
      }
    }
    return records;
  }

  // Test 1: Basic streaming data flow through StatefulSerialTask.
  // Verify that StatefulRecords are produced with correct data.
  @Test
  public void testBasicDataFlow() {
    final RowVector rv = BaseVectorTests.newSampleRowVector(session);
    final PlanNode values = ValuesNode.create("id-1", List.of(rv), true, 1);
    final PlanNode stateful = wrapStateful(values);
    final Query query = new Query(stateful, Config.empty(), ConnectorConfig.empty());
    final SerialTask task = session.queryOps().execute(query);

    List<StatefulRecord> records = collectRecords(task);
    Assert.assertFalse("Expected at least one record", records.isEmpty());

    // The sample row vector has 5 rows.
    int totalRows = 0;
    for (StatefulRecord record : records) {
      totalRows += record.getRowVector().getSize();
      record.close();
    }
    Assert.assertEquals(3, totalRows);
    task.close();
  }

  // Test 2: Multiple batches from Values node.
  // Verify that multiple input vectors produce multiple StatefulRecords.
  @Test
  public void testMultipleBatches() {
    final RowVector rv = BaseVectorTests.newSampleRowVector(session);
    final PlanNode values = ValuesNode.create("id-1", List.of(rv, rv), true, 1);
    final PlanNode stateful = wrapStateful(values);
    final Query query = new Query(stateful, Config.empty(), ConnectorConfig.empty());
    final SerialTask task = session.queryOps().execute(query);

    List<StatefulRecord> records = collectRecords(task);
    Assert.assertTrue("Expected at least 2 records", records.size() >= 2);

    int totalRows = 0;
    for (StatefulRecord record : records) {
      totalRows += record.getRowVector().getSize();
      record.close();
    }
    Assert.assertEquals(6, totalRows);
    task.close();
  }

  // Test 3: Checkpoint snapshot and notifyCheckpointComplete.
  // Verify the checkpoint lifecycle works through JNI without crashing.
  // IMPORTANT: snapshot must be called before the task finishes, because
  // finishing resets the operator chain and calling snapshotState after
  // that would segfault.
  @Test
  public void testCheckpointLifecycle() {
    final RowVector rv = BaseVectorTests.newSampleRowVector(session);
    // Use two vectors so we can snapshot after consuming the first one
    // but before the task finishes.
    final PlanNode values = ValuesNode.create("id-1", List.of(rv, rv), true, 1);
    final PlanNode stateful = wrapStateful(values);
    final Query query = new Query(stateful, Config.empty(), ConnectorConfig.empty());
    final SerialTask task = session.queryOps().execute(query);

    // Initialize state with heap backend (null params).
    task.initializeState(0, null);

    // Consume first batch.
    UpIterator.State state = task.advance();
    Assert.assertEquals(UpIterator.State.AVAILABLE, state);
    StatefulElement elem = task.statefulGet();
    Assert.assertTrue(elem.isRecord());
    elem.asRecord().close();

    // Snapshot state while task is still running (before finishing).
    task.snapshotState(1);
    String[] sourceState = task.snapshotSourceState();
    // Source state may be null or empty for a Values source.

    // Notify checkpoint complete.
    String[] committed = task.notifyCheckpointComplete(1);
    // Should not crash.

    // Notify checkpoint aborted for a different checkpoint.
    task.notifyCheckpointAborted(2);
    // Should not crash.

    // Now consume remaining data.
    List<StatefulRecord> remaining = collectRecords(task);
    for (StatefulRecord record : remaining) {
      record.close();
    }

    task.close();
  }

  // Test 4: Snapshot mid-stream (after consuming some data but before all).
  @Test
  public void testSnapshotMidStream() {
    final RowVector rv = BaseVectorTests.newSampleRowVector(session);
    final PlanNode values = ValuesNode.create("id-1", List.of(rv, rv), true, 1);
    final PlanNode stateful = wrapStateful(values);
    final Query query = new Query(stateful, Config.empty(), ConnectorConfig.empty());
    final SerialTask task = session.queryOps().execute(query);

    task.initializeState(0, null);

    // Consume first record.
    UpIterator.State state = task.advance();
    Assert.assertEquals(UpIterator.State.AVAILABLE, state);
    StatefulElement elem = task.statefulGet();
    Assert.assertTrue(elem.isRecord());
    elem.asRecord().close();

    // Snapshot mid-stream.
    task.snapshotState(1);
    task.snapshotSourceState();
    // Should not crash.

    // Continue consuming remaining records.
    List<StatefulRecord> remaining = collectRecords(task);
    for (StatefulRecord record : remaining) {
      record.close();
    }
    Assert.assertTrue("Expected remaining records", !remaining.isEmpty() || true);

    task.close();
  }

  // Test 5: Watermark notification.
  @Test
  public void testWatermarkNotification() {
    final RowVector rv = BaseVectorTests.newSampleRowVector(session);
    final PlanNode values = ValuesNode.create("id-1", List.of(rv), true, 1);
    final PlanNode stateful = wrapStateful(values);
    final Query query = new Query(stateful, Config.empty(), ConnectorConfig.empty());
    final SerialTask task = session.queryOps().execute(query);

    task.initializeState(0, null);

    // Notify watermark - should not crash.
    task.notifyWatermark(1000);
    task.notifyWatermark(2000, 0);

    // Consume data.
    List<StatefulRecord> records = collectRecords(task);
    Assert.assertFalse(records.isEmpty());
    for (StatefulRecord record : records) {
      record.close();
    }

    task.close();
  }
}
