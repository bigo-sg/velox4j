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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarCharVector;
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
import io.github.zhztheplayer.velox4j.data.BaseVector;
import io.github.zhztheplayer.velox4j.data.RowVector;
import io.github.zhztheplayer.velox4j.data.SelectivityVector;
import io.github.zhztheplayer.velox4j.eval.Evaluation;
import io.github.zhztheplayer.velox4j.eval.Evaluator;
import io.github.zhztheplayer.velox4j.expression.CallTypedExpr;
import io.github.zhztheplayer.velox4j.expression.ConstantTypedExpr;
import io.github.zhztheplayer.velox4j.expression.FieldAccessTypedExpr;
import io.github.zhztheplayer.velox4j.expression.TypedExpr;
import io.github.zhztheplayer.velox4j.memory.AllocationListener;
import io.github.zhztheplayer.velox4j.memory.MemoryManager;
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.BigIntType;
import io.github.zhztheplayer.velox4j.type.VarCharType;
import io.github.zhztheplayer.velox4j.variant.VarCharValue;

public class UnixTimestampFlinkTest {
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

  private RowVector makeVarcharRowVector(String... values) {
    BufferAllocator alloc = new RootAllocator();
    VarCharVector vec = new VarCharVector("c0", alloc);
    vec.allocateNew();
    for (int i = 0; i < values.length; i++) {
      if (values[i] != null) {
        vec.set(i, values[i].getBytes(StandardCharsets.UTF_8));
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

  private BaseVector evalUnixTimestamp(RowVector input, String format) {
    SelectivityVector sv = session.selectivityVectorOps().create(input.getSize());
    TypedExpr str = FieldAccessTypedExpr.create(new VarCharType(), "c0");
    CallTypedExpr expr;
    if (format == null) {
      expr = new CallTypedExpr(new BigIntType(), List.of(str), "unix_timestamp");
    } else {
      expr =
          new CallTypedExpr(
              new BigIntType(),
              List.of(
                  str, new ConstantTypedExpr(new VarCharType(), new VarCharValue(format), null)),
              "unix_timestamp");
    }
    Config config = Config.create(Map.of("session_timezone", "Asia/Shanghai"));
    Evaluation eval = new Evaluation(expr, config, ConnectorConfig.empty());
    Evaluator evaluator = session.evaluationOps().createEvaluator(eval);
    return evaluator.eval(sv, input);
  }

  @Test
  public void testDefaultFormatSessionTimezone() {
    try (RowVector input =
        makeVarcharRowVector("2026-09-11 10:00:00", "1970-01-01 00:00:00", null)) {
      BaseVector result = evalUnixTimestamp(input, null);
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        Assert.assertEquals(3, fv.getValueCount());
        BigIntVector bigIntVec = (BigIntVector) fv;
        Assert.assertEquals(1789092000L, bigIntVec.get(0));
        Assert.assertEquals(-28800L, bigIntVec.get(1));
        Assert.assertTrue("null input must return null", bigIntVec.isNull(2));
      }
    }
  }

  @Test
  public void testUnparseableReturnsLongMinValue() {
    try (RowVector input = makeVarcharRowVector("garbage", "")) {
      BaseVector result = evalUnixTimestamp(input, null);
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        BigIntVector bigIntVec = (BigIntVector) fv;
        Assert.assertEquals(Long.MIN_VALUE, bigIntVec.get(0));
        Assert.assertEquals(Long.MIN_VALUE, bigIntVec.get(1));
      }
    }
  }

  @Test
  public void testCustomFormat() {
    try (RowVector input = makeVarcharRowVector("2026-09-11")) {
      BaseVector result = evalUnixTimestamp(input, "yyyy-MM-dd");
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        BigIntVector bigIntVec = (BigIntVector) fv;
        Assert.assertEquals(1789056000L, bigIntVec.get(0));
      }
    }
  }
}
