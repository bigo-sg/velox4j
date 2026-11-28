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

import io.github.zhztheplayer.velox4j.stateful.KeyedStateSpec;
import io.github.zhztheplayer.velox4j.type.RowType;

public class GroupWindowAggregationNode extends PlanNode {
  private final PlanNode aggregation;
  private final KeyedStateSpec keyedStateSpec;
  private final RowType inputType;
  private final Long allowedLateness;
  private final boolean produceUpdates;
  private final Integer rowtimeIndex;
  private final boolean isEventTime;
  private final Integer windowType;
  private final Long gap;
  private final Long size;
  private final Long slide;
  private final Integer windowStartIndex;
  private final Integer windowEndIndex;
  private final RowType outputType;

  @JsonCreator
  public GroupWindowAggregationNode(
      @JsonProperty("id") String id,
      @JsonProperty("aggregation") PlanNode aggregation,
      @JsonProperty("keyedStateSpec") KeyedStateSpec keyedStateSpec,
      @JsonProperty("inputType") RowType inputType,
      @JsonProperty("allowedLateness") Long allowedLateness,
      @JsonProperty("produceUpdates") boolean produceUpdates,
      @JsonProperty("rowtimeIndex") Integer rowtimeIndex,
      @JsonProperty("isEventTime") boolean isEventTime,
      @JsonProperty("windowType") Integer windowType,
      @JsonProperty("gap") Long gap,
      @JsonProperty("size") Long size,
      @JsonProperty("slide") Long slide,
      @JsonProperty("windowStartIndex") Integer windowStartIndex,
      @JsonProperty("windowEndIndex") Integer windowEndIndex,
      @JsonProperty("outputType") RowType outputType) {
    super(id);
    if (keyedStateSpec == null) {
      throw new IllegalArgumentException("GroupWindowAggregationNode requires a KeyedStateSpec");
    }
    if (inputType == null) {
      throw new IllegalArgumentException("GroupWindowAggregationNode requires an inputType");
    }
    this.aggregation = aggregation;
    this.keyedStateSpec = keyedStateSpec;
    this.inputType = inputType;
    this.allowedLateness = allowedLateness;
    this.produceUpdates = produceUpdates;
    this.rowtimeIndex = rowtimeIndex;
    this.isEventTime = isEventTime;
    this.windowType = windowType;
    this.gap = gap;
    this.size = size;
    this.slide = slide;
    this.windowStartIndex = windowStartIndex;
    this.windowEndIndex = windowEndIndex;
    this.outputType = outputType;
  }

  @JsonGetter("aggregation")
  public PlanNode getAggregation() {
    return aggregation;
  }

  @JsonGetter("keyedStateSpec")
  public KeyedStateSpec getKeyedStateSpec() {
    return keyedStateSpec;
  }

  @JsonGetter("inputType")
  public RowType getInputType() {
    return inputType;
  }

  @JsonGetter("allowedLateness")
  public Long getAllowedLateness() {
    return allowedLateness;
  }

  @JsonGetter("produceUpdates")
  public boolean produceUpdates() {
    return produceUpdates;
  }

  @JsonGetter("rowtimeIndex")
  public Integer getRowtimeIndex() {
    return rowtimeIndex;
  }

  @JsonGetter("isEventTime")
  public boolean isEventTime() {
    return isEventTime;
  }

  @JsonGetter("windowType")
  public Integer getWindowType() {
    return windowType;
  }

  @JsonGetter("gap")
  public Long getGap() {
    return gap;
  }

  @JsonGetter("size")
  public Long getSize() {
    return size;
  }

  @JsonGetter("slide")
  public Long getSlide() {
    return slide;
  }

  @JsonGetter("windowStartIndex")
  public Integer getWindowStartIndex() {
    return windowStartIndex;
  }

  @JsonGetter("windowEndIndex")
  public Integer getWindowEndIndex() {
    return windowEndIndex;
  }

  @JsonGetter("outputType")
  public RowType getOutputType() {
    return outputType;
  }

  @Override
  protected List<PlanNode> getSources() {
    return List.of();
  }
}
