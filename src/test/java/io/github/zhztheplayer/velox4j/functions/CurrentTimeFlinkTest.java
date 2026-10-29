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
import java.time.LocalTime;
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
import io.github.zhztheplayer.velox4j.type.IntegerType;

/**
 * velox4j-level invocation tests for the flinksql current_time function: an INT vector of
 * millisecond-of-day values under the session timezone, matching Flink's CURRENT_TIME semantics.
 */
public class CurrentTimeFlinkTest {
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

  private BaseVector evalCurrentTime(RowVector input) {
    SelectivityVector sv = session.selectivityVectorOps().create(input.getSize());
    CallTypedExpr expr = new CallTypedExpr(new IntegerType(), List.of(), "current_time");
    Evaluation eval =
        new Evaluation(
            expr, Config.create(Map.of("session_timezone", ZONE.getId())), ConnectorConfig.empty());
    Evaluator evaluator = session.evaluationOps().createEvaluator(eval);
    return evaluator.eval(sv, input);
  }

  @Test
  public void testCurrentTimeIsMillisOfDayUnderSessionTimezone() {
    try (RowVector input = makeRowVector(2)) {
      BaseVector result = evalCurrentTime(input);
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        Assert.assertEquals(2, fv.getValueCount());
        IntVector iv = (IntVector) fv;
        for (int i = 0; i < 2; i++) {
          int millisOfDay = iv.get(i);
          Assert.assertTrue(
              "millis-of-day must be in [0, 86400000), got " + millisOfDay,
              millisOfDay >= 0 && millisOfDay < 86_400_000);
          long expected = LocalTime.now(ZONE).toNanoOfDay() / 1_000_000;
          long delta = Math.abs(millisOfDay - expected);
          // Allow the comparison to straddle midnight and up to 5s of test
          // execution delay between evaluation and assertion.
          long bounded = Math.min(delta, 86_400_000 - delta);
          Assert.assertTrue(
              "current_time drifted more than 5s from the wall clock under " + ZONE,
              bounded <= 5_000);
        }
      }
    }
  }
}
