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
package io.github.zhztheplayer.velox4j.variant;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.github.zhztheplayer.velox4j.serde.NativeBean;

/**
 * Java binding of Velox's variant API. A Variant can be serialized to JSON and deserialized from
 * JSON.
 *
 * <p>A null {@code value} is meaningful for variants: it carries the variant's type (a typed null),
 * and the velox-side serde reads an explicit {@code "value": null} as that typed null. The
 * class-level ALWAYS therefore overrides the mapper-wide NON_NULL inclusion so the {@code value}
 * member is always emitted, even for null-valued carriers (e.g. {@code new VarCharValue(null)} used
 * to pass typed-null constants in expressions).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public abstract class Variant implements NativeBean {
  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  @Override
  public abstract String toString();
}
