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
package io.github.zhztheplayer.velox4j.stateful;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.zhztheplayer.velox4j.serializable.ISerializable;
import io.github.zhztheplayer.velox4j.type.Type;

/**
 * The keyed-state configuration of a stateful operator as declared by the plan: which input
 * channels make up the user key, and the key column types. Pure serializable plan data; the keyed
 * state itself is built from this spec in the operator's initializeStateBackend.
 */
public class KeyedStateSpec extends ISerializable {
  private final List<Integer> keyChannels;
  private final List<Type> keyTypes;

  @JsonCreator
  public KeyedStateSpec(
      @JsonProperty("keyChannels") List<Integer> keyChannels,
      @JsonProperty("keyTypes") List<Type> keyTypes) {
    this.keyChannels = keyChannels;
    this.keyTypes = keyTypes;
  }

  @JsonGetter("keyChannels")
  public List<Integer> getKeyChannels() {
    return keyChannels;
  }

  @JsonGetter("keyTypes")
  public List<Type> getKeyTypes() {
    return keyTypes;
  }
}
