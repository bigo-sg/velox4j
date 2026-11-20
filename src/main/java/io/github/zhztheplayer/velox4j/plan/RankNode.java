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

/**
 * Java mirror of the velox RankNode plan node: the typed rank operator over a constant rank range.
 * The sort specification is split into the key channels plus per-channel ascending / nullsFirst
 * flags, matching the C++ serialize keys one for one.
 */
public class RankNode extends PlanNode {
  private final Long rankStart;
  private final Long rankEnd;
  private final List<Integer> sortKeyChannels;
  private final List<Boolean> sortAscending;
  private final List<Boolean> sortNullsFirst;
  private final KeyedStateSpec keyedStateSpec;
  private final Boolean outputRankNumber;
  private final Boolean generateUpdateBefore;
  private final Boolean firstN;
  private final Long cacheSize;
  private final RowType inputType;
  private final RowType outputType;

  @JsonCreator
  public RankNode(
      @JsonProperty("id") String id,
      @JsonProperty("rankStart") Long rankStart,
      @JsonProperty("rankEnd") Long rankEnd,
      @JsonProperty("sortKeyChannels") List<Integer> sortKeyChannels,
      @JsonProperty("sortAscending") List<Boolean> sortAscending,
      @JsonProperty("sortNullsFirst") List<Boolean> sortNullsFirst,
      @JsonProperty("keyedStateSpec") KeyedStateSpec keyedStateSpec,
      @JsonProperty("outputRankNumber") Boolean outputRankNumber,
      @JsonProperty("generateUpdateBefore") Boolean generateUpdateBefore,
      @JsonProperty("firstN") Boolean firstN,
      @JsonProperty("cacheSize") Long cacheSize,
      @JsonProperty("inputType") RowType inputType,
      @JsonProperty("outputType") RowType outputType) {
    super(id);
    this.rankStart = rankStart;
    this.rankEnd = rankEnd;
    this.sortKeyChannels = sortKeyChannels;
    this.sortAscending = sortAscending;
    this.sortNullsFirst = sortNullsFirst;
    this.keyedStateSpec = keyedStateSpec;
    this.outputRankNumber = outputRankNumber;
    this.generateUpdateBefore = generateUpdateBefore;
    // Tolerant default: plans serialized before the field existed carry no
    // "firstN" key (null here) and behave as the non-FirstN shapes.
    this.firstN = firstN == null ? Boolean.FALSE : firstN;
    // Same tolerant-default shape for the TopN cache bound: the native
    // default of table.exec.rank.topn-cache-size is 10000
    // (ExecutionConfigOptions.java:501-505).
    this.cacheSize = cacheSize == null ? Long.valueOf(10000L) : cacheSize;
    this.inputType = inputType;
    this.outputType = outputType;
  }

  @Override
  protected List<PlanNode> getSources() {
    return List.of();
  }

  @JsonGetter("rankStart")
  public Long getRankStart() {
    return rankStart;
  }

  @JsonGetter("rankEnd")
  public Long getRankEnd() {
    return rankEnd;
  }

  @JsonGetter("sortKeyChannels")
  public List<Integer> getSortKeyChannels() {
    return sortKeyChannels;
  }

  @JsonGetter("sortAscending")
  public List<Boolean> getSortAscending() {
    return sortAscending;
  }

  @JsonGetter("sortNullsFirst")
  public List<Boolean> getSortNullsFirst() {
    return sortNullsFirst;
  }

  @JsonGetter("keyedStateSpec")
  public KeyedStateSpec getKeyedStateSpec() {
    return keyedStateSpec;
  }

  @JsonGetter("outputRankNumber")
  public Boolean getOutputRankNumber() {
    return outputRankNumber;
  }

  @JsonGetter("generateUpdateBefore")
  public Boolean getGenerateUpdateBefore() {
    return generateUpdateBefore;
  }

  @JsonGetter("firstN")
  public Boolean getFirstN() {
    return firstN;
  }

  @JsonGetter("cacheSize")
  public Long getCacheSize() {
    return cacheSize;
  }

  @JsonGetter("inputType")
  public RowType getInputType() {
    return inputType;
  }

  @JsonGetter("outputType")
  public RowType getOutputType() {
    return outputType;
  }
}
