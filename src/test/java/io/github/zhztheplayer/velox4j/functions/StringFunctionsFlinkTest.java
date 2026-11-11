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
import org.apache.arrow.vector.BitVector;
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
import io.github.zhztheplayer.velox4j.type.BooleanType;
import io.github.zhztheplayer.velox4j.type.IntegerType;
import io.github.zhztheplayer.velox4j.type.VarCharType;
import io.github.zhztheplayer.velox4j.variant.IntegerValue;
import io.github.zhztheplayer.velox4j.variant.VarCharValue;

/**
 * Asserts the FLINK preset resolves the string functions to the flinksql registrations (not the
 * sparksql baseline): the one-argument trim removes only the 0x20 space, the two-argument forms
 * keep the Flink (str, seek) argument order, the *_runtime names resolve with the isSpaceString
 * degradation, lpad/rpad return NULL for a negative length instead of raising, an empty REPLACE
 * `from` inserts at UTF-8 code-point boundaries (prestosql's byte-level `replace` would insert
 * between bytes and the spark variant ignores an empty search), RIGHT counts code points, and an
 * empty LIKE pattern vacuously matches every input per the Flink 1.19.2 runtime (unlike the
 * SQL-standard reading that matches only the empty string).
 */
public class StringFunctionsFlinkTest {
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

  private RowVector makeVarcharRowVector(String col0, String col1) {
    BufferAllocator alloc = new RootAllocator();
    VarCharVector vec0 = new VarCharVector("c0", alloc);
    VarCharVector vec1 = new VarCharVector("c1", alloc);
    vec0.allocateNew();
    vec1.allocateNew();
    vec0.set(0, col0.getBytes(StandardCharsets.UTF_8));
    vec1.set(0, col1.getBytes(StandardCharsets.UTF_8));
    vec0.setValueCount(1);
    vec1.setValueCount(1);
    VectorSchemaRoot root = VectorSchemaRoot.of(vec0, vec1);
    RowVector rv =
        session.arrowOps().fromArrowTable(alloc, new org.apache.arrow.vector.table.Table(root));
    return rv;
  }

  private RowVector makeNullableVarcharRowVector(String value) {
    BufferAllocator alloc = new RootAllocator();
    VarCharVector vec = new VarCharVector("c0", alloc);
    vec.allocateNew();
    if (value != null) {
      vec.set(0, value.getBytes(StandardCharsets.UTF_8));
    } else {
      vec.setNull(0);
    }
    vec.setValueCount(1);
    VectorSchemaRoot root = VectorSchemaRoot.of(vec);
    return session.arrowOps().fromArrowTable(alloc, new org.apache.arrow.vector.table.Table(root));
  }

  private BaseVector eval(RowVector input, TypedExpr expr) {
    SelectivityVector sv = session.selectivityVectorOps().create(input.getSize());
    Evaluation eval = new Evaluation(expr, Config.empty(), ConnectorConfig.empty());
    Evaluator evaluator = session.evaluationOps().createEvaluator(eval);
    return evaluator.eval(sv, input);
  }

  private TypedExpr trimOneArg() {
    return new CallTypedExpr(
        new VarCharType(), List.of(FieldAccessTypedExpr.create(new VarCharType(), "c0")), "trim");
  }

  private TypedExpr trimTwoArg(String name, String seek) {
    return new CallTypedExpr(
        new VarCharType(),
        List.of(
            FieldAccessTypedExpr.create(new VarCharType(), "c0"),
            new ConstantTypedExpr(new VarCharType(), new VarCharValue(seek), null)),
        name);
  }

  private TypedExpr trimTwoArgColumns(String name) {
    return new CallTypedExpr(
        new VarCharType(),
        List.of(
            FieldAccessTypedExpr.create(new VarCharType(), "c0"),
            FieldAccessTypedExpr.create(new VarCharType(), "c1")),
        name);
  }

  private TypedExpr pad(String name, int len, String pad) {
    return new CallTypedExpr(
        new VarCharType(),
        List.of(
            FieldAccessTypedExpr.create(new VarCharType(), "c0"),
            new ConstantTypedExpr(new IntegerType(), new IntegerValue(len), null),
            new ConstantTypedExpr(new VarCharType(), new VarCharValue(pad), null)),
        name);
  }

  private TypedExpr varcharConstant(String value) {
    return new ConstantTypedExpr(new VarCharType(), new VarCharValue(value), null);
  }

  private TypedExpr replaceExpr(String from, String to) {
    return new CallTypedExpr(
        new VarCharType(),
        List.of(
            FieldAccessTypedExpr.create(new VarCharType(), "c0"),
            varcharConstant(from),
            varcharConstant(to)),
        "replace");
  }

  private TypedExpr rightExpr(int len) {
    return new CallTypedExpr(
        new VarCharType(),
        List.of(
            FieldAccessTypedExpr.create(new VarCharType(), "c0"),
            new ConstantTypedExpr(new IntegerType(), new IntegerValue(len), null)),
        "right");
  }

  private TypedExpr fromBase64Expr() {
    return new CallTypedExpr(
        new VarCharType(),
        List.of(FieldAccessTypedExpr.create(new VarCharType(), "c0")),
        "from_base64");
  }

  private TypedExpr likeExpr(String pattern, String escape) {
    if (escape == null) {
      return new CallTypedExpr(
          new BooleanType(),
          List.of(FieldAccessTypedExpr.create(new VarCharType(), "c0"), varcharConstant(pattern)),
          "like");
    }
    return new CallTypedExpr(
        new BooleanType(),
        List.of(
            FieldAccessTypedExpr.create(new VarCharType(), "c0"),
            varcharConstant(pattern),
            varcharConstant(escape)),
        "like");
  }

  private String getString(BaseVector result) {
    try (BufferAllocator alloc = new RootAllocator();
        FieldVector fv = Arrow.toArrowVector(alloc, result)) {
      Assert.assertEquals(1, fv.getValueCount());
      if (fv.isNull(0)) {
        return null;
      }
      return new String(((VarCharVector) fv).get(0), StandardCharsets.UTF_8);
    }
  }

  private Boolean getBoolean(BaseVector result) {
    try (BufferAllocator alloc = new RootAllocator();
        FieldVector fv = Arrow.toArrowVector(alloc, result)) {
      Assert.assertEquals(1, fv.getValueCount());
      if (fv.isNull(0)) {
        return null;
      }
      return ((BitVector) fv).get(0) == 1;
    }
  }

  @Test
  public void testTrimRemovesOnlySpaces() {
    try (RowVector input = makeVarcharRowVector(" \tabc ", "unused")) {
      Assert.assertEquals("\tabc", getString(eval(input, trimOneArg())));
    }
    try (RowVector input = makeVarcharRowVector("  ab  ", "unused")) {
      Assert.assertEquals("ab", getString(eval(input, trimOneArg())));
    }
  }

  @Test
  public void testTwoArgTrimKeepsFlinkArgumentOrder() {
    // Flink order (str, seek): trimming "abx " of the {' ', 'x'} set yields "ab".
    // The sparksql baseline registers the reversed (trimStr, srcStr) order and
    // would return "" here, so this asserts the flinksql registration.
    try (RowVector input = makeVarcharRowVector("abx ", "unused")) {
      Assert.assertEquals("ab", getString(eval(input, trimTwoArg("trim", " x"))));
      Assert.assertEquals("ab", getString(eval(input, trimTwoArg("rtrim", " x"))));
    }
    try (RowVector input = makeVarcharRowVector(" xab", "unused")) {
      Assert.assertEquals("ab", getString(eval(input, trimTwoArg("ltrim", " x"))));
      Assert.assertEquals("ab", getString(eval(input, trimTwoArg("trim", " x"))));
    }
  }

  @Test
  public void testRuntimeSeekFlavorDegradesToSpaces() {
    // The *_runtime names exist only in the flinksql registrations; resolving
    // them at all proves the FLINK preset loaded the flinksql functions. A
    // column-sourced seek whose first byte is a space trims spaces only.
    try (RowVector input = makeVarcharRowVector("abx ", " x")) {
      Assert.assertEquals("abx", getString(eval(input, trimTwoArgColumns("rtrim_runtime"))));
      Assert.assertEquals("abx", getString(eval(input, trimTwoArgColumns("trim_runtime"))));
      Assert.assertEquals("abx ", getString(eval(input, trimTwoArgColumns("ltrim_runtime"))));
    }
    try (RowVector input = makeVarcharRowVector(" xab", " x")) {
      Assert.assertEquals("xab", getString(eval(input, trimTwoArgColumns("ltrim_runtime"))));
      Assert.assertEquals("xab", getString(eval(input, trimTwoArgColumns("trim_runtime"))));
    }
    // A seek that does not start with a space takes the character-set walk.
    try (RowVector input = makeVarcharRowVector("abx ", "x ")) {
      Assert.assertEquals("ab", getString(eval(input, trimTwoArgColumns("rtrim_runtime"))));
    }
  }

  @Test
  public void testPadFlinkBoundaries() {
    // Flink returns NULL for a negative length instead of raising.
    try (RowVector input = makeVarcharRowVector("abc", "unused")) {
      Assert.assertNull(getString(eval(input, pad("lpad", -1, "*"))));
      Assert.assertNull(getString(eval(input, pad("rpad", -1, "*"))));
      Assert.assertNull(getString(eval(input, pad("lpad", 5, ""))));
      Assert.assertEquals("", getString(eval(input, pad("lpad", 0, "*"))));
    }
    try (RowVector input = makeVarcharRowVector("text", "unused")) {
      Assert.assertEquals("xtext", getString(eval(input, pad("lpad", 5, "x"))));
      Assert.assertEquals("textx", getString(eval(input, pad("rpad", 5, "x"))));
      Assert.assertEquals("xyxtext", getString(eval(input, pad("lpad", 7, "xy"))));
      Assert.assertEquals("textxyx", getString(eval(input, pad("rpad", 7, "xy"))));
      Assert.assertEquals("tex", getString(eval(input, pad("lpad", 3, "xy"))));
    }
  }

  @Test
  public void testReplaceEmptyFromUtf8Boundaries() {
    // Empty `from` inserts `to` at UTF-8 code-point boundaries, matching Flink's
    // String.replace — the flinksql vector registration overrides prestosql's
    // byte-level `replace` (which would split multibyte characters), and the
    // spark variant ignores an empty search, so this asserts the flinksql
    // registration.
    try (RowVector input = makeNullableVarcharRowVector("abc")) {
      Assert.assertEquals("xaxbxcx", getString(eval(input, replaceExpr("", "x"))));
    }
    try (RowVector input = makeNullableVarcharRowVector("你好")) {
      Assert.assertEquals("x你x好x", getString(eval(input, replaceExpr("", "x"))));
    }
  }

  @Test
  public void testRight() {
    // RIGHT counts UTF-8 code points: n <= 0 yields the empty string, n beyond
    // the length the whole string, and a multibyte input stays intact.
    try (RowVector input = makeNullableVarcharRowVector("abc")) {
      Assert.assertEquals("bc", getString(eval(input, rightExpr(2))));
      Assert.assertEquals("", getString(eval(input, rightExpr(0))));
      Assert.assertEquals("abc", getString(eval(input, rightExpr(5))));
    }
    try (RowVector input = makeNullableVarcharRowVector("你好")) {
      Assert.assertEquals("好", getString(eval(input, rightExpr(1))));
    }
  }

  @Test
  public void testFromBase64() {
    // Unpadded and empty inputs both decode correctly; the flinksql
    // registration returns VARCHAR where prestosql's returns VARBINARY.
    try (RowVector input = makeNullableVarcharRowVector("dGVzdA")) {
      Assert.assertEquals("test", getString(eval(input, fromBase64Expr())));
    }
    try (RowVector input = makeNullableVarcharRowVector("")) {
      Assert.assertEquals("", getString(eval(input, fromBase64Expr())));
    }
  }

  @Test
  public void testLikeVacuousEmptyPattern() {
    // An empty pattern vacuously matches every input, per the Flink 1.19.2
    // runtime (verified by an end-to-end native-vs-offloaded dual-run); the
    // SQL-standard reading (and the baseline LIKE) would match only the empty
    // string, so this asserts the flinksql registration.
    try (RowVector input = makeNullableVarcharRowVector("abc")) {
      Assert.assertEquals(Boolean.TRUE, getBoolean(eval(input, likeExpr("", null))));
    }
    try (RowVector input = makeNullableVarcharRowVector("")) {
      Assert.assertEquals(Boolean.TRUE, getBoolean(eval(input, likeExpr("", null))));
    }
  }

  @Test
  public void testLikeEscape() {
    // '#' escapes the '%' into a literal percent.
    try (RowVector input = makeNullableVarcharRowVector("a%b")) {
      Assert.assertEquals(Boolean.TRUE, getBoolean(eval(input, likeExpr("a#%b", "#"))));
    }
    try (RowVector input = makeNullableVarcharRowVector("axb")) {
      Assert.assertEquals(Boolean.FALSE, getBoolean(eval(input, likeExpr("a#%b", "#"))));
    }
    // A non-ASCII escape (one Java char, multi-byte UTF-8) is accepted too,
    // per Flink's SqlLikeUtils — a byte-counted check would reject it.
    try (RowVector input = makeNullableVarcharRowVector("a%b")) {
      Assert.assertEquals(Boolean.TRUE, getBoolean(eval(input, likeExpr("aé%b", "é"))));
    }
    try (RowVector input = makeNullableVarcharRowVector("axb")) {
      Assert.assertEquals(Boolean.FALSE, getBoolean(eval(input, likeExpr("aé%b", "é"))));
    }
  }
}
