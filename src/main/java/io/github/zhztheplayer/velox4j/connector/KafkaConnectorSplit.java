package io.github.zhztheplayer.velox4j.connector;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class KafkaConnectorSplit extends ConnectorSplit {
  private String bootstrapServers;
  
  private String groupId;
  
  private String format;
  
  private boolean enableAutoCommit;
  
  private String autoResetoffset;
  
  private Map<String, List<Integer>> topicPartitions;
  
  public KafkaConnectorSplit(
    @JsonProperty("connectorId") String connectorId,
    @JsonProperty("splitWeight") long splitWeight,
    @JsonProperty("cacheable") boolean cacheable,
    @JsonProperty("bootstrapServers") String bootstrapServers,
    @JsonProperty("groupId") String groupId,
    @JsonProperty("format") String format,
    @JsonProperty("enableAutoCommit") boolean enableAutoCommit,
    @JsonProperty("autoResetOffset") String autoResetOffset,
    @JsonProperty("topicPartitions") Map<String, List<Integer>> topicPartitions) {
    super(connectorId, splitWeight, cacheable);
    this.bootstrapServers = bootstrapServers;
    this.groupId = groupId;
    this.format = format;
    this.enableAutoCommit = enableAutoCommit;
    this.autoResetoffset = autoResetOffset;
    this.topicPartitions = topicPartitions;
  }
  
  @JsonGetter("bootstrapServers")
  public String getBootstrapServers() {
    return this.bootstrapServers;
  }
  
  @JsonGetter("groupId")
  public String getGroupId() {
    return this.groupId;
  }
  
  @JsonGetter("format")
  public String getFormat() {
    return this.format;
  }
  
  @JsonGetter("enableAutoCommit")
  public boolean getEnableAutoCommit() {
    return this.enableAutoCommit;
  }
  
  @JsonGetter("autoResetOffset")
  public String getAutoResetOffset() {
    return this.autoResetoffset;
  }
  
  @JsonGetter("topicPartitions")
  public Map<String, List<Integer>> getTopicPartitions() {
    return this.topicPartitions;
  }
}
