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
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.FieldVector;
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
import io.github.zhztheplayer.velox4j.expression.FieldAccessTypedExpr;
import io.github.zhztheplayer.velox4j.memory.AllocationListener;
import io.github.zhztheplayer.velox4j.memory.MemoryManager;
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.BooleanType;

public class LogicalFunctionsFlinkTest {
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

  private RowVector makeNullableBooleanRowVector(Boolean... values) {
    BufferAllocator alloc = new RootAllocator();
    BitVector vec = new BitVector("c0", alloc);
    vec.allocateNew();
    for (int i = 0; i < values.length; i++) {
      if (values[i] != null) {
        vec.set(i, values[i] ? 1 : 0);
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

  private void assertFunction(RowVector input, String name, Boolean... expected) {
    SelectivityVector sv = session.selectivityVectorOps().create(input.getSize());
    CallTypedExpr expr =
        new CallTypedExpr(
            new BooleanType(), List.of(FieldAccessTypedExpr.create(new BooleanType(), "c0")), name);
    Evaluation eval = new Evaluation(expr, Config.empty(), ConnectorConfig.empty());
    Evaluator evaluator = session.evaluationOps().createEvaluator(eval);
    BaseVector result = evaluator.eval(sv, input);
    try (BufferAllocator alloc = new RootAllocator();
        FieldVector fv = Arrow.toArrowVector(alloc, result)) {
      Assert.assertEquals(expected.length, fv.getValueCount());
      for (int i = 0; i < expected.length; i++) {
        if (expected[i] == null) {
          Assert.assertTrue(name + " row " + i + " should be null", fv.isNull(i));
        } else {
          Assert.assertFalse(name + " row " + i + " should not be null", fv.isNull(i));
          BitVector bitVec = (BitVector) fv;
          Assert.assertEquals(name + " row " + i, expected[i].booleanValue(), bitVec.get(i) == 1);
        }
      }
    }
  }

  @Test
  public void testNotPreservesNull() {
    try (RowVector input = makeNullableBooleanRowVector(true, false, null)) {
      assertFunction(input, "not", false, true, null);
    }
  }

  @Test
  public void testIsTrueNeverReturnsNull() {
    try (RowVector input = makeNullableBooleanRowVector(true, false, null)) {
      assertFunction(input, "is_true", true, false, false);
    }
  }

  @Test
  public void testIsFalseNeverReturnsNull() {
    try (RowVector input = makeNullableBooleanRowVector(true, false, null)) {
      assertFunction(input, "is_false", false, true, false);
    }
  }

  @Test
  public void testIsNotTrueNeverReturnsNull() {
    try (RowVector input = makeNullableBooleanRowVector(true, false, null)) {
      assertFunction(input, "is_not_true", false, true, true);
    }
  }

  @Test
  public void testIsNotFalseNeverReturnsNull() {
    try (RowVector input = makeNullableBooleanRowVector(true, false, null)) {
      assertFunction(input, "is_not_false", true, false, true);
    }
  }
}
