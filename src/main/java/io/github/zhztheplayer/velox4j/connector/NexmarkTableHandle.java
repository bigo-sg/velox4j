package io.github.zhztheplayer.velox4j.connector;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NexmarkTableHandle extends ConnectorTableHandle {

  @JsonCreator
  public NexmarkTableHandle(
          @JsonProperty("connectorId") String connectorId) {
    super(connectorId);
  }

}
