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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.zhztheplayer.velox4j.aggregate.Aggregate;
import io.github.zhztheplayer.velox4j.type.RowType;

public class GroupWindowAggsHandlerNode extends PlanNode {
  private final RowType outputType;
  private final List<Aggregate> aggregates;
  private final int countStarIndex;

  @JsonCreator
  public GroupWindowAggsHandlerNode(
      @JsonProperty("id") String id,
      @JsonProperty("outputType") RowType outputType,
      @JsonProperty("aggregates") List<Aggregate> aggregates,
      @JsonProperty("countStarIndex") int countStarIndex) {
    super(id);
    if (aggregates == null) {
      throw new IllegalArgumentException("GroupWindowAggsHandlerNode requires aggregates");
    }
    if (countStarIndex < -1 || countStarIndex >= aggregates.size()) {
      throw new IllegalArgumentException(
          "countStarIndex must be -1 or an aggregate index, got " + countStarIndex);
    }
    this.outputType = outputType;
    this.aggregates = aggregates;
    this.countStarIndex = countStarIndex;
  }

  @JsonGetter("outputType")
  public RowType getOutputType() {
    return outputType;
  }

  @JsonGetter("aggregates")
  public List<Aggregate> getAggregates() {
    return aggregates;
  }

  /**
   * Index of the COUNT(*) aggregate among {@link #getAggregates()} that feeds the retraction record
   * counter; -1 when absent (insert-only input). P0's empty-window check is the state's
   * non-materializing existence read, so the field is reserved for the P1 retraction upgrade and is
   * not consumed yet.
   */
  @JsonGetter("countStarIndex")
  public int getCountStarIndex() {
    return countStarIndex;
  }

  @Override
  protected List<PlanNode> getSources() {
    return List.of();
  }
}
