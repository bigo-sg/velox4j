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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.IntVector;
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
import io.github.zhztheplayer.velox4j.memory.AllocationListener;
import io.github.zhztheplayer.velox4j.memory.MemoryManager;
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.DateType;

/**
 * velox4j-level invocation tests for the flinksql current_date function: an INT vector of epoch
 * days under the session timezone, matching Flink's CURRENT_DATE semantics.
 */
public class CurrentDateFlinkTest {
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
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

  private RowVector makeRowVector(int rows) {
    BufferAllocator alloc = new RootAllocator();
    VarCharVector vec = new VarCharVector("c0", alloc);
    vec.allocateNew();
    for (int i = 0; i < rows; i++) {
      vec.set(i, ("row" + i).getBytes(StandardCharsets.UTF_8));
    }
    vec.setValueCount(rows);
    VectorSchemaRoot root = VectorSchemaRoot.of(vec);
    RowVector rv =
        session.arrowOps().fromArrowTable(alloc, new org.apache.arrow.vector.table.Table(root));
    return rv;
  }

  private BaseVector evalCurrentDate(RowVector input) {
    SelectivityVector sv = session.selectivityVectorOps().create(input.getSize());
    CallTypedExpr expr = new CallTypedExpr(new DateType(), List.of(), "current_date");
    Evaluation eval =
        new Evaluation(
            expr, Config.create(Map.of("session_timezone", ZONE.getId())), ConnectorConfig.empty());
    Evaluator evaluator = session.evaluationOps().createEvaluator(eval);
    return evaluator.eval(sv, input);
  }

  @Test
  public void testCurrentDateIsEpochDayUnderSessionTimezone() {
    try (RowVector input = makeRowVector(2)) {
      BaseVector result = evalCurrentDate(input);
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        Assert.assertEquals(2, fv.getValueCount());
        IntVector iv = (IntVector) fv;
        int expected = (int) LocalDate.now(ZONE).toEpochDay();
        // CURRENT_DATE reads the clock per row; both rows fall on the same
        // day unless the test straddles midnight, so compare against the
        // day computed at assertion time and accept the previous/next day
        // only at the boundary.
        int v0 = iv.get(0);
        int v1 = iv.get(1);
        Assert.assertEquals("per-row current_date should be consistent", v0, v1);
        Assert.assertTrue(
            "current_date should be the epoch day under " + ZONE + ", got " + v0,
            v0 == expected || v0 == expected - 1);
      }
    }
  }
}
