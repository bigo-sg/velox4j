package io.github.zhztheplayer.velox4j.connector;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FuzzerTableHandle extends ConnectorTableHandle {
  private final int fuzzerSeed;

  @JsonCreator
  public FuzzerTableHandle(
          @JsonProperty("connectorId") String connectorId,
          @JsonProperty("fuzzerSeed") int fuzzerSeed) {
    super(connectorId);
    this.fuzzerSeed = fuzzerSeed;
  }

  @JsonGetter("fuzzerSeed")
  public int getSeed() {
    return fuzzerSeed;
  }

}
