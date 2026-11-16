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
import io.github.zhztheplayer.velox4j.aggregate.Aggregate;
import io.github.zhztheplayer.velox4j.arrow.Arrow;
import io.github.zhztheplayer.velox4j.config.Config;
import io.github.zhztheplayer.velox4j.config.ConnectorConfig;
import io.github.zhztheplayer.velox4j.connector.ExternalStreamConnectorSplit;
import io.github.zhztheplayer.velox4j.connector.ExternalStreamTableHandle;
import io.github.zhztheplayer.velox4j.connector.ExternalStreams;
import io.github.zhztheplayer.velox4j.data.RowVector;
import io.github.zhztheplayer.velox4j.expression.CallTypedExpr;
import io.github.zhztheplayer.velox4j.expression.FieldAccessTypedExpr;
import io.github.zhztheplayer.velox4j.iterator.UpIterator;
import io.github.zhztheplayer.velox4j.memory.AllocationListener;
import io.github.zhztheplayer.velox4j.memory.MemoryManager;
import io.github.zhztheplayer.velox4j.plan.GroupAggregationNode;
import io.github.zhztheplayer.velox4j.plan.GroupAggsHandlerNode;
import io.github.zhztheplayer.velox4j.plan.HashPartitionFunctionSpec;
import io.github.zhztheplayer.velox4j.plan.PlanNode;
import io.github.zhztheplayer.velox4j.plan.StatefulPlanNode;
import io.github.zhztheplayer.velox4j.plan.TableScanNode;
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.stateful.KeyedStateSpec;
import io.github.zhztheplayer.velox4j.stateful.StatefulElement;
import io.github.zhztheplayer.velox4j.stateful.StatefulRecord;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.BigIntType;
import io.github.zhztheplayer.velox4j.type.RowType;

/**
 * Aggregation tests under the FLINK preset. The FLINK preset init installs the flinksql aggregate
 * family on top of the sparksql baseline (with overwrite), so 'avg' resolves with Flink semantics
 * on the group-aggregation path: avg(BIGINT) yields BIGINT with truncating division instead of the
 * sparksql DOUBLE average. The plan shape mirrors gluten-flink's StreamExecGroupAggregate
 * translation: a mock ExternalStream source whose target is a GroupAggregationNode. If the flinksql
 * registration were missing, the operator's aggregate resolution would reject the call with its
 * declared-result-type check (avg(bigint) produces DOUBLE while the declared output column carries
 * BIGINT).
 */
public class AggregationFlinkTest {
  private static final String CONNECTOR_ID = "connector-external-stream";
  private static MemoryManager memoryManager;
  private Session session;
  private RootAllocator allocator;
  private SerialTask task;

  @BeforeClass
  public static void beforeClass() throws Exception {
    Velox4jTests.ensureInitializedForFlink();
    memoryManager = MemoryManager.create(AllocationListener.NOOP);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    memoryManager.close();
  }

  @Before
  public void setUp() throws Exception {
    session = Velox4j.newSession(memoryManager);
    allocator = new RootAllocator();
  }

  @After
  public void tearDown() throws Exception {
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
  public void testGroupAggregationAvgBigintResolvesToFlinkSemantics() {
    // Input rows carry no $row_kind column, i.e. INSERT-only, so every output row is a +I record.
    // The JNI boundary returns the changelog-shaped merged vector: the declared output columns
    // plus a trailing $row_kind TINYINT column, all +I (0) here. Groups are chosen so Flink's
    // truncating BIGINT average differs from sparksql's DOUBLE average for two of the three
    // keys: key 0 averages 0.5 -> 0, key 1 averages 20.33... -> 20, and key 2 averages exactly 7.
    final RowVector inputVector =
        makeInputVector(new long[] {0, 0, 1, 1, 1, 2}, new long[] {0, 1, 10, 20, 31, 7});
    StatefulRecord record = null;
    try {
      record = runGroupAggregation(inputVector);
      final RowVector outputVector = record.getRowVector();

      final RowType outputType = (RowType) outputVector.getType();
      Assert.assertEquals(List.of("regionkey", "avg", "$row_kind"), outputType.getNames());
      Assert.assertTrue(
          "avg output must be BIGINT under Flink semantics",
          outputType.getChildren().get(1) instanceof BigIntType);

      Assert.assertArrayEquals(new long[] {0, 1, 2}, readBigIntColumn(outputVector, "regionkey"));
      Assert.assertArrayEquals(
          "avg(BIGINT) must truncate to BIGINT under Flink semantics",
          new long[] {0, 20, 7},
          readBigIntColumn(outputVector, "avg"));
      Assert.assertArrayEquals(
          "INSERT-only input must emit +I records",
          new byte[] {0, 0, 0},
          readTinyIntColumn(outputVector, "$row_kind"));
    } finally {
      if (record != null) {
        record.close();
      }
      inputVector.close();
    }
  }

  /** Runs avg(nationkey) grouped by regionkey, in the shape gluten-flink produces. */
  private StatefulRecord runGroupAggregation(RowVector inputVector) {
    final RowType inputType =
        new RowType(List.of("regionkey", "nationkey"), List.of(new BigIntType(), new BigIntType()));
    final RowType outputType =
        new RowType(List.of("regionkey", "avg"), List.of(new BigIntType(), new BigIntType()));
    final List<Integer> keyChannels = List.of(0);

    final PlanNode aggsHandlerNode =
        new GroupAggsHandlerNode(
            "id-2",
            inputType,
            outputType,
            List.of(
                new Aggregate(
                    new CallTypedExpr(
                        new BigIntType(),
                        List.of(FieldAccessTypedExpr.create(new BigIntType(), "nationkey")),
                        "avg"),
                    List.of(new BigIntType()),
                    null,
                    List.of(),
                    List.of(),
                    false)),
            false,
            false);
    final PlanNode aggregationNode =
        new GroupAggregationNode(
            "id-3",
            aggsHandlerNode,
            new HashPartitionFunctionSpec(inputType, keyChannels),
            new KeyedStateSpec(keyChannels, List.of(new BigIntType())),
            outputType);

    // Mock source wrapping the aggregation as its target, then feed rows through the queue -
    // the same wiring GlutenOneInputOperator sets up.
    final TableScanNode scanNode =
        new TableScanNode(
            "id-1", inputType, new ExternalStreamTableHandle(CONNECTOR_ID), List.of());
    final StatefulPlanNode planNode = new StatefulPlanNode(scanNode.getId(), scanNode);
    planNode.addTarget(new StatefulPlanNode(aggregationNode.getId(), aggregationNode));
    final Query query = new Query(planNode, Config.empty(), ConnectorConfig.empty());
    task = session.queryOps().execute(query);
    final ExternalStreams.BlockingQueue queue = session.externalStreamOps().newBlockingQueue();
    task.addSplit(scanNode.getId(), new ExternalStreamConnectorSplit(CONNECTOR_ID, queue.id()));
    task.noMoreSplits(scanNode.getId());
    task.initializeState(0, null);

    queue.put(inputVector);
    task.waitFor();
    Assert.assertEquals(UpIterator.State.AVAILABLE, task.advance());
    final StatefulElement element = task.statefulGet();
    Assert.assertTrue(element.isRecord());
    return element.asRecord();
  }

  private RowVector makeInputVector(long[] regionkeys, long[] nationkeys) {
    final BigIntVector regionkeyVector = new BigIntVector("regionkey", allocator);
    regionkeyVector.allocateNew(regionkeys.length);
    final BigIntVector nationkeyVector = new BigIntVector("nationkey", allocator);
    nationkeyVector.allocateNew(nationkeys.length);
    for (int i = 0; i < regionkeys.length; i++) {
      regionkeyVector.setSafe(i, regionkeys[i]);
      nationkeyVector.setSafe(i, nationkeys[i]);
    }
    regionkeyVector.setValueCount(regionkeys.length);
    nationkeyVector.setValueCount(nationkeys.length);
    final RowVector rv =
        session
            .arrowOps()
            .fromArrowTable(allocator, new Table(List.of(regionkeyVector, nationkeyVector)));
    regionkeyVector.close();
    nationkeyVector.close();
    return rv;
  }

  private long[] readBigIntColumn(RowVector rv, String name) {
    final FieldVector structVector = Arrow.toArrowVector(allocator, rv);
    try {
      final BigIntVector child = (BigIntVector) findChild(structVector, name);
      final long[] values = new long[child.getValueCount()];
      for (int i = 0; i < child.getValueCount(); i++) {
        values[i] = child.get(i);
      }
      return values;
    } finally {
      structVector.close();
    }
  }

  private byte[] readTinyIntColumn(RowVector rv, String name) {
    final FieldVector structVector = Arrow.toArrowVector(allocator, rv);
    try {
      final TinyIntVector child = (TinyIntVector) findChild(structVector, name);
      final byte[] values = new byte[child.getValueCount()];
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
}
