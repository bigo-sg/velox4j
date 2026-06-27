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
import java.util.Objects;

public class NexmarkParallelSplit extends ParallelSplit {
  private final List<NexmarkConnectorSplit> subtaskSplits;

  public NexmarkParallelSplit(String connectorId, List<NexmarkConnectorSplit> subtaskSplits) {
    super(connectorId, 0, true);
    this.subtaskSplits = subtaskSplits;
  }

  @Override
  public ConnectorSplit getSubtaskSplit(int index, int parallelism) {
    Objects.requireNonNull(subtaskSplits, "subtaskSplits is null");
    if (parallelism != subtaskSplits.size()) {
      throw new IllegalStateException(
          String.format(
              "Runtime parallelism (%d) does not match planned subtask count (%d). "
                  + "Nexmark multi-parallelism requires the same parallelism at plan and runtime.",
              parallelism, subtaskSplits.size()));
    }
    if (index < 0 || index >= subtaskSplits.size()) {
      throw new IndexOutOfBoundsException(
          "Subtask index " + index + " out of range [0, " + subtaskSplits.size() + ")");
    }
    return subtaskSplits.get(index);
  }
}
