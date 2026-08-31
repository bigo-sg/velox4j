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
package io.github.zhztheplayer.velox4j.stateful;

import java.util.List;

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
import io.github.zhztheplayer.velox4j.iterator.UpIterator;
import io.github.zhztheplayer.velox4j.memory.AllocationListener;
import io.github.zhztheplayer.velox4j.memory.MemoryManager;
import io.github.zhztheplayer.velox4j.plan.StatefulPlanNode;
import io.github.zhztheplayer.velox4j.plan.TableScanNode;
import io.github.zhztheplayer.velox4j.query.Query;
import io.github.zhztheplayer.velox4j.query.SerialTask;
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.BigIntType;
import io.github.zhztheplayer.velox4j.type.RowType;
import io.github.zhztheplayer.velox4j.type.TinyIntType;

public class StatefulRecordMergedRowVectorTest {
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

  @Test
  public void testStatefulRecordCarriesMergedRowKindColumn() {
    // $row_kind bytes: 0=INSERT, 1=UPDATE_BEFORE, 2=UPDATE_AFTER (Flink RowKind ordinals).
    RowType outputType =
        new RowType(
            List.of("c", ROW_KIND_COLUMN_NAME), List.of(new BigIntType(), new TinyIntType()));
    RowVector inputVector = makeMergedRowVector(new long[] {10, 20, 30}, new byte[] {0, 1, 2});
    StatefulRecord record = runSingleRecordQuery(outputType, inputVector);
    RowVector outputVector = record.getRowVector();
    RowType mergedType = (RowType) outputVector.getType();
    Assert.assertEquals(2, mergedType.size());
    Assert.assertEquals("c", mergedType.getNames().get(0));
    Assert.assertEquals(ROW_KIND_COLUMN_NAME, mergedType.getNames().get(1));
    Assert.assertArrayEquals(new long[] {10, 20, 30}, readBigIntColumn(outputVector, "c"));
    Assert.assertArrayEquals(
        new byte[] {0, 1, 2}, readTinyIntColumn(outputVector, ROW_KIND_COLUMN_NAME));
    record.close();
    inputVector.close();
  }

  @Test
  public void testStatefulRecordAppendOnlyHasNoRowKindColumn() {
    RowType outputType = new RowType(List.of("c"), List.of(new BigIntType()));
    RowVector inputVector = makeAppendOnlyRowVector(new long[] {7, 8, 9});
    StatefulRecord record = runSingleRecordQuery(outputType, inputVector);
    RowVector outputVector = record.getRowVector();
    RowType mergedType = (RowType) outputVector.getType();
    Assert.assertEquals(1, mergedType.size());
    Assert.assertEquals("c", mergedType.getNames().get(0));
    Assert.assertArrayEquals(new long[] {7, 8, 9}, readBigIntColumn(outputVector, "c"));
    record.close();
    inputVector.close();
  }

  private StatefulRecord runSingleRecordQuery(RowType outputType, RowVector inputVector) {
    TableScanNode scanNode =
        new TableScanNode(
            "id-1", outputType, new ExternalStreamTableHandle(CONNECTOR_ID), List.of());
    StatefulPlanNode planNode = new StatefulPlanNode(scanNode.getId(), scanNode);
    Query query = new Query(planNode, Config.empty(), ConnectorConfig.empty());
    SerialTask task = session.queryOps().execute(query);
    this.task = task;
    ExternalStreams.BlockingQueue queue = session.externalStreamOps().newBlockingQueue();
    ConnectorSplit split = new ExternalStreamConnectorSplit(CONNECTOR_ID, queue.id());
    task.addSplit(scanNode.getId(), split);
    task.noMoreSplits(scanNode.getId());
    queue.put(inputVector);
    task.waitFor();
    Assert.assertEquals(UpIterator.State.AVAILABLE, task.advance());
    StatefulElement element = task.statefulGet();
    Assert.assertTrue(element.isRecord());
    return element.asRecord();
  }

  private static long[] readBigIntColumn(RowVector rv, String name) {
    FieldVector structVector = Arrow.toArrowVector(new RootAllocator(), rv);
    try {
      BigIntVector child = (BigIntVector) findChild(structVector, name);
      long[] values = new long[child.getValueCount()];
      for (int i = 0; i < child.getValueCount(); i++) {
        values[i] = child.get(i);
      }
      return values;
    } finally {
      structVector.close();
    }
  }

  private static byte[] readTinyIntColumn(RowVector rv, String name) {
    FieldVector structVector = Arrow.toArrowVector(new RootAllocator(), rv);
    try {
      TinyIntVector child = (TinyIntVector) findChild(structVector, name);
      byte[] values = new byte[child.getValueCount()];
      for (int i = 0; i < child.getValueCount(); i++) {
        values[i] = child.get(i);
      }
      return values;
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

  private RowVector makeMergedRowVector(long[] values, byte[] rowKinds) {
    BigIntVector bigIntVector = new BigIntVector("c", allocator);
    bigIntVector.allocateNew(values.length);
    TinyIntVector rowKindVector = new TinyIntVector(ROW_KIND_COLUMN_NAME, allocator);
    rowKindVector.allocateNew(rowKinds.length);
    for (int i = 0; i < values.length; i++) {
      bigIntVector.setSafe(i, values[i]);
      rowKindVector.setSafe(i, rowKinds[i]);
    }
    bigIntVector.setValueCount(values.length);
    rowKindVector.setValueCount(rowKinds.length);
    RowVector rv =
        session
            .arrowOps()
            .fromArrowTable(allocator, new Table(List.of(bigIntVector, rowKindVector)));
    bigIntVector.close();
    rowKindVector.close();
    return rv;
  }

  private RowVector makeAppendOnlyRowVector(long[] values) {
    BigIntVector bigIntVector = new BigIntVector("c", allocator);
    bigIntVector.allocateNew(values.length);
    for (int i = 0; i < values.length; i++) {
      bigIntVector.setSafe(i, values[i]);
    }
    bigIntVector.setValueCount(values.length);
    RowVector rv = session.arrowOps().fromArrowTable(allocator, new Table(List.of(bigIntVector)));
    bigIntVector.close();
    return rv;
  }
}
