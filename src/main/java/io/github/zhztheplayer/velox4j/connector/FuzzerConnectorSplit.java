package io.github.zhztheplayer.velox4j.connector;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

// TODO: Add a builder for this class.
public class FuzzerConnectorSplit extends ConnectorSplit {
  private final int numRows;

  @JsonCreator
  public FuzzerConnectorSplit(
      @JsonProperty("connectorId") String connectorId,
      @JsonProperty("start") int numRows) {
    super(connectorId, 0, true);
    this.numRows = numRows;
  }

  @JsonGetter("splitWeight")
  public long getSplitWeight() {
    return super.getSplitWeight();
  }

  @JsonGetter("cacheable")
  public boolean isCacheable() {
    return super.isCacheable();
  }

  @JsonGetter("numRows")
  public int getNumRows() {
    return numRows;
  }

}
