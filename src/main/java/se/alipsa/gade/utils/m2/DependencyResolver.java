package se.alipsa.gade.utils.m2;

import groovy.lang.GroovyClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import se.alipsa.gade.model.Dependency;
import se.alipsa.mavenutils.DependenciesResolveException;
import se.alipsa.mavenutils.MavenUtils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;


public class DependencyResolver {

  private static final Logger log = LogManager.getLogger();
  public static final URL CENTRAL_MAVEN_REPOSITORY;

  static {
    try {
      CENTRAL_MAVEN_REPOSITORY = new URL("https://repo1.maven.org/maven2/");
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
  private final Set<URL> remoteRepositories;
  private GroovyClassLoader classLoader = null;

  public DependencyResolver() {
    remoteRepositories = new LinkedHashSet<>();
    remoteRepositories.add(CENTRAL_MAVEN_REPOSITORY);
  }

  public DependencyResolver(GroovyClassLoader classLoader) {
    this();
    this.classLoader = classLoader;
  }

  public void addDependency(String groupId, String artifactId, String version) throws ResolvingException {
    Dependency dep = new Dependency(groupId, artifactId, version);
    addDependency(dep);
  }

  public void addDependency(String dependency) throws ResolvingException {
    Dependency dep = new Dependency(dependency);
    addDependency(dep);
  }

  private void addDependency(Dependency dep) throws ResolvingException {

    List<File> artifacts = resolve(dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
    if (classLoader == null) {
      log.error("You must add a GroovyClassloader before adding dependencies");
      throw new ResolvingException("You must add a GroovyClassloader before adding dependencies");
    }
    try {
      for (File artifact : artifacts) {
        classLoader.addURL(artifact.toURI().toURL());
      }
    } catch (MalformedURLException e) {
      log.warn("Failed to convert the downloaded file to a URL", e);
      throw new ResolvingException("Failed to convert the downloaded file to a URL", e);
    }
  }

  public List<File> resolve(String groupId, String artifactId, String version) throws ResolvingException {
    List<File> jarFiles = new ArrayList<>();
    resolve(new Dependency(groupId, artifactId, version), jarFiles);
    return jarFiles;
  }

  public List<File> resolve(String dependency) throws ResolvingException {
    List<File> jarFiles = new ArrayList<>();
    resolve(new Dependency(dependency), jarFiles);
    return jarFiles;
  }

  private void resolve(Dependency dependency, List<File> jarFiles) throws ResolvingException {
    MavenUtils mavenUtils = new MavenUtils();
    File artifact;
    try {
      artifact = mavenUtils.resolveArtifact(
          dependency.getGroupId(),
          dependency.getArtifactId(),
          null,
          "jar",
          dependency.getVersion()
      );
    } catch (SettingsBuildingException | ArtifactResolutionException e) {
      log.warn("Failed to resolve artifact " + dependency);
      throw new ResolvingException("Failed to resolve artifact " + dependency);
    }
    jarFiles.add(artifact);
    File pomFile = new File(artifact.getParent(), artifact.getName().replace(".jar", ".pom"));
    try {
      for (File file : mavenUtils.resolveDependencies(pomFile)) {
        if (file.getName().toLowerCase().endsWith(".jar")) {
          jarFiles.add(file);
        }
      }
    } catch (SettingsBuildingException | ModelBuildingException | DependenciesResolveException e) {
      log.warn("Failed to resolve pom file " + pomFile);
      throw new ResolvingException("Failed to resolve pom file " + pomFile);
    }
  }
}
