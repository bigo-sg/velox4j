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
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.IntVector;
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
import io.github.zhztheplayer.velox4j.session.Session;
import io.github.zhztheplayer.velox4j.test.Velox4jTests;
import io.github.zhztheplayer.velox4j.type.BigIntType;
import io.github.zhztheplayer.velox4j.type.IntegerType;
import io.github.zhztheplayer.velox4j.type.VarCharType;
import io.github.zhztheplayer.velox4j.variant.BigIntValue;
import io.github.zhztheplayer.velox4j.variant.IntegerValue;

public class Sha2FlinkTest {
  // Digests pinned from Flink's own test suite ("", "test") and probed
  // independently for the UTF-8 and long inputs.
  private static final String SHA224_EMPTY =
      "d14a028c2a3a2bc9476102bb288234c415a2b01f828ea62ac5b3e42f";
  private static final String SHA256_EMPTY =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
  private static final String SHA384_EMPTY =
      "38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc"
          + "7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b";
  private static final String SHA512_EMPTY =
      "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d"
          + "0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e";
  private static final String SHA224_TEST =
      "90a3ed9e32b2aaf4c61c410eb925426119e1a9dc53d4286ade99a809";
  private static final String SHA256_TEST =
      "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
  private static final String SHA384_TEST =
      "768412320f7b0aa5812fce428dc4706b3cae50e02a64caa16a782249bfe8efc"
          + "4b7ef1ccb126255d196047dfedf17a0a9";
  private static final String SHA512_TEST =
      "ee26b0dd4af7e749aa1a8ee3c10ae9923f618980772e473f8819a5d4940e0db"
          + "27ac185f8a0e1d5f84f88bc887fd67b143732c304cc5fa9ad8e6f57f50028a8ff";
  private static final String SHA224_UTF8 =
      "b3f5c93d7b531d7e29266412f2c842f8a4b7286871f87d259c6eaf07";
  private static final String SHA256_UTF8 =
      "beca6335b20ff57ccc47403ef4d9e0b8fccb4442b3151c2e7d50050673d43172";
  private static final String SHA384_UTF8 =
      "5621250177cc297c02d4c7c2a950d541a52b5c478e1fa16ca5022316de898d7be5c"
          + "66b16ad735295b48b84a47e986144";
  private static final String SHA512_UTF8 =
      "4b28a152c8e203ebb52e099301041e3cf704a56190d3097ec8b086a0f9bfb4b9d533"
          + "ce71fc3bcf374359e506dc5f17322ec3911eac8dd8f5b35308d938ba0c26";
  private static final String SHA224_LONG =
      "e6e4a6be069cc9bead8b6050856d2b26da6b3f7efa0951e5fb3a54dd";
  private static final String SHA256_LONG =
      "74e7e5bb9d22d6db26bf76946d40fff3ea9f0346b884fd0694920fccfad15e33";
  private static final String SHA384_LONG =
      "ce6d4ea5442bc6c830bea1942d4860db9f7b96f0e9d2c3073ffe47a0e1166d95612d"
          + "840ff15e5efdd23c1f273096da32";
  private static final String SHA512_LONG =
      "95cadc34aa46b9fdef432f62fe5bad8d9f475bfbecf797d5802bb5f2937a85d93ce48"
          + "57a6262b03834c01c610d74cd1215f9a466dc6ad3dd15078e3309a03a6d";
  private static final String EMPTY = "";
  private static final String TEST = "test";
  private static final String UTF8 = "你好世界";
  private static final String LONG = "0123456789abcdefghijklmnopqrstuvwxyz";

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

  private RowVector makeRowVector(String... varcharColumn) {
    // The explicit Object[] wrap keeps the single String[] column from being
    // spread into the varargs parameter list.
    return makeRowVectorImpl(new Object[] {varcharColumn});
  }

  private RowVector makeRowVector(String[] varcharColumn, Integer[] intColumn) {
    return makeRowVectorImpl(new Object[] {varcharColumn, intColumn});
  }

  private RowVector makeRowVectorImpl(Object... columns) {
    // The allocator is intentionally left open: ownership of the Arrow buffers
    // is transferred to the native side through the Arrow C data interface, so
    // closing it here fails the leak check (same as ToDateFlinkTest).
    final BufferAllocator alloc = new RootAllocator();
    final List<FieldVector> vectors = new ArrayList<>();
    for (int i = 0; i < columns.length; i++) {
      final FieldVector vec;
      if (columns[i] instanceof String[]) {
        final String[] column = (String[]) columns[i];
        final VarCharVector varchar = new VarCharVector("c" + i, alloc);
        varchar.allocateNew();
        for (int j = 0; j < column.length; j++) {
          if (column[j] != null) {
            varchar.set(j, column[j].getBytes(StandardCharsets.UTF_8));
          } else {
            varchar.setNull(j);
          }
        }
        varchar.setValueCount(column.length);
        vec = varchar;
      } else {
        final Integer[] column = (Integer[]) columns[i];
        final IntVector ints = new IntVector("c" + i, alloc);
        ints.allocateNew();
        for (int j = 0; j < column.length; j++) {
          if (column[j] != null) {
            ints.set(j, column[j]);
          } else {
            ints.setNull(j);
          }
        }
        ints.setValueCount(column.length);
        vec = ints;
      }
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

  private static TypedExpr intColumn(int index) {
    return FieldAccessTypedExpr.create(new IntegerType(), "c" + index);
  }

  private static TypedExpr intConstant(int value) {
    return new ConstantTypedExpr(new IntegerType(), new IntegerValue(value), null);
  }

  private static TypedExpr bigintConstant(long value) {
    return new ConstantTypedExpr(new BigIntType(), new BigIntValue(value), null);
  }

  private static CallTypedExpr sha2(TypedExpr s, TypedExpr hashLength) {
    return new CallTypedExpr(new VarCharType(), List.of(s, hashLength), "sha2");
  }

  private static CallTypedExpr shaNamed(String name, TypedExpr s) {
    return new CallTypedExpr(new VarCharType(), List.of(s), name);
  }

  private static void assertVarcharValues(BaseVector result, String... expected) {
    try (BufferAllocator alloc = new RootAllocator();
        FieldVector fv = Arrow.toArrowVector(alloc, result)) {
      Assert.assertEquals(expected.length, fv.getValueCount());
      final VarCharVector varchars = (VarCharVector) fv;
      for (int i = 0; i < expected.length; i++) {
        if (expected[i] == null) {
          Assert.assertTrue("Row " + i + " should be null", fv.isNull(i));
        } else {
          Assert.assertFalse("Row " + i + " should be non-null", fv.isNull(i));
          Assert.assertEquals(
              "Row " + i + " mismatch",
              expected[i],
              new String(varchars.get(i), StandardCharsets.UTF_8));
        }
      }
    }
  }

  @Test
  public void testFixedLengthSpellingsPinnedValues() {
    final String[] inputs = {EMPTY, TEST, UTF8, LONG};
    try (RowVector input = makeRowVector(inputs);
        BaseVector r224 = evalExpr(input, shaNamed("sha224", varcharColumn(0)));
        BaseVector r256 = evalExpr(input, shaNamed("sha256", varcharColumn(0)));
        BaseVector r384 = evalExpr(input, shaNamed("sha384", varcharColumn(0)));
        BaseVector r512 = evalExpr(input, shaNamed("sha512", varcharColumn(0)))) {
      assertVarcharValues(r224, SHA224_EMPTY, SHA224_TEST, SHA224_UTF8, SHA224_LONG);
      assertVarcharValues(r256, SHA256_EMPTY, SHA256_TEST, SHA256_UTF8, SHA256_LONG);
      assertVarcharValues(r384, SHA384_EMPTY, SHA384_TEST, SHA384_UTF8, SHA384_LONG);
      assertVarcharValues(r512, SHA512_EMPTY, SHA512_TEST, SHA512_UTF8, SHA512_LONG);
    }
  }

  @Test
  public void testSha2ColumnLengthPinnedValues() {
    // One column of (input, hashLength) pairs per supported bit length.
    try (RowVector input =
            makeRowVector(
                new String[] {EMPTY, TEST, UTF8, LONG}, new Integer[] {224, 256, 384, 512});
        BaseVector result = evalExpr(input, sha2(varcharColumn(0), intColumn(1)))) {
      assertVarcharValues(result, SHA224_EMPTY, SHA256_TEST, SHA384_UTF8, SHA512_LONG);
    }
  }

  @Test
  public void testSha2ZeroSelects256() {
    try (RowVector input =
            makeRowVector(new String[] {EMPTY, TEST, UTF8}, new Integer[] {0, 0, 0});
        BaseVector result = evalExpr(input, sha2(varcharColumn(0), intColumn(1)))) {
      assertVarcharValues(result, SHA256_EMPTY, SHA256_TEST, SHA256_UTF8);
    }
  }

  @Test
  public void testSha2ConstantLengthIntAndBigInt() {
    // The planner folds literal lengths into constants; the BIGINT form
    // exercises the int64 signature of the velox function set.
    try (RowVector input = makeRowVector(new String[] {TEST, UTF8});
        BaseVector intConst = evalExpr(input, sha2(varcharColumn(0), intConstant(512)));
        BaseVector bigConst = evalExpr(input, sha2(varcharColumn(0), bigintConstant(256L)))) {
      assertVarcharValues(intConst, SHA512_TEST, SHA512_UTF8);
      assertVarcharValues(bigConst, SHA256_TEST, SHA256_UTF8);
    }
  }

  @Test
  public void testNullPropagation() {
    try (RowVector input = makeRowVector(new String[] {null, TEST});
        BaseVector r224 = evalExpr(input, shaNamed("sha224", varcharColumn(0)));
        BaseVector r256 = evalExpr(input, shaNamed("sha256", varcharColumn(0)));
        BaseVector r384 = evalExpr(input, shaNamed("sha384", varcharColumn(0)));
        BaseVector r512 = evalExpr(input, shaNamed("sha512", varcharColumn(0)))) {
      assertVarcharValues(r224, null, SHA224_TEST);
      assertVarcharValues(r256, null, SHA256_TEST);
      assertVarcharValues(r384, null, SHA384_TEST);
      assertVarcharValues(r512, null, SHA512_TEST);
    }
    try (RowVector input = makeRowVector(new String[] {TEST, TEST}, new Integer[] {256, null});
        BaseVector result = evalExpr(input, sha2(varcharColumn(0), intColumn(1)))) {
      assertVarcharValues(result, SHA256_TEST, null);
    }
  }

  @Test
  public void testSha2UnsupportedLengthThrowsUserError() {
    try (RowVector input = makeRowVector(new String[] {TEST}, new Integer[] {385});
        RowVector input2 = makeRowVector(new String[] {TEST})) {
      final VeloxException columnError =
          Assert.assertThrows(
              VeloxException.class, () -> evalExpr(input, sha2(varcharColumn(0), intColumn(1))));
      Assert.assertTrue(
          columnError.getMessage(),
          columnError.getMessage().contains("Unsupported SHA-2 hash length: 385"));
      final VeloxException constError =
          Assert.assertThrows(
              VeloxException.class,
              () -> evalExpr(input2, sha2(varcharColumn(0), intConstant(-1))));
      Assert.assertTrue(
          constError.getMessage(),
          constError.getMessage().contains("Unsupported SHA-2 hash length: -1"));
    }
  }
}
