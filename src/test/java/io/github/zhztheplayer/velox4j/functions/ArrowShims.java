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

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;

import io.github.zhztheplayer.velox4j.arrow.Arrow;
import io.github.zhztheplayer.velox4j.data.BaseVector;
import io.github.zhztheplayer.velox4j.data.RowVector;
import io.github.zhztheplayer.velox4j.session.Session;

/** Test helpers shared by the Flink-preset function tests. */
final class ArrowShims {

  private ArrowShims() {}

  /** A one-row row vector with a single null column, for constant-only evaluations. */
  static RowVector oneNullRow(Session session) {
    // The allocator intentionally stays open: the imported RowVector keeps
    // referencing its memory (same pattern as RegexpExtractFlinkTest).
    BufferAllocator alloc = new RootAllocator();
    VarCharVector vec = new VarCharVector("c0", alloc);
    vec.allocateNew();
    vec.setNull(0);
    vec.setValueCount(1);
    VectorSchemaRoot root = VectorSchemaRoot.of(vec);
    return session.arrowOps().fromArrowTable(alloc, new org.apache.arrow.vector.table.Table(root));
  }

  /** Reads a one-row TIMESTAMP result as {seconds, nanos}. */
  static long[] readTimestampMicros(BaseVector result) {
    try (BufferAllocator alloc = new RootAllocator();
        FieldVector fv = Arrow.toArrowVector(alloc, result)) {
      // velox TIMESTAMP maps to Arrow timestamp[us]; read raw micros.
      org.apache.arrow.vector.TimeStampMicroVector tv =
          (org.apache.arrow.vector.TimeStampMicroVector) fv;
      long micros = tv.get(0);
      long sec = Math.floorDiv(micros, 1_000_000L);
      long nanos = Math.floorMod(micros, 1_000_000L) * 1000L;
      return new long[] {sec, nanos};
    }
  }
}
