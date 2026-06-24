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

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.zhztheplayer.velox4j.connector.Assignment;
import io.github.zhztheplayer.velox4j.connector.ConnectorTableHandle;
import io.github.zhztheplayer.velox4j.exception.VeloxException;
import io.github.zhztheplayer.velox4j.type.Type;

/**
 * Java binding of Velox {@code TableScanNodeWithWatermark}: a {@link TableScanNode}-like scan with
 * optional {@link WatermarkPushDownSpec} (serialized as {@code watermarkPushDownSpec}).
 */
public class TableScanWithWatermarkNode extends PlanNode {
  private final Type outputType;
  private final ConnectorTableHandle tableHandle;
  private final List<Assignment> assignments;
  private final WatermarkPushDownSpec watermarkPushDownSpec;

  @JsonCreator
  public TableScanWithWatermarkNode(
      @JsonProperty("id") String id,
      @JsonProperty("outputType") Type outputType,
      @JsonProperty("tableHandle") ConnectorTableHandle tableHandle,
      @JsonProperty("assignments") List<Assignment> assignments,
      @JsonProperty("watermarkPushDownSpec") WatermarkPushDownSpec watermarkPushDownSpec) {
    super(id);
    this.outputType = outputType;
    this.tableHandle = tableHandle;
    this.assignments = assignments;
    this.watermarkPushDownSpec = watermarkPushDownSpec;
  }

  @JsonGetter("outputType")
  public Type getOutputType() {
    return outputType;
  }

  @JsonGetter("tableHandle")
  public ConnectorTableHandle getTableHandle() {
    return tableHandle;
  }

  @JsonGetter("assignments")
  public List<Assignment> getAssignments() {
    return assignments;
  }

  @JsonGetter("watermarkPushDownSpec")
  public WatermarkPushDownSpec getWatermarkPushDownSpec() {
    return watermarkPushDownSpec;
  }

  @Override
  protected List<PlanNode> getSources() {
    return Collections.emptyList();
  }

  @Override
  public void setSources(List<PlanNode> sources) {
    throw new VeloxException("TableScanWithWatermark should not set sources");
  }
}
