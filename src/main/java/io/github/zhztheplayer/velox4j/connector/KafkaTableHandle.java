package io.github.zhztheplayer.velox4j.connector;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.zhztheplayer.velox4j.expression.TypedExpr;
import io.github.zhztheplayer.velox4j.type.RowType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KafkaTableHandle extends ConnectorTableHandle {
  private String tableName;
  
  private final boolean filterPushdownEnabled;
  
  private final List<SubfieldFilter> subfieldFilters;
  
  private final TypedExpr remainingFilter;
  
  private final RowType dataColumns;
  
  private final Map<String, String> tableParameters;
  
  public KafkaTableHandle(
    @JsonProperty("connectorId") String connectorId, 
    @JsonProperty("tableName") String tableName, 
    @JsonProperty("dataColumns") RowType dataColumns, 
    @JsonProperty("tableParameters") Map<String, String> tableParameters) {
    this(connectorId, tableName, false, new ArrayList<>(), null, dataColumns, tableParameters);
  }
  
  public KafkaTableHandle(
    @JsonProperty("connectorId") String connectorId,
    @JsonProperty("tableName") String tableName,
    @JsonProperty("filterPushdownEnabled") boolean filterPushdownEnabled,
    @JsonProperty("subfieldFilters") List<SubfieldFilter> subfieldFilters,
    @JsonProperty("remainingFilter") TypedExpr remainingFilter,
    @JsonProperty("dataColumns") RowType dataColumns,
    @JsonProperty("tableParameters") Map<String, String> tableParameters) {
    super(connectorId);
    this.tableName = tableName;
    this.filterPushdownEnabled = filterPushdownEnabled;
    this.subfieldFilters = subfieldFilters;
    this.remainingFilter = remainingFilter;
    this.dataColumns = dataColumns;
    this.tableParameters = tableParameters;
  }
  
  @JsonProperty("tableName")
  public String getTableName() {
    return this.tableName;
  }
  
  @JsonProperty("filterPushdownEnabled")
  public boolean getFilterPushdownEnabled() {
    return this.filterPushdownEnabled;
  }
  
  @JsonProperty("subfieldFilters")
  public List<SubfieldFilter> getSubfieldFilters() {
    return this.subfieldFilters;
  }
  
  @JsonProperty("remainingFilter")
  public TypedExpr getReaminingFilter() {
    return this.remainingFilter;
  }
  
  @JsonProperty("dataColumns")
  public RowType getDataColumns() {
    return this.dataColumns;
  }
  
  @JsonProperty("tableParameters")
  public Map<String, String> getTableParameters() {
    return this.tableParameters;
  }
}
