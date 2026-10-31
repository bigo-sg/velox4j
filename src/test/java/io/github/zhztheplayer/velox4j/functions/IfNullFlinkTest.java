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
import io.github.zhztheplayer.velox4j.expression.TypedExpr;
import io.github.zhztheplayer.velox4j.memory.AllocationListener;
import io.github.zhztheplayer.velox4j.memory.MemoryManager;
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.VarCharType;
import io.github.zhztheplayer.velox4j.variant.VarCharValue;

public class IfNullFlinkTest {
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

  private TypedExpr nullLiteral() {
    // A typed-null constant: exercises the Variant serde path where a null
    // value must still be serialized with an explicit "value": null member
    // (@JsonInclude(ALWAYS) on the Variant base class).
    return new ConstantTypedExpr(new VarCharType(), new VarCharValue(null), null);
  }

  private TypedExpr stringLiteral(String value) {
    return new ConstantTypedExpr(new VarCharType(), new VarCharValue(value), null);
  }

  private BaseVector evalIfNull(RowVector input, TypedExpr a, TypedExpr b) {
    SelectivityVector sv = session.selectivityVectorOps().create(input.getSize());
    CallTypedExpr ifNullExpr = new CallTypedExpr(new VarCharType(), List.of(a, b), "ifnull");
    Evaluation eval = new Evaluation(ifNullExpr, Config.empty(), ConnectorConfig.empty());
    Evaluator evaluator = session.evaluationOps().createEvaluator(eval);
    return evaluator.eval(sv, input);
  }

  @Test
  public void testFirstNonNullWins() {
    try (RowVector input = makeVarcharRowVector("first", null)) {
      BaseVector result =
          evalIfNull(
              input,
              FieldAccessTypedExpr.create(new VarCharType(), "c0"),
              stringLiteral("fallback"));
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        Assert.assertEquals(2, fv.getValueCount());
        VarCharVector vc = (VarCharVector) fv;
        Assert.assertEquals("first", new String(vc.get(0), StandardCharsets.UTF_8));
        Assert.assertEquals("fallback", new String(vc.get(1), StandardCharsets.UTF_8));
      }
    }
  }

  @Test
  public void testIfNullWithNullLiteralFirstArg() {
    try (RowVector input = makeVarcharRowVector("dummy")) {
      BaseVector result = evalIfNull(input, nullLiteral(), stringLiteral("fallback"));
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        Assert.assertEquals(1, fv.getValueCount());
        VarCharVector vc = (VarCharVector) fv;
        Assert.assertEquals("fallback", new String(vc.get(0), StandardCharsets.UTF_8));
      }
    }
  }

  @Test
  public void testIfNullBothNullYieldsNull() {
    try (RowVector input = makeVarcharRowVector((String) null)) {
      BaseVector result =
          evalIfNull(input, FieldAccessTypedExpr.create(new VarCharType(), "c0"), nullLiteral());
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        Assert.assertEquals(1, fv.getValueCount());
        Assert.assertTrue("both null should yield null", fv.isNull(0));
      }
    }
  }
}
