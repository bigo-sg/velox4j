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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PulsarConnectorSplit extends ConnectorSplit {
  private final String serviceUrl;
  private final String topic;
  private final String subscriptionName;
  private final String format;

  @JsonCreator
  public PulsarConnectorSplit(
      @JsonProperty("connectorId") String connectorId,
      @JsonProperty("serviceUrl") String serviceUrl,
      @JsonProperty("topic") String topic,
      @JsonProperty("subscriptionName") String subscriptionName,
      @JsonProperty("format") String format) {
    super(connectorId, 0, false);
    this.serviceUrl = serviceUrl;
    this.topic = topic;
    this.subscriptionName = subscriptionName;
    this.format = format;
  }

  @JsonGetter("serviceUrl")
  public String getServiceUrl() {
    return serviceUrl;
  }

  @JsonGetter("topic")
  public String getTopic() {
    return topic;
  }

  @JsonGetter("subscriptionName")
  public String getSubscriptionName() {
    return subscriptionName;
  }

  @JsonGetter("format")
  public String getFormat() {
    return format;
  }

  @Override
  @JsonIgnore
  public long getSplitWeight() {
    return super.getSplitWeight();
  }

  @Override
  @JsonIgnore
  public boolean isCacheable() {
    return super.isCacheable();
  }
}
