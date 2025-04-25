package io.github.zhztheplayer.velox4j.connector;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NexmarkTableHandle extends ConnectorTableHandle {
    private final NexmarkConfiguration config;

    @JsonCreator
    public NexmarkTableHandle(
            @JsonProperty("connectorId") String connectorId,
            @JsonProperty("config") NexmarkConfiguration config) {
        super(connectorId);
        this.config = config;
    }

    @JsonProperty
    public NexmarkConfiguration getConfig() {
        return config;
    }
}
