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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

// TODO: Add a builder for this class.
public class NexmarkConnectorSplit extends ParallelSplit {
  private final GeneratorConfig config;
  private final List<NexmarkConnectorSplit> subtaskSplits;

  @JsonCreator
  public NexmarkConnectorSplit(
      @JsonProperty("connectorId") String connectorId,
      @JsonProperty("config") GeneratorConfig config,
      @JsonProperty("subtaskSplits") List<NexmarkConnectorSplit> subtaskSplits) {
    super(connectorId, 0, true);
    this.config = config;
    this.subtaskSplits = subtaskSplits;
  }

  @JsonGetter("config")
  public GeneratorConfig getConfig() {
    return config;
  }

  @Override
  public ConnectorSplit getSubtaskSplit(int index, int parallelism) {
    return subtaskSplits.get(index);
  }
}
