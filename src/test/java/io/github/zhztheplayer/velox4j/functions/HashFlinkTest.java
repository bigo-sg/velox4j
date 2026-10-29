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
import io.github.zhztheplayer.velox4j.expression.FieldAccessTypedExpr;
import io.github.zhztheplayer.velox4j.memory.AllocationListener;
import io.github.zhztheplayer.velox4j.memory.MemoryManager;
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.VarCharType;

public class HashFlinkTest {
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
    return session.arrowOps().fromArrowTable(alloc, new org.apache.arrow.vector.table.Table(root));
  }

  private BaseVector evalHash(RowVector input, String functionName) {
    CallTypedExpr hashExpr =
        new CallTypedExpr(
            new VarCharType(),
            List.of(FieldAccessTypedExpr.create(new VarCharType(), "c0")),
            functionName);
    Evaluation eval = new Evaluation(hashExpr, Config.empty(), ConnectorConfig.empty());
    try (SelectivityVector sv = session.selectivityVectorOps().create(input.getSize());
        Evaluator evaluator = session.evaluationOps().createEvaluator(eval)) {
      return evaluator.eval(sv, input);
    }
  }

  @Test
  public void testMd5() {
    try (RowVector input = makeVarcharRowVector("test", "你好", null);
        BaseVector result = evalHash(input, "md5")) {
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        Assert.assertEquals(3, fv.getValueCount());
        VarCharVector vc = (VarCharVector) fv;
        Assert.assertEquals(
            "098f6bcd4621d373cade4e832627b4f6", new String(vc.get(0), StandardCharsets.UTF_8));
        Assert.assertEquals(
            "7eca689f0d3389d9dea66ae112e5cfd7", new String(vc.get(1), StandardCharsets.UTF_8));
        Assert.assertTrue("null input should yield null", fv.isNull(2));
      }
    }
  }

  @Test
  public void testSha1() {
    try (RowVector input = makeVarcharRowVector("test", "你好", null);
        BaseVector result = evalHash(input, "sha1")) {
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        Assert.assertEquals(3, fv.getValueCount());
        VarCharVector vc = (VarCharVector) fv;
        Assert.assertEquals(
            "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3",
            new String(vc.get(0), StandardCharsets.UTF_8));
        Assert.assertEquals(
            "440ee0853ad1e99f962b63e459ef992d7c211722",
            new String(vc.get(1), StandardCharsets.UTF_8));
        Assert.assertTrue("null input should yield null", fv.isNull(2));
      }
    }
  }
}
