package io.github.zhztheplayer.velox4j.plan;

import com.google.common.base.Preconditions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class WatermarkAssignerNode extends PlanNode {
  private List<PlanNode> sources;
  private final ProjectNode project;
  private final long idleTimeout;
  private final int rowtimeFieldIndex;

  @JsonCreator
  public WatermarkAssignerNode(
          @JsonProperty("id") String id,
          @JsonProperty("sources") List<PlanNode> sources,
          @JsonProperty("project") ProjectNode project,
          @JsonProperty("idleTimeout") long idleTimeout,
          @JsonProperty("rowtimeFieldIndex") int rowtimeFieldIndex) {
    super(id);
    this.sources = sources;
    this.project = project;
    this.idleTimeout = idleTimeout;
    this.rowtimeFieldIndex = rowtimeFieldIndex;
  }

  @Override
  protected List<PlanNode> getSources() {
    return sources;
  }

  @JsonGetter("project")
  public ProjectNode getProject() {
    return project;
  }

  @JsonGetter("idleTimeout")
  public long getIdleTimeout() {
    return idleTimeout;
  }

  @JsonGetter("rowtimeFieldIndex")
  public int getRowtimeFieldIndex() {
    return rowtimeFieldIndex;
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
