package io.github.zhztheplayer.velox4j.connector;

import com.fasterxml.jackson.annotation.JsonCreator;

public class DiscardDataTableHandle extends ConnectorInsertTableHandle {

  @JsonCreator
  public DiscardDataTableHandle() {
    super();
  }

  @Override
  public boolean supportsMultiThreading() {
    return true;
  }
}
