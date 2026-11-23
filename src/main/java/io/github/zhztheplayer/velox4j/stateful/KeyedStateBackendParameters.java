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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.zhztheplayer.velox4j.serializable.ISerializable;

public class KeyedStateBackendParameters extends ISerializable {

  /** Lower bound of Flink's default max parallelism (C++ kDefaultMaxParallelism). */
  public static final int DEFAULT_MAX_PARALLELISM = 128;

  private String jobId;
  private String operatorId;
  private int stateBackendType;
  private int maxParallelism;
  private int startKeyGroup;
  private int numKeyGroups;

  public KeyedStateBackendParameters(String jobId, String operatorId, int stateBackendType) {
    this(jobId, operatorId, stateBackendType, DEFAULT_MAX_PARALLELISM, 0, DEFAULT_MAX_PARALLELISM);
  }

  @JsonCreator
  public KeyedStateBackendParameters(
      @JsonProperty("jobId") String jobId,
      @JsonProperty("operatorId") String operatorId,
      @JsonProperty("stateBackendType") int stateBackendType,
      @JsonProperty("maxParallelism") Integer maxParallelism,
      @JsonProperty("startKeyGroup") Integer startKeyGroup,
      @JsonProperty("numKeyGroups") Integer numKeyGroups) {
    this.jobId = jobId;
    this.operatorId = operatorId;
    this.stateBackendType = stateBackendType;
    // Tolerant defaults match the C++ create (StateBackend.h): JSON written
    // before these fields existed deserializes as the full default space.
    this.maxParallelism = maxParallelism == null ? DEFAULT_MAX_PARALLELISM : maxParallelism;
    this.startKeyGroup = startKeyGroup == null ? 0 : startKeyGroup;
    this.numKeyGroups = numKeyGroups == null ? DEFAULT_MAX_PARALLELISM : numKeyGroups;
  }

  @JsonGetter("jobId")
  public String getJobId() {
    return this.jobId;
  }

  @JsonGetter("operatorId")
  public String getOpeatorId() {
    return this.operatorId;
  }

  @JsonGetter("stateBackendType")
  public int getBackendType() {
    return stateBackendType;
  }

  @JsonGetter("maxParallelism")
  public int getMaxParallelism() {
    return maxParallelism;
  }

  @JsonGetter("startKeyGroup")
  public int getStartKeyGroup() {
    return startKeyGroup;
  }

  @JsonGetter("numKeyGroups")
  public int getNumKeyGroups() {
    return numKeyGroups;
  }
}
