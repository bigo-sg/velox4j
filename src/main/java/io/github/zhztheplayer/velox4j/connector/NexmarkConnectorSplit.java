package io.github.zhztheplayer.velox4j.connector;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

// TODO: Add a builder for this class.
public class NexmarkConnectorSplit extends ConnectorSplit {
  private final int numRows;

  @JsonCreator
  public NexmarkConnectorSplit(
      @JsonProperty("connectorId") String connectorId,
      @JsonProperty("numRows") int numRows) {
    super(connectorId, 0, true);
    this.numRows = numRows;
  }

  @JsonGetter("numRows")
  public int getNumRows() {
    return numRows;
  }

}
