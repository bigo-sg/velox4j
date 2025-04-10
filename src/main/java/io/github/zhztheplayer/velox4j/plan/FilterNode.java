package io.github.zhztheplayer.velox4j.plan;

import com.google.common.base.Preconditions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.zhztheplayer.velox4j.expression.TypedExpr;

import java.util.List;

public class FilterNode extends PlanNode {
  private List<PlanNode> sources;
  private final TypedExpr filter;

  @JsonCreator
  public FilterNode(
      @JsonProperty("id") String id,
      @JsonProperty("sources") List<PlanNode> sources,
      @JsonProperty("filter") TypedExpr filter) {
    super(id);
    this.sources = sources;
    this.filter = filter;
  }

  @Override
  protected List<PlanNode> getSources() {
    return sources;
  }

  @JsonGetter("filter")
  public TypedExpr getFilter() {
    return filter;
  }

  @Override
  public void setSources(List<PlanNode> sources) {
    if (this.sources != null && !this.sources.isEmpty()) {
      this.sources.forEach(planNode -> planNode.setSources(sources));
    } else {
      Preconditions.checkArgument(sources.size() == 1, "Filter only accept one source");
      this.sources = sources;
    }
  }
}
