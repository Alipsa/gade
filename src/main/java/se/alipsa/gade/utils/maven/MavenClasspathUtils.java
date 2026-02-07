package se.alipsa.gade.utils.maven;

import groovy.lang.GroovyClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.runtime.MavenResolver;
import se.alipsa.gade.utils.ClasspathCacheManager;
import se.alipsa.mavenutils.DependenciesResolveException;
import se.alipsa.mavenutils.MavenUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MavenClasspathUtils {

  private static final Logger log = LogManager.getLogger(MavenClasspathUtils.class);

  private MavenClasspathUtils() {}

  public static void addPomDependenciesTo(GroovyClassLoader loader, File projectDir, boolean testContext, ConsoleTextArea console) {
    addPomDependenciesTo(loader, projectDir, testContext, console, null);
  }

  public static void addPomDependenciesTo(
      GroovyClassLoader loader, File projectDir, boolean testContext,
      ConsoleTextArea console, String mavenHome) {
    if (projectDir == null) {
      return;
    }
    File pom = new File(projectDir, "pom.xml");
    if (!pom.exists()) {
      return;
    }
    long start = System.currentTimeMillis();
    try {
      List<File> deps = resolveDependencies(projectDir, pom, testContext, console, mavenHome);
      for (File f : deps) {
        if (!f.exists()) {
          console.appendWarningFx("Dependency file does not exist: " + f);
          continue;
        }
        loader.addURL(f.toURI().toURL());
      }
      long ms = System.currentTimeMillis() - start;
      log.info("  Maven classpath resolved in {}s", ms / 1000.0);
    } catch (Exception e) {
      log.warn("Failed to resolve Maven dependencies for {}", pom, e);
      console.appendWarningFx("Failed to resolve Maven dependencies: " + e.getMessage());
      tryFallback(loader, pom, testContext, console);
    }
  }

  private static void tryFallback(GroovyClassLoader loader, File pom, boolean testContext, ConsoleTextArea console) {
    try {
      if (testContext) {
        console.appendWarningFx("Falling back to minimal Maven dependency resolver (test scope not fully supported).");
      } else {
        console.appendWarningFx("Falling back to minimal Maven dependency resolver.");
      }
      MavenResolver.addPomDependenciesTo(loader, pom);
    } catch (Exception ex) {
      log.warn("Fallback Maven dependency resolver failed for {}", pom, ex);
      console.appendWarningFx("Fallback Maven dependency resolver failed: " + ex.getMessage());
    }
  }

  private static List<File> resolveDependencies(
      File projectDir, File pom, boolean testContext, ConsoleTextArea console, String mavenHome)
      throws IOException, DependenciesResolveException {
    String fingerprint = fingerprintProject(projectDir, testContext, mavenHome);
    File cacheFile = cacheFile(projectDir, testContext);
    List<File> cached = load(cacheFile, fingerprint);
    if (cached != null) {
      console.appendFx("  Using cached Maven classpath", true);
      return cached;
    }
    MavenUtils maven = new MavenUtils();
    Set<File> resolved;
    try {
      MavenUtils.MavenExecutionOptions options = createExecutionOptions(projectDir, mavenHome);
      MavenUtils.DependenciesResolutionResult resolution = maven.resolveDependenciesWithSelection(pom, options, testContext);
      resolved = resolution.getDependencies();
      log.debug("Resolved Maven dependencies via distribution mode {} for {}",
          resolution.getDistributionSelection().getMode(), projectDir);
    } catch (Exception e) {
      if (e instanceof DependenciesResolveException dre) {
        throw dre;
      }
      throw new DependenciesResolveException("Failed to resolve dependencies from " + pom, e);
    }
    List<File> deps = resolved.stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(File::getAbsolutePath))
        .toList();
    store(cacheFile, fingerprint, deps);
    return deps;
  }

  private static List<File> load(File cacheFile, String expectedFingerprint) {
    ClasspathCacheManager.CachedClasspath cached = ClasspathCacheManager.load(cacheFile, expectedFingerprint, false);
    if (cached == null) {
      return null;
    }
    return cached.dependencies();
  }

  private static void store(File cacheFile, String fingerprint, List<File> deps) {
    ClasspathCacheManager.store(cacheFile, fingerprint, deps, "Gade Maven classpath cache");
  }

  private static File cacheFile(File projectDir, boolean testContext) {
    String projectKey = MavenBuildUtils.projectKey(projectDir);
    File dir = new File(ClasspathCacheManager.getCacheDir(), "maven-classpath/" + projectKey);
    return new File(dir, testContext ? "classpath-test.properties" : "classpath-main.properties");
  }

  private static String fingerprintProject(File projectDir, boolean testContext, String mavenHome) throws IOException {
    List<Path> tracked = new ArrayList<>();
    tracked.add(projectDir.toPath().resolve("pom.xml"));
    tracked.add(projectDir.toPath().resolve(".mvn").resolve("maven.config"));
    tracked.add(projectDir.toPath().resolve(".mvn").resolve("extensions.xml"));
    tracked.add(projectDir.toPath().resolve(".mvn").resolve("wrapper").resolve("maven-wrapper.properties"));

    long dotMvnLatest = ClasspathCacheManager.latestModified(projectDir.toPath().resolve(".mvn"));
    Map<String, String> extra = new LinkedHashMap<>();
    extra.put("dotMvnLatest", String.valueOf(dotMvnLatest));
    File configuredMavenHome = resolveConfiguredMavenHome(mavenHome);
    extra.put("mavenHome", configuredMavenHome == null ? "" : configuredMavenHome.getAbsolutePath());
    extra.put("embeddedMavenProp", System.getProperty(MavenDistributionLocator.EMBEDDED_MAVEN_HOME_PROP, ""));
    return ClasspathCacheManager.computeFingerprint(projectDir, testContext, tracked, extra);
  }

  static MavenUtils.MavenExecutionOptions createExecutionOptions(File projectDir, String mavenHome) {
    return new MavenUtils.MavenExecutionOptions(projectDir, resolveConfiguredMavenHome(mavenHome), true);
  }

  private static File resolveConfiguredMavenHome(String mavenHome) {
    if (mavenHome != null && !mavenHome.isBlank()) {
      return new File(mavenHome.trim());
    }
    return MavenDistributionLocator.resolveBundledMavenHome();
  }
}
