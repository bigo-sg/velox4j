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
package io.github.zhztheplayer.velox4j.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TimestampType extends Type {
  private final int precision;
  private final boolean localZoned;

  public TimestampType() {
    this(6, false);
  }

  @JsonCreator
  public TimestampType(
      @JsonProperty("precision") Integer precision, @JsonProperty("localZoned") Boolean localZoned) {
    // Velox TIMESTAMP has no precision/localZoned; default when absent after C++ round trip.
    this.precision = precision == null ? 6 : precision;
    this.localZoned = localZoned != null && localZoned;
  }

  @JsonGetter("precision")
  public int getPrecision() {
    return precision;
  }

  @JsonGetter("localZoned")
  public boolean isLocalZoned() {
    return localZoned;
  }
}
