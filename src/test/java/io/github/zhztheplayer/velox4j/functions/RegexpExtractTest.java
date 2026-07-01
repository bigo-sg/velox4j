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

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
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
import io.github.zhztheplayer.velox4j.memory.AllocationListener;
import io.github.zhztheplayer.velox4j.memory.MemoryManager;
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.BooleanType;
import io.github.zhztheplayer.velox4j.type.IntegerType;
import io.github.zhztheplayer.velox4j.type.VarCharType;
import io.github.zhztheplayer.velox4j.variant.IntegerValue;
import io.github.zhztheplayer.velox4j.variant.VarCharValue;

public class RegexpExtractTest {
  private static MemoryManager memoryManager;
  private Session session;

  @BeforeClass
  public static void beforeClass() throws Exception {
    Velox4jTests.ensureInitialized();
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

  private BaseVector evalRegexpExtract(RowVector input, String pattern, int groupIdx) {
    SelectivityVector sv = session.selectivityVectorOps().create(input.getSize());
    CallTypedExpr regexpExpr =
        new CallTypedExpr(
            new VarCharType(),
            List.of(
                FieldAccessTypedExpr.create(new VarCharType(), "c0"),
                new ConstantTypedExpr(new VarCharType(), new VarCharValue(pattern), null),
                new ConstantTypedExpr(new IntegerType(), new IntegerValue(groupIdx), null)),
            "regexp_extract");
    Evaluation eval = new Evaluation(regexpExpr, Config.empty(), ConnectorConfig.empty());
    Evaluator evaluator = session.evaluationOps().createEvaluator(eval);
    return evaluator.eval(sv, input);
  }

  private BaseVector evalRegexpExtractIsNotNull(RowVector input, String pattern, int groupIdx) {
    SelectivityVector sv = session.selectivityVectorOps().create(input.getSize());
    CallTypedExpr regexpExpr =
        new CallTypedExpr(
            new VarCharType(),
            List.of(
                FieldAccessTypedExpr.create(new VarCharType(), "c0"),
                new ConstantTypedExpr(new VarCharType(), new VarCharValue(pattern), null),
                new ConstantTypedExpr(new IntegerType(), new IntegerValue(groupIdx), null)),
            "regexp_extract");
    CallTypedExpr isNotNullExpr =
        new CallTypedExpr(new BooleanType(), List.of(regexpExpr), "isnotnull");
    Evaluation eval = new Evaluation(isNotNullExpr, Config.empty(), ConnectorConfig.empty());
    Evaluator evaluator = session.evaluationOps().createEvaluator(eval);
    return evaluator.eval(sv, input);
  }

  @Test
  public void testRegexpExtractReturnsNullOnNoMatch() {
    try (RowVector input = makeVarcharRowVector("no_match_here")) {
      BaseVector result = evalRegexpExtract(input, "channel_id=([^&]*)", 1);
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        Assert.assertEquals(1, fv.getValueCount());
        Assert.assertTrue("Expected null for no match", fv.isNull(0));
      }
    }
  }

  @Test
  public void testRegexpExtractReturnsValueOnMatch() {
    try (RowVector input = makeVarcharRowVector("url?channel_id=123&other=1")) {
      BaseVector result = evalRegexpExtract(input, "channel_id=([^&]*)", 1);
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        Assert.assertEquals(1, fv.getValueCount());
        Assert.assertFalse("Expected non-null for match", fv.isNull(0));
        VarCharVector varCharVec = (VarCharVector) fv;
        String value = new String(varCharVec.get(0), StandardCharsets.UTF_8);
        Assert.assertEquals("123", value);
      }
    }
  }

  @Test
  public void testRegexpExtractIsNotNullReturnsFalseOnNoMatch() {
    try (RowVector input = makeVarcharRowVector("no_match_here")) {
      BaseVector result = evalRegexpExtractIsNotNull(input, "channel_id=([^&]*)", 1);
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        Assert.assertEquals(1, fv.getValueCount());
        Assert.assertFalse(
            "Expected isnotnull to be false on no match (so WHERE ... IS NOT NULL filters it out)",
            (Boolean) fv.getObject(0));
      }
    }
  }
}
