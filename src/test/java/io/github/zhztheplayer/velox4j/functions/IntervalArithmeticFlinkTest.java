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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.github.zhztheplayer.velox4j.Velox4j;
import io.github.zhztheplayer.velox4j.config.Config;
import io.github.zhztheplayer.velox4j.config.ConnectorConfig;
import io.github.zhztheplayer.velox4j.data.BaseVector;
import io.github.zhztheplayer.velox4j.data.RowVector;
import io.github.zhztheplayer.velox4j.data.SelectivityVector;
import io.github.zhztheplayer.velox4j.eval.Evaluation;
import io.github.zhztheplayer.velox4j.eval.Evaluator;
import io.github.zhztheplayer.velox4j.expression.CallTypedExpr;
import io.github.zhztheplayer.velox4j.expression.ConstantTypedExpr;
import io.github.zhztheplayer.velox4j.expression.TypedExpr;
import io.github.zhztheplayer.velox4j.memory.AllocationListener;
import io.github.zhztheplayer.velox4j.memory.MemoryManager;
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.IntervalDayTimeType;
import io.github.zhztheplayer.velox4j.type.IntervalYearMonthType;
import io.github.zhztheplayer.velox4j.type.TimestampType;
import io.github.zhztheplayer.velox4j.variant.BigIntValue;
import io.github.zhztheplayer.velox4j.variant.IntegerValue;
import io.github.zhztheplayer.velox4j.variant.TimestampValue;

public class IntervalArithmeticFlinkTest {
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

  // 2017-11-29 10:58:58.998 UTC.
  private static final long SEC = 1511947138L;
  private static final long NANOS = 998000000L;

  private long[] evalIntervalArithmetic(String fn, TypedExpr intervalConst) {
    return evalIntervalArithmeticWithTs(fn, intervalConst, SEC, NANOS);
  }

  @Test
  public void testPlusIntervalDayTime() {
    TypedExpr interval =
        new ConstantTypedExpr(new IntervalDayTimeType(), new BigIntValue(8L * 3600_000L), null);
    long[] r = evalIntervalArithmetic("plus", interval);
    Assert.assertArrayEquals(new long[] {SEC + 8 * 3600, NANOS}, r);
  }

  @Test
  public void testPlusIntervalYearMonthClamp() {
    // 2025-01-31 23:59:59.999 + 1 month -> 2025-02-28 23:59:59.999 (clamped).
    TypedExpr interval =
        new ConstantTypedExpr(new IntervalYearMonthType(), new IntegerValue(1), null);
    long[] r = evalIntervalArithmeticWithTs("plus", interval, 1738367999L, 999000000L);
    Assert.assertArrayEquals(new long[] {1740787199L, 999000000L}, r);
  }

  private long[] evalIntervalArithmeticWithTs(
      String fn, TypedExpr intervalConst, long sec, long nanos) {
    RowVector input = ArrowShims.oneNullRow(session);
    SelectivityVector sv = session.selectivityVectorOps().create(input.getSize());
    TypedExpr tsConst =
        new ConstantTypedExpr(new TimestampType(), TimestampValue.create(sec, nanos), null);
    CallTypedExpr expr =
        new CallTypedExpr(new TimestampType(), List.of(tsConst, intervalConst), fn);
    Evaluation eval = new Evaluation(expr, Config.empty(), ConnectorConfig.empty());
    Evaluator evaluator = session.evaluationOps().createEvaluator(eval);
    BaseVector result = evaluator.eval(sv, input);
    return ArrowShims.readTimestampMicros(result);
  }
}
