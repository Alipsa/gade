package se.alipsa.gade.model.centralsearch;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Params {
  public String q;
  @JsonProperty("hl.snippets")
  public String hlSnippets;
  public String core;
  public String hl;
  public String indent;
  public String fl;
  public String start;
  public String sort;
  @JsonProperty("hl.fl")
  public String hlFl;
  public String rows;
  public String wt;
  public String version;
}
