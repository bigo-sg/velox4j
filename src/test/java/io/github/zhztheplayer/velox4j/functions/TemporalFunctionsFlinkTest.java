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
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.TimeStampMicroVector;
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
import io.github.zhztheplayer.velox4j.type.TimestampType;

/**
 * velox4j-level invocation tests for the flinksql temporal functions: current_timestamp, now,
 * localtimestamp (TIMESTAMP under the session timezone), and localtime (INT millisecond-of-day
 * under the session timezone), matching Flink's streaming semantics.
 */
public class TemporalFunctionsFlinkTest {
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

  /**
   * Mirrors gluten-flink's VeloxQueryConfig: the session timezone only takes effect when
   * adjust_timestamp_to_session_timezone is enabled.
   */
  private static Config createFlinkTimezoneConfig() {
    return Config.create(
        Map.of("session_timezone", ZONE.getId(), "adjust_timestamp_to_session_timezone", "true"));
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

  private BaseVector evalFunction(
      RowVector input, String functionName, io.github.zhztheplayer.velox4j.type.Type resultType) {
    SelectivityVector sv = session.selectivityVectorOps().create(input.getSize());
    CallTypedExpr expr = new CallTypedExpr(resultType, List.of(), functionName);
    Evaluation eval = new Evaluation(expr, createFlinkTimezoneConfig(), ConnectorConfig.empty());
    Evaluator evaluator = session.evaluationOps().createEvaluator(eval);
    return evaluator.eval(sv, input);
  }

  private long[] evalTimestampFunction(RowVector input, String functionName) {
    BaseVector result = evalFunction(input, functionName, new TimestampType());
    try (BufferAllocator alloc = new RootAllocator();
        FieldVector fv = Arrow.toArrowVector(alloc, result)) {
      Assert.assertEquals(input.getSize(), fv.getValueCount());
      TimeStampMicroVector tv = (TimeStampMicroVector) fv;
      long[] values = new long[input.getSize()];
      for (int i = 0; i < input.getSize(); i++) {
        values[i] = tv.get(i);
      }
      return values;
    }
  }

  @Test
  public void testCurrentTimestampIsEpochMicrosWithinBoundedDelta() {
    try (RowVector input = makeRowVector(3)) {
      long[] values = evalTimestampFunction(input, "current_timestamp");
      long t0 = System.currentTimeMillis() - 5_000;
      long t1 = System.currentTimeMillis() + 5_000;
      for (int i = 0; i < values.length; i++) {
        long millis = values[i] / 1_000;
        Assert.assertTrue(
            "current_timestamp drifted more than 5s from the wall clock: " + millis,
            millis >= t0 && millis <= t1);
      }
    }
  }

  @Test
  public void testNowIsAliasOfCurrentTimestamp() {
    try (RowVector input = makeRowVector(3)) {
      long[] values = evalTimestampFunction(input, "now");
      long t0 = System.currentTimeMillis() - 5_000;
      long t1 = System.currentTimeMillis() + 5_000;
      for (int i = 0; i < values.length; i++) {
        long millis = values[i] / 1_000;
        Assert.assertTrue(
            "now drifted more than 5s from the wall clock: " + millis,
            millis >= t0 && millis <= t1);
      }
    }
  }

  @Test
  public void testLocaltimestampIsSessionTimezoneEpochMicros() {
    try (RowVector input = makeRowVector(3)) {
      long[] values = evalTimestampFunction(input, "localtimestamp");
      // localtimestamp = current_timestamp shifted by the session timezone offset.
      // Under Asia/Shanghai (+8h), local wall-clock millis = epoch millis + 8h.
      long offsetMillis = ZONE.getRules().getOffset(Instant.now()).getTotalSeconds() * 1000L;
      long t0Wall = System.currentTimeMillis() + offsetMillis - 5_000;
      long t1Wall = System.currentTimeMillis() + offsetMillis + 5_000;
      for (int i = 0; i < values.length; i++) {
        long millis = values[i] / 1_000;
        Assert.assertTrue(
            "localtimestamp drifted more than 5s from the session-timezone wall clock: " + millis,
            millis >= t0Wall && millis <= t1Wall);
      }
    }
  }

  @Test
  public void testLocaltimeIsMillisOfDayUnderSessionTimezone() {
    try (RowVector input = makeRowVector(3)) {
      BaseVector result = evalFunction(input, "localtime", new IntegerType());
      try (BufferAllocator alloc = new RootAllocator();
          FieldVector fv = Arrow.toArrowVector(alloc, result)) {
        Assert.assertEquals(input.getSize(), fv.getValueCount());
        IntVector iv = (IntVector) fv;
        for (int i = 0; i < input.getSize(); i++) {
          int millisOfDay = iv.get(i);
          Assert.assertTrue(
              "localtime millis-of-day must be in [0, 86400000), got " + millisOfDay,
              millisOfDay >= 0 && millisOfDay < 86_400_000);
          long expected = LocalTime.now(ZONE).toNanoOfDay() / 1_000_000;
          long delta = Math.abs(millisOfDay - expected);
          long bounded = Math.min(delta, 86_400_000 - delta);
          Assert.assertTrue(
              "localtime drifted more than 5s from the session-timezone wall clock",
              bounded <= 5_000);
        }
      }
    }
  }
}
