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
import java.util.ArrayList;
import java.util.List;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.TimeStampMicroVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.table.Table;
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
import io.github.zhztheplayer.velox4j.exception.VeloxException;
import io.github.zhztheplayer.velox4j.expression.CallTypedExpr;
import io.github.zhztheplayer.velox4j.expression.ConstantTypedExpr;
import io.github.zhztheplayer.velox4j.expression.FieldAccessTypedExpr;
import io.github.zhztheplayer.velox4j.expression.TypedExpr;
import io.github.zhztheplayer.velox4j.memory.AllocationListener;
import io.github.zhztheplayer.velox4j.memory.MemoryManager;
import io.github.zhztheplayer.velox4j.serde.Serde;
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.BigIntType;
import io.github.zhztheplayer.velox4j.type.DoubleType;
import io.github.zhztheplayer.velox4j.type.IntegerType;
import io.github.zhztheplayer.velox4j.type.TimestampType;
import io.github.zhztheplayer.velox4j.type.VarCharType;
import io.github.zhztheplayer.velox4j.variant.IntegerValue;
import io.github.zhztheplayer.velox4j.variant.TimestampValue;
import io.github.zhztheplayer.velox4j.variant.VarCharValue;

public class ToTimestampFlinkTest {
  private static final String ISO = "yyyy-MM-dd";
  private static final String FULL = "yyyy-MM-dd HH:mm:ss";
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

  private RowVector makeVarcharRowVector(String[]... columns) {
    // The allocator is intentionally left open: ownership of the Arrow buffers
    // is transferred to the native side through the Arrow C data interface (same
    // as ToDateFlinkTest).
    final BufferAllocator alloc = new RootAllocator();
    final List<FieldVector> vectors = new ArrayList<>();
    for (int i = 0; i < columns.length; i++) {
      final VarCharVector vec = new VarCharVector("c" + i, alloc);
      vec.allocateNew();
      for (int j = 0; j < columns[i].length; j++) {
        if (columns[i][j] != null) {
          vec.set(j, columns[i][j].getBytes(StandardCharsets.UTF_8));
        } else {
          vec.setNull(j);
        }
      }
      vec.setValueCount(columns[i].length);
      vectors.add(vec);
    }
    final VectorSchemaRoot root = new VectorSchemaRoot(vectors);
    return session.arrowOps().fromArrowTable(alloc, new Table(root));
  }

  private RowVector makeNumericRowVector(long[] bigints, int[] ints) {
    final BufferAllocator alloc = new RootAllocator();
    final List<FieldVector> vectors = new ArrayList<>();
    final BigIntVector bigVec = new BigIntVector("c0", alloc);
    bigVec.allocateNew();
    for (int i = 0; i < bigints.length; i++) {
      bigVec.set(i, bigints[i]);
    }
    bigVec.setValueCount(bigints.length);
    vectors.add(bigVec);
    if (ints != null) {
      final IntVector intVec = new IntVector("c1", alloc);
      intVec.allocateNew();
      for (int i = 0; i < ints.length; i++) {
        intVec.set(i, ints[i]);
      }
      intVec.setValueCount(ints.length);
      vectors.add(intVec);
    }
    final VectorSchemaRoot root = new VectorSchemaRoot(vectors);
    return session.arrowOps().fromArrowTable(alloc, new Table(root));
  }

  private RowVector makeDoubleRowVector(double... doubles) {
    final BufferAllocator alloc = new RootAllocator();
    final List<FieldVector> vectors = new ArrayList<>();
    final Float8Vector vec = new Float8Vector("c0", alloc);
    vec.allocateNew();
    for (int i = 0; i < doubles.length; i++) {
      vec.set(i, doubles[i]);
    }
    vec.setValueCount(doubles.length);
    vectors.add(vec);
    final VectorSchemaRoot root = new VectorSchemaRoot(vectors);
    return session.arrowOps().fromArrowTable(alloc, new Table(root));
  }

  private BaseVector evalExpr(RowVector input, TypedExpr expr) {
    final SelectivityVector sv = session.selectivityVectorOps().create(input.getSize());
    final Evaluation eval = new Evaluation(expr, Config.empty(), ConnectorConfig.empty());
    final Evaluator evaluator = session.evaluationOps().createEvaluator(eval);
    return evaluator.eval(sv, input);
  }

  private static TypedExpr varcharColumn(int index) {
    return FieldAccessTypedExpr.create(new VarCharType(), "c" + index);
  }

  private static TypedExpr varcharConstant(String value) {
    return new ConstantTypedExpr(new VarCharType(), new VarCharValue(value), null);
  }

  private static TypedExpr bigintColumn() {
    return FieldAccessTypedExpr.create(new BigIntType(), "c0");
  }

  private static TypedExpr doubleColumn() {
    return FieldAccessTypedExpr.create(new DoubleType(), "c0");
  }

  private static TypedExpr intColumn() {
    return FieldAccessTypedExpr.create(new IntegerType(), "c1");
  }

  private static TypedExpr intConstant(int value) {
    return new ConstantTypedExpr(new IntegerType(), new IntegerValue(value), null);
  }

  private static CallTypedExpr toTimestamp(TypedExpr... inputs) {
    return new CallTypedExpr(new TimestampType(), List.of(inputs), "to_timestamp");
  }

  private static CallTypedExpr toTimestampLtz(TypedExpr v, TypedExpr p) {
    return new CallTypedExpr(new TimestampType(), List.of(v, p), "to_timestamp_ltz");
  }

  // expected[i] == null -> row i is null; otherwise {seconds, nanos}.
  private static void assertTimestamps(BaseVector result, long[]... expected) {
    try (BufferAllocator alloc = new RootAllocator();
        FieldVector fv = Arrow.toArrowVector(alloc, result)) {
      Assert.assertEquals(expected.length, fv.getValueCount());
      final TimeStampMicroVector ts = (TimeStampMicroVector) fv;
      for (int i = 0; i < expected.length; i++) {
        if (expected[i] == null) {
          Assert.assertTrue("Row " + i + " should be null", fv.isNull(i));
        } else {
          Assert.assertFalse("Row " + i + " should be non-null", fv.isNull(i));
          Assert.assertEquals(
              "Row " + i + " timestamp mismatch",
              expected[i][0] * 1_000_000L + expected[i][1] / 1_000L,
              ts.get(i));
        }
      }
    }
  }

  @Test
  public void testOneArgPinnedValuesBatch1() {
    try (RowVector input =
            makeVarcharRowVector(
                new String[] {
                  "2026-09-11 10:00:00",
                  "2026-9-1 5:6:7",
                  "2026-09-11",
                  "2026-09-11 10:00:00.123456789"
                });
        BaseVector result = evalExpr(input, toTimestamp(varcharColumn(0)))) {
      assertTimestamps(
          result,
          new long[] {1789120800L, 0L},
          new long[] {1788239167L, 0L},
          new long[] {1789084800L, 0L},
          new long[] {1789120800L, 123000000L});
    }
  }

  @Test
  public void testOneArgPinnedValuesBatch2() {
    // SMART clamping, 24:00 roll, day default, 5-digit year, NULL on garbage.
    try (RowVector input =
            makeVarcharRowVector(
                new String[] {
                  "2026-09-11 24:00:00",
                  "2026-02-30 10:00:00",
                  "2026-09-",
                  "+10000-01-01 00:00:00",
                  "garbage"
                });
        BaseVector result = evalExpr(input, toTimestamp(varcharColumn(0)))) {
      assertTimestamps(
          result,
          new long[] {1789171200L, 0L},
          new long[] {1772272800L, 0L},
          new long[] {1788220800L, 0L},
          new long[] {253402300800L, 0L},
          null);
    }
  }

  @Test
  public void testTwoArgColumnAndConstantFormatPinnedValues() {
    try (RowVector input =
            makeVarcharRowVector(
                new String[] {"2026-09-11 10:00:00", "2026/09/11", "10:00:00"},
                new String[] {FULL, "yyyy/MM/dd", "HH:mm:ss"});
        BaseVector result = evalExpr(input, toTimestamp(varcharColumn(0), varcharColumn(1)))) {
      assertTimestamps(
          result,
          new long[] {1789120800L, 0L},
          new long[] {1789084800L, 0L},
          new long[] {36000L, 0L});
    }
    try (RowVector input = makeVarcharRowVector(new String[] {"2026-09-11", "2026-9-1"});
        BaseVector result = evalExpr(input, toTimestamp(varcharColumn(0), varcharConstant(ISO)))) {
      assertTimestamps(result, new long[] {1789084800L, 0L}, new long[] {1788220800L, 0L});
    }
  }

  @Test
  public void testNullPropagationBothArities() {
    try (RowVector input = makeVarcharRowVector(new String[] {null, "2026-09-11"});
        BaseVector result = evalExpr(input, toTimestamp(varcharColumn(0)))) {
      assertTimestamps(result, null, new long[] {1789084800L, 0L});
    }
    try (RowVector input =
            makeVarcharRowVector(new String[] {null, "2026-09-11"}, new String[] {ISO, null});
        BaseVector result = evalExpr(input, toTimestamp(varcharColumn(0), varcharColumn(1)))) {
      assertTimestamps(result, null, null);
    }
  }

  @Test
  public void testTwoArgParseFailureReturnsNullIncludingBadFormat() {
    // Every failure is a NULL row — an illegal format does NOT throw here
    // (the opposite of to_date's disposition, matching Flink's try-catch).
    try (RowVector input =
            makeVarcharRowVector(
                new String[] {"invalid value", "2026/09/11", "", "2026-09-11 10:00:00"},
                new String[] {ISO, ISO, ISO, "RR"});
        BaseVector result = evalExpr(input, toTimestamp(varcharColumn(0), varcharColumn(1)))) {
      assertTimestamps(result, null, null, null, null);
    }
    try (RowVector input = makeVarcharRowVector(new String[] {"2026-09-11 10:00:00"});
        BaseVector result = evalExpr(input, toTimestamp(varcharColumn(0), varcharConstant("RR")))) {
      assertTimestamps(result, new long[][] {null});
    }
  }

  @Test
  public void testToTimestampLtzDomainAndIllegalPrecision() {
    // J-group representatives: p=0/p=3, negative epoch, out-of-range NULL.
    try (RowVector input =
            makeNumericRowVector(
                new long[] {1760000000L, -1L, 253402300800000L, 1666605683L},
                new int[] {0, 3, 3, 0});
        BaseVector result = evalExpr(input, toTimestampLtz(bigintColumn(), intColumn()))) {
      assertTimestamps(
          result,
          new long[] {1760000000L, 0L},
          new long[] {-1L, 999000000L},
          null,
          new long[] {1666605683L, 0L});
    }
    try (RowVector input = makeDoubleRowVector(123456789.123, -0.9);
        BaseVector result = evalExpr(input, toTimestampLtz(doubleColumn(), intConstant(3)))) {
      // Double p=3: Java (long) truncation toward zero (J13/J14 forms).
      assertTimestamps(result, new long[] {123456L, 789000000L}, new long[] {0L, 0L});
    }
    // An illegal precision throws the Flink-mirroring user error.
    try (RowVector input = makeNumericRowVector(new long[] {123456789L}, new int[] {6})) {
      final VeloxException error =
          Assert.assertThrows(
              VeloxException.class,
              () -> evalExpr(input, toTimestampLtz(bigintColumn(), intColumn())));
      Assert.assertTrue(error.getMessage(), error.getMessage().contains("unsupported"));
    }
  }

  @Test
  public void testTimestampConstantRoundTripAndEval() {
    // The gluten case-TIMESTAMP folding path emits exactly this constant shape.
    // Serde round trip (Java leg): the C++ round-trip leg of
    // SerdeTests#testISerializableRoundTrip drops TimestampType's precision
    // attribute (a pre-existing velox4j gap, see TypeSerdeTest#testTimestampType),
    // so the Java-leg JSON equality is asserted here instead.
    final ConstantTypedExpr constant =
        new ConstantTypedExpr(new TimestampType(), TimestampValue.create(1789120800L, 0L), null);
    final String json = Serde.toPrettyJson(constant);
    final TypedExpr back = Serde.fromJson(json, TypedExpr.class);
    Assert.assertEquals(json, Serde.toPrettyJson(back));
    // Constant-vector evaluation through the native side: the folding channel.
    try (RowVector input = makeVarcharRowVector(new String[] {"a", "b", "c"});
        BaseVector result =
            evalExpr(
                input,
                new ConstantTypedExpr(
                    new TimestampType(), TimestampValue.create(1789120800L, 123000000L), null))) {
      assertTimestamps(
          result,
          new long[] {1789120800L, 123000000L},
          new long[] {1789120800L, 123000000L},
          new long[] {1789120800L, 123000000L});
    }
  }
}
