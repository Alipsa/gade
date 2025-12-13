package se.alipsa.gade.runtime;

import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import se.alipsa.groovy.resolver.DependencyResolver;
import se.alipsa.groovy.resolver.ResolvingException;
import groovy.lang.GroovyClassLoader;

/**
 * Minimal Maven pom resolver for runtime classpath creation.
 */
public final class MavenResolver {

  private static final Logger log = LogManager.getLogger(MavenResolver.class);

  private MavenResolver() {}

  public static void addPomDependenciesTo(GroovyClassLoader loader, File pomFile) throws Exception {
    MavenXpp3Reader reader = new MavenXpp3Reader();
    Model model;
    try (FileReader fileReader = new FileReader(pomFile)) {
      model = reader.read(fileReader);
    }
    Properties props = model.getProperties();
    List<String> coords = model.getDependencies().stream()
        .filter(dep -> !"test".equalsIgnoreCase(dep.getScope()))
        .map(dep -> toCoord(dep, props))
        .collect(Collectors.toList());
    DependencyResolver resolver = new DependencyResolver(loader);
    for (String coord : coords) {
      try {
        resolver.addDependency(coord);
      } catch (ResolvingException e) {
        log.warn("Failed to resolve {}", coord, e);
      }
    }
  }

  private static String toCoord(Dependency dep, Properties props) {
    String version = dep.getVersion();
    if (version != null && version.startsWith("${") && version.endsWith("}")) {
      String key = version.substring(2, version.length() - 1);
      version = props.getProperty(key, version);
    }
    return dep.getGroupId() + ":" + dep.getArtifactId() + ":" + version;
  }
}
