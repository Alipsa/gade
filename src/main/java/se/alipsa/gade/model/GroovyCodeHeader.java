package se.alipsa.gade.model;

import java.util.List;

public class GroovyCodeHeader {

  private final List<String> grabs;
  private final List<String> deps;
  private final List<String> imports;

  public GroovyCodeHeader(List<String> grabs, List<String> deps, List<String> imports) {
    this.grabs = grabs;
    this.deps = deps;
    this.imports = imports;
  }

  public String grabs() {
    return String.join("\n", grabs);
  }

  public String deps() {
    return String.join("\n", deps);
  }

  public String imports() {
    return String.join("\n", imports);
  }
}
