package se.alipsa.gade.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RuntimeConfig {

  private final String name;
  private final RuntimeType type;
  private final String javaHome;
  private final String buildToolHome;
  private final String groovyHome;
  private final List<String> additionalJars;
  private final List<String> dependencies;

  public RuntimeConfig(String name, RuntimeType type) {
    this(name, type, null, null, null, Collections.emptyList(), Collections.emptyList());
  }

  public RuntimeConfig(String name, RuntimeType type, String javaHome, String groovyHome,
                       List<String> additionalJars, List<String> dependencies) {
    this(name, type, javaHome, null, groovyHome, additionalJars, dependencies);
  }

  @JsonCreator
  public RuntimeConfig(@JsonProperty("name") String name,
                       @JsonProperty("type") RuntimeType type,
                       @JsonProperty("javaHome") String javaHome,
                       @JsonProperty("buildToolHome") String buildToolHome,
                       @JsonProperty("groovyHome") String groovyHome,
                       @JsonProperty("additionalJars") List<String> additionalJars,
                       @JsonProperty("dependencies") List<String> dependencies) {
    this.name = name;
    this.type = type;
    this.javaHome = javaHome;
    this.buildToolHome = buildToolHome;
    this.groovyHome = groovyHome;
    this.additionalJars = additionalJars == null ? Collections.emptyList() : new ArrayList<>(additionalJars);
    this.dependencies = dependencies == null ? Collections.emptyList() : new ArrayList<>(dependencies);
  }

  public String getName() {
    return name;
  }

  public RuntimeType getType() {
    return type;
  }

  public String getJavaHome() {
    return javaHome;
  }

  public String getBuildToolHome() {
    return buildToolHome;
  }

  public String getGroovyHome() {
    return groovyHome;
  }

  public List<String> getAdditionalJars() {
    return Collections.unmodifiableList(additionalJars);
  }

  public List<String> getDependencies() {
    return Collections.unmodifiableList(dependencies);
  }

  @JsonIgnore
  public boolean isBuiltIn() {
    return type != RuntimeType.CUSTOM;
  }

  public RuntimeConfig withName(String newName) {
    return new RuntimeConfig(newName, type, javaHome, buildToolHome, groovyHome, additionalJars, dependencies);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RuntimeConfig that = (RuntimeConfig) o;
    return Objects.equals(name, that.name) && type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type);
  }

  @Override
  public String toString() {
    return name + " (" + type + ")";
  }
}
