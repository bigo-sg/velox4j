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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimestampType extends Type {
  private final Integer precision;
  private final Boolean localZoned;

  public TimestampType() {
    this(null, null);
  }

  public TimestampType(Integer precision, Boolean localZoned) {
    this(precision, localZoned, null);
  }

  @JsonCreator
  public TimestampType(
      @JsonProperty("precision") Integer precision,
      @JsonProperty("localZoned") Boolean localZoned,
      @JsonProperty("type") String serdeTypeName) {
    this.precision = precision;
    this.localZoned =
        Boolean.TRUE.equals(localZoned) || "FLINK_TIMESTAMP_LTZ".equals(serdeTypeName)
            ? true
            : null;
  }

  @JsonIgnore
  public int getPrecision() {
    return precision == null ? 0 : precision;
  }

  @JsonGetter("precision")
  public Integer getSerializedPrecision() {
    return precision;
  }

  @JsonIgnore
  public boolean isLocalZoned() {
    return localZoned != null && localZoned;
  }

  @JsonIgnore
  public Boolean getSerializedLocalZoned() {
    return localZoned;
  }

  @JsonIgnore
  public boolean hasFlinkTimestampMetadata() {
    return precision != null || localZoned != null;
  }

  @JsonIgnore
  public String getSerdeTypeName() {
    if (!hasFlinkTimestampMetadata()) {
      return "TIMESTAMP";
    }
    return isLocalZoned() ? "FLINK_TIMESTAMP_LTZ" : "FLINK_TIMESTAMP";
  }
}
