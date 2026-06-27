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
package io.github.zhztheplayer.velox4j.plan;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.zhztheplayer.velox4j.serializable.ISerializable;

/**
 * Java binding of Velox {@code WatermarkPushDownSpec}: watermark metadata pushed into a table scan
 * (project for row-time expression, timeouts, and rowtime column index).
 */
public class WatermarkPushDownSpec extends ISerializable {
  private final ProjectNode project;
  private final long idleTimeout;
  private final long watermarkInterval;
  private final int rowtimeFieldIndex;

  @JsonCreator
  public WatermarkPushDownSpec(
      @JsonProperty("project") ProjectNode project,
      @JsonProperty("idleTimeout") long idleTimeout,
      @JsonProperty("watermarkInterval") long watermarkInterval,
      @JsonProperty("rowtimeFieldIndex") int rowtimeFieldIndex) {
    this.project = project;
    this.idleTimeout = idleTimeout;
    this.watermarkInterval = watermarkInterval;
    this.rowtimeFieldIndex = rowtimeFieldIndex;
  }

  @JsonGetter("project")
  public ProjectNode getProject() {
    return project;
  }

  @JsonGetter("idleTimeout")
  public long getIdleTimeout() {
    return idleTimeout;
  }

  @JsonGetter("watermarkInterval")
  public long getWatermarkInterval() {
    return watermarkInterval;
  }

  @JsonGetter("rowtimeFieldIndex")
  public int getRowtimeFieldIndex() {
    return rowtimeFieldIndex;
  }
}
