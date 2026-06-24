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
package io.github.zhztheplayer.velox4j.connector;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NexmarkGeneratorConfig implements Serializable {
  private final NexmarkConfiguration configuration;
  private final long baseTime;
  private final long firstEventId;
  private final long maxEvents;
  private final long firstEventNumber;

  @JsonCreator
  public NexmarkGeneratorConfig(
      @JsonProperty("configuration") NexmarkConfiguration configuration,
      @JsonProperty("baseTime") long baseTime,
      @JsonProperty("firstEventId") long firstEventId,
      @JsonProperty("maxEvents") long maxEvents,
      @JsonProperty("firstEventNumber") long firstEventNumber) {
    this.configuration = configuration;
    this.baseTime = baseTime;
    this.firstEventId = firstEventId;
    this.maxEvents = maxEvents;
    this.firstEventNumber = firstEventNumber;
  }

  @JsonGetter
  public NexmarkConfiguration getConfiguration() {
    return configuration;
  }

  @JsonGetter
  public long getBaseTime() {
    return baseTime;
  }

  @JsonGetter
  public long getFirstEventId() {
    return firstEventId;
  }

  @JsonGetter("maxEvents")
  public long getMaxEventsOrZero() {
    return maxEvents;
  }

  @JsonGetter
  public long getFirstEventNumber() {
    return firstEventNumber;
  }
}
