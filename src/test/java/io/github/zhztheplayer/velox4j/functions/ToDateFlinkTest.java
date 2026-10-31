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
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.FieldVector;
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
import io.github.zhztheplayer.velox4j.serde.SerdeTests;
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.DateType;
import io.github.zhztheplayer.velox4j.type.VarCharType;
import io.github.zhztheplayer.velox4j.variant.IntegerValue;
import io.github.zhztheplayer.velox4j.variant.VarCharValue;

public class ToDateFlinkTest {
  private static final String ISO = "yyyy-MM-dd";
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
    // is transferred to the native side through the Arrow C data interface, so
    // closing it here fails the leak check (same as RegexpExtractFlinkTest).
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

  private static CallTypedExpr toDate(TypedExpr... inputs) {
    return new CallTypedExpr(new DateType(), List.of(inputs), "to_date");
  }

  private static void assertDateDays(BaseVector result, Integer... expectedDays) {
    try (BufferAllocator alloc = new RootAllocator();
        FieldVector fv = Arrow.toArrowVector(alloc, result)) {
      Assert.assertEquals(expectedDays.length, fv.getValueCount());
      final DateDayVector dates = (DateDayVector) fv;
      for (int i = 0; i < expectedDays.length; i++) {
        if (expectedDays[i] == null) {
          Assert.assertTrue("Row " + i + " should be null", fv.isNull(i));
        } else {
          Assert.assertFalse("Row " + i + " should be non-null", fv.isNull(i));
          Assert.assertEquals(
              "Row " + i + " epoch days mismatch", (int) expectedDays[i], dates.get(i));
        }
      }
    }
  }

  @Test
  public void testOneArgPinnedValues() {
    try (RowVector input =
            makeVarcharRowVector(
                new String[] {"2026-09-11", "2026-9-1", "2026-09-11 10:00:00", "9999-12-31"});
        BaseVector result = evalExpr(input, toDate(varcharColumn(0)))) {
      assertDateDays(result, 20707, 20697, 20707, 2932896);
    }
  }

  @Test
  public void testTwoArgColumnFormatPinnedValues() {
    try (RowVector input =
            makeVarcharRowVector(
                new String[] {"2026/09/11", "20260911", "2026", "2026-254"},
                new String[] {"yyyy/MM/dd", "yyyyMMdd", "yyyy", "yyyy-D"});
        BaseVector result = evalExpr(input, toDate(varcharColumn(0), varcharColumn(1)))) {
      assertDateDays(result, 20707, 20707, 20454, 20707);
    }
  }

  @Test
  public void testTwoArgConstantFormatPinnedValues() {
    try (RowVector input = makeVarcharRowVector(new String[] {"2026-09-11", "2026-9-1"});
        BaseVector result = evalExpr(input, toDate(varcharColumn(0), varcharConstant(ISO)))) {
      assertDateDays(result, 20707, 20697);
    }
  }

  @Test
  public void testNullPropagationBothArities() {
    try (RowVector input = makeVarcharRowVector(new String[] {null, "2026-09-11"});
        BaseVector result = evalExpr(input, toDate(varcharColumn(0)))) {
      assertDateDays(result, null, 20707);
    }
    try (RowVector input =
            makeVarcharRowVector(new String[] {null, "2026-09-11"}, new String[] {ISO, null});
        BaseVector result = evalExpr(input, toDate(varcharColumn(0), varcharColumn(1)))) {
      assertDateDays(result, null, null);
    }
  }

  @Test
  public void testTwoArgParseFailureReturnsNull() {
    try (RowVector input =
            makeVarcharRowVector(
                new String[] {"invalid value", "2026/09/11", "", "2026-09-11"},
                new String[] {ISO, ISO, ISO, "dd-MM-yyyy"});
        BaseVector result = evalExpr(input, toDate(varcharColumn(0), varcharColumn(1)))) {
      assertDateDays(result, null, null, null, null);
    }
  }

  @Test
  public void testInvalidFormatThrowsUserError() {
    try (RowVector input = makeVarcharRowVector(new String[] {"2026-09-11"})) {
      final VeloxException constFormatError =
          Assert.assertThrows(
              VeloxException.class,
              () -> evalExpr(input, toDate(varcharColumn(0), varcharConstant("QQ"))));
      Assert.assertTrue(
          constFormatError.getMessage(), constFormatError.getMessage().contains("not supported"));
    }
    try (RowVector input = makeVarcharRowVector(new String[] {"2026-09-11"}, new String[] {"QQ"})) {
      final VeloxException columnFormatError =
          Assert.assertThrows(
              VeloxException.class,
              () -> evalExpr(input, toDate(varcharColumn(0), varcharColumn(1))));
      Assert.assertTrue(
          columnFormatError.getMessage(), columnFormatError.getMessage().contains("not supported"));
    }
  }

  @Test
  public void testOneArgYearOverflowThrowsUserError() {
    try (RowVector input = makeVarcharRowVector(new String[] {"9999999999"})) {
      final VeloxException error =
          Assert.assertThrows(
              VeloxException.class, () -> evalExpr(input, toDate(varcharColumn(0))));
      Assert.assertTrue(error.getMessage(), error.getMessage().contains("out of int range"));
    }
  }

  @Test
  public void testDateConstantRoundTripAndEval() {
    // The gluten case-DATE folding path emits exactly this constant shape.
    SerdeTests.testISerializableRoundTrip(
        new ConstantTypedExpr(new DateType(), new IntegerValue(20707), null));
    try (RowVector input = makeVarcharRowVector(new String[] {"a", "b", "c"});
        BaseVector result =
            evalExpr(input, new ConstantTypedExpr(new DateType(), new IntegerValue(20707), null))) {
      assertDateDays(result, 20707, 20707, 20707);
    }
    try (RowVector input = makeVarcharRowVector(new String[] {"a", "b", "c"});
        BaseVector result =
            evalExpr(
                input, new ConstantTypedExpr(new DateType(), new IntegerValue(-719528), null))) {
      assertDateDays(result, -719528, -719528, -719528);
    }
  }
}
