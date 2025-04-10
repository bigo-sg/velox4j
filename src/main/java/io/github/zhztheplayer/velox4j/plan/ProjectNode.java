package io.github.zhztheplayer.velox4j.plan;

import com.google.common.base.Preconditions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.zhztheplayer.velox4j.expression.TypedExpr;

import java.util.List;

public class ProjectNode extends PlanNode {
  private List<PlanNode> sources;
  private final List<String> names;
  private final List<TypedExpr> projections;

  @JsonCreator
  public ProjectNode(
      @JsonProperty("id") String id,
      @JsonProperty("sources") List<PlanNode> sources,
      @JsonProperty("names") List<String> names,
      @JsonProperty("projections") List<TypedExpr> projections) {
    super(id);
    this.sources = sources;
    this.names = names;
    this.projections = projections;
  }

  @Override
  protected List<PlanNode> getSources() {
    return sources;
  }

  @JsonGetter("names")
  public List<String> getNames() {
    return names;
  }

  @JsonGetter("projections")
  public List<TypedExpr> getProjections() {
    return projections;
  }

  @Override
  public void setSources(List<PlanNode> sources) {
    if (this.sources != null && !this.sources.isEmpty()) {
      this.sources.forEach(planNode -> planNode.setSources(sources));
    } else {
      Preconditions.checkArgument(sources.size() == 1, "Project only accept one source");
      this.sources = sources;
    }
  }
}
