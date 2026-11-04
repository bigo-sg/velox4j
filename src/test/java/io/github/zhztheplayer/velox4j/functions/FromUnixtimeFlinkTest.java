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

public class FromUnixtimeFlinkTest {
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

  private RowVector makeBigintRowVector(Long... values) {
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

  private BaseVector evalFromUnixtime(RowVector input, String format) {
    SelectivityVector sv = session.selectivityVectorOps().create(input.getSize());
    TypedExpr unixtime = FieldAccessTypedExpr.create(new BigIntType(), "c0");
    CallTypedExpr expr;
    if (format == null) {
      expr = new CallTypedExpr(new VarCharType(), List.of(unixtime), "from_unixtime");
    } else {
      expr =
          new CallTypedExpr(
              new VarCharType(),
              List.of(
                  unixtime,
                  new ConstantTypedExpr(new VarCharType(), new VarCharValue(format), null)),
              "from_unixtime");
    }
    Config config =
        Config.create(
            Map.of("session_timezone", "UTC", "adjust_timestamp_to_session_timezone", "true"));
    Evaluation eval = new Evaluation(expr, config, ConnectorConfig.empty());
    Evaluator evaluator = session.evaluationOps().createEvaluator(eval);
    return evaluator.eval(sv, input);
  }

  @Test
  public void testDefaultFormatUtc() {
    try (RowVector input = makeBigintRowVector(1760000000L, 0L, -1L, null)) {
      BaseVector result = evalFromUnixtime(input, null);
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        Assert.assertEquals(4, fv.getValueCount());
        VarCharVector varCharVec = (VarCharVector) fv;
        Assert.assertEquals(
            "2025-10-09 08:53:20", new String(varCharVec.get(0), StandardCharsets.UTF_8));
        Assert.assertEquals(
            "1970-01-01 00:00:00", new String(varCharVec.get(1), StandardCharsets.UTF_8));
        Assert.assertEquals(
            "1969-12-31 23:59:59", new String(varCharVec.get(2), StandardCharsets.UTF_8));
        Assert.assertTrue("null input must return null", varCharVec.isNull(3));
      }
    }
  }

  @Test
  public void testCustomFormat() {
    try (RowVector input = makeBigintRowVector(1760000000L)) {
      BaseVector result = evalFromUnixtime(input, "yyyy-MM-dd");
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        VarCharVector varCharVec = (VarCharVector) fv;
        Assert.assertEquals("2025-10-09", new String(varCharVec.get(0), StandardCharsets.UTF_8));
      }
    }
  }
}
