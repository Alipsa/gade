package se.alipsa.grade.model;

public class Dependency {

  private String groupId;
  private String artifactId;
  private String version;

  public Dependency() {
  }

  public Dependency(String groupId, String artifactId, String version) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
  }

  public Dependency(String dependencyString) {
    var parts = dependencyString.split(":");
    if (parts.length != 3) {
      throw new IllegalArgumentException("Incorrect format for dependency for " + dependencyString);
    }
    this.groupId = parts[0];
    this.artifactId = parts[1];
    this.version = parts[2];
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  public String getGroupId() {
    return groupId;
  }

  public void setArtifactId(String artifactId) {
    this.artifactId = artifactId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getVersion() {
    return version;
  }

  @Override
  public String toString() {
    return groupId + ":" + artifactId + ":" + version;
  }
}
