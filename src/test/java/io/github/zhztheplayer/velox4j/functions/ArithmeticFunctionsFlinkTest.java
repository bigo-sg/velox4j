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
package io.github.zhztheplayer.velox4j.functions;

import java.util.List;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VectorSchemaRoot;
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
import io.github.zhztheplayer.velox4j.connector.ExternalStreamConnectorSplit;
import io.github.zhztheplayer.velox4j.connector.ExternalStreamTableHandle;
import io.github.zhztheplayer.velox4j.connector.ExternalStreams;
import io.github.zhztheplayer.velox4j.data.BaseVector;
import io.github.zhztheplayer.velox4j.data.RowVector;
import io.github.zhztheplayer.velox4j.expression.CallTypedExpr;
import io.github.zhztheplayer.velox4j.expression.ConstantTypedExpr;
import io.github.zhztheplayer.velox4j.expression.FieldAccessTypedExpr;
import io.github.zhztheplayer.velox4j.expression.TypedExpr;
import io.github.zhztheplayer.velox4j.iterator.UpIterator;
import io.github.zhztheplayer.velox4j.memory.AllocationListener;
import io.github.zhztheplayer.velox4j.memory.MemoryManager;
import io.github.zhztheplayer.velox4j.plan.ProjectNode;
import io.github.zhztheplayer.velox4j.plan.TableScanNode;
import io.github.zhztheplayer.velox4j.query.Query;
import io.github.zhztheplayer.velox4j.query.SerialTask;
import io.github.zhztheplayer.velox4j.serde.Serde;
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.BigIntType;
import io.github.zhztheplayer.velox4j.type.DoubleType;
import io.github.zhztheplayer.velox4j.type.IntegerType;
import io.github.zhztheplayer.velox4j.type.Type;
import io.github.zhztheplayer.velox4j.variant.IntegerValue;

/**
 * velox4j-level invocation tests for the flinksql arithmetic functions (abs / floor / ceil / round
 * / negate), evaluated through the plan-JSON path: the Query is serialized to JSON and deserialized
 * back before execution, locking both the flinksql semantics and the plan serde at the bridge
 * layer.
 */
public class ArithmeticFunctionsFlinkTest {
  private static MemoryManager memoryManager;
  private Session session;

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
  }

  @After
  public void tearDown() throws Exception {
    session.close();
  }

  private RowVector makeBigIntRowVector(Long... values) {
    BufferAllocator alloc = new RootAllocator();
    BigIntVector vec = new BigIntVector("c0", alloc);
    vec.allocateNew();
    for (int i = 0; i < values.length; i++) {
      if (values[i] != null) {
        vec.set(i, values[i]);
      } else {
        vec.setNull(i);
      }
    }
    vec.setValueCount(values.length);
    VectorSchemaRoot root = VectorSchemaRoot.of(vec);
    RowVector rv =
        session.arrowOps().fromArrowTable(alloc, new org.apache.arrow.vector.table.Table(root));
    return rv;
  }

  private RowVector makeDoubleRowVector(Double... values) {
    BufferAllocator alloc = new RootAllocator();
    Float8Vector vec = new Float8Vector("c0", alloc);
    vec.allocateNew();
    for (int i = 0; i < values.length; i++) {
      if (values[i] != null) {
        vec.set(i, values[i]);
      } else {
        vec.setNull(i);
      }
    }
    vec.setValueCount(values.length);
    VectorSchemaRoot root = VectorSchemaRoot.of(vec);
    RowVector rv =
        session.arrowOps().fromArrowTable(alloc, new org.apache.arrow.vector.table.Table(root));
    return rv;
  }

  /** Runs the projection through a JSON round trip of the plan and returns the first output. */
  private RowVector evalThroughPlanJson(TypedExpr projection, RowVector input)
      throws InterruptedException {
    final ExternalStreams.BlockingQueue queue = session.externalStreamOps().newBlockingQueue();
    final TableScanNode scanNode =
        new TableScanNode(
            "scan-1",
            input.getType(),
            new ExternalStreamTableHandle("connector-external-stream"),
            List.of());
    final ProjectNode projectNode =
        new ProjectNode("project-1", List.of(scanNode), List.of("out"), List.of(projection));
    Query query = new Query(projectNode, Config.empty(), ConnectorConfig.empty());
    // Plan-JSON path: serialize and deserialize the query before execution.
    final String queryJson = Serde.toJson(query);
    query = Serde.fromJson(queryJson, Query.class);
    final SerialTask task = session.queryOps().execute(query);
    task.addSplit(
        scanNode.getId(),
        new ExternalStreamConnectorSplit("connector-external-stream", queue.id()));
    task.noMoreSplits(scanNode.getId());
    queue.put(input);
    for (int i = 0; i < 100; i++) {
      final UpIterator.State state = task.advance();
      if (state == UpIterator.State.AVAILABLE) {
        return (RowVector) task.get();
      }
      if (state == UpIterator.State.FINISHED) {
        throw new IllegalStateException("task finished without producing output");
      }
    }
    throw new IllegalStateException("task never became available after input was pushed");
  }

  private TypedExpr c0(Type type) {
    return FieldAccessTypedExpr.create(type, "c0");
  }

  @Test
  public void testAbsBigint() throws Exception {
    try (RowVector input = makeBigIntRowVector(-5L, 7L, null, 0L)) {
      RowVector result =
          evalThroughPlanJson(
              new CallTypedExpr(new BigIntType(), List.of(c0(new BigIntType())), "abs"), input);
      try (BufferAllocator alloc = new RootAllocator();
          org.apache.arrow.vector.table.Table t = Arrow.toArrowTable(alloc, result)) {
        BigIntVector v = (BigIntVector) t.getVector(0);
        Assert.assertEquals(4, v.getValueCount());
        Assert.assertEquals(5L, v.get(0));
        Assert.assertEquals(7L, v.get(1));
        Assert.assertTrue(v.isNull(2));
        Assert.assertEquals(0L, v.get(3));
      }
    }
  }

  @Test
  public void testNegateBigint() throws Exception {
    try (RowVector input = makeBigIntRowVector(-7L, 9L)) {
      RowVector result =
          evalThroughPlanJson(
              new CallTypedExpr(new BigIntType(), List.of(c0(new BigIntType())), "negate"), input);
      try (BufferAllocator alloc = new RootAllocator();
          org.apache.arrow.vector.table.Table t = Arrow.toArrowTable(alloc, result)) {
        BigIntVector v = (BigIntVector) t.getVector(0);
        Assert.assertEquals(2, v.getValueCount());
        Assert.assertEquals(7L, v.get(0));
        Assert.assertEquals(-9L, v.get(1));
      }
    }
  }

  @Test
  public void testFloorCeilDouble() throws Exception {
    try (RowVector input = makeDoubleRowVector(2.5, -2.5, null)) {
      RowVector floor =
          evalThroughPlanJson(
              new CallTypedExpr(new DoubleType(), List.of(c0(new DoubleType())), "floor"), input);
      try (BufferAllocator alloc = new RootAllocator();
          org.apache.arrow.vector.table.Table t = Arrow.toArrowTable(alloc, floor)) {
        Float8Vector v = (Float8Vector) t.getVector(0);
        Assert.assertEquals(3, v.getValueCount());
        Assert.assertEquals(2.0, v.get(0), 0.0);
        Assert.assertEquals(-3.0, v.get(1), 0.0);
        Assert.assertTrue(v.isNull(2));
      }
      RowVector ceil =
          evalThroughPlanJson(
              new CallTypedExpr(new DoubleType(), List.of(c0(new DoubleType())), "ceil"), input);
      try (BufferAllocator alloc = new RootAllocator();
          org.apache.arrow.vector.table.Table t = Arrow.toArrowTable(alloc, ceil)) {
        Float8Vector v = (Float8Vector) t.getVector(0);
        Assert.assertEquals(3, v.getValueCount());
        Assert.assertEquals(3.0, v.get(0), 0.0);
        Assert.assertEquals(-2.0, v.get(1), 0.0);
        Assert.assertTrue(v.isNull(2));
      }
    }
  }

  @Test
  public void testRoundHalfUpInDecimalDomain() throws Exception {
    // 2.675 -> 2.68 and 647568.815 -> 647568.82: HALF_UP decided in the
    // decimal domain (Flink's BigDecimal semantics), which binary-domain
    // or scale-multiply rounding would get wrong.
    try (RowVector input = makeDoubleRowVector(2.675, 647568.815, -2.5)) {
      TypedExpr roundTo2 =
          new CallTypedExpr(
              new DoubleType(),
              List.of(
                  c0(new DoubleType()),
                  new ConstantTypedExpr(new IntegerType(), new IntegerValue(2), null)),
              "round");
      BaseVector result = evalThroughPlanJson(roundTo2, new DoubleType(), input);
      try (BufferAllocator alloc = new RootAllocator();
          org.apache.arrow.vector.table.Table t = Arrow.toArrowTable(alloc, result)) {
        Float8Vector v = (Float8Vector) t.getVector(0);
        Assert.assertEquals(3, v.getValueCount());
        Assert.assertEquals(2.68, v.get(0), 0.0);
        Assert.assertEquals(647568.82, v.get(1), 0.0);
        Assert.assertEquals(-3.0, v.get(2), 0.0);
      }
    }
  }
}
