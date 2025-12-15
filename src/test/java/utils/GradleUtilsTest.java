package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.groovy.tools.LoaderConfiguration;
import org.codehaus.groovy.tools.RootLoader;
import org.junit.jupiter.api.Test;
import groovy.lang.GroovyClassLoader;
import se.alipsa.groovy.resolver.Dependency;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.gade.utils.gradle.GradleUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.*;

public class GradleUtilsTest {

  private static final Logger log = LogManager.getLogger(GradleUtilsTest.class);

  @Test
  public void testDependencies() throws Exception {
    var gradleProjectDir = FileUtils.getResource("utils/gradle/package");

    var gradleUtil = new GradleUtils(null, gradleProjectDir, System.getProperty("java.home"));

    //System.out.println("Task names:" + gradleUtil.getGradleTaskNames());

    var depNames = gradleUtil.getProjectDependencyNames();
    assertTrue(depNames.contains("commons-math3-3.6.1.jar"), "Failed to find dependency commons-math3-3.6.1.jar, was: " + depNames);
    assertTrue(depNames.contains("guava-31.0.1-jre.jar"), "Failed to find dependency guava-31.0.1-jre.jar, was: " + depNames);

    var dependencies = gradleUtil.getProjectDependencies();
    //dependencies.forEach(d -> System.out.println(d.getAbsolutePath()));
    assertNotNull(dependencies.stream().filter(f -> f.getName().equals("commons-math3-3.6.1.jar")).findAny().orElse(null), "Failed to find dependency commons-math3-3.6.1.jar");
    assertNotNull(dependencies.stream().filter(f -> f.getName().equals("guava-31.0.1-jre.jar")).findAny().orElse(null), "Failed to find dependency guava-31.0.1-jre.jar");
  }

  @Test
  public void testDownloadArtifact() throws IOException, URISyntaxException {
    Dependency dependency = new Dependency("org.slf4j:slf4j-api:1.7.36");
    File artifactDir = GradleUtils.cachedFile(dependency);
    if (artifactDir.exists()) {
      log.info("Deleting files in {} to ensure remote download works", artifactDir.getAbsolutePath());
      GradleUtils.purgeCache(dependency);
    } else {
      log.info("{} does not exist: no problem, we are going to fetch it!", artifactDir.getAbsolutePath());
    }
    File file = GradleUtils.downloadArtifact(dependency);
    assertTrue(file.exists(), "File does not exist");
    assertEquals("slf4j-api-1.7.36.jar", file.getName(), "File name is wrong");
  }

  @Test
  public void testGradleRuntimeLoadsAddedDependency() throws Exception {
    File gradleProjectDir = FileUtils.getResource("utils/gradle/package");
    GradleUtils gradleUtil = new GradleUtils(null, gradleProjectDir, System.getProperty("java.home"));

    var dependencies = gradleUtil.getProjectDependencies();
    assertTrue(
        dependencies.stream().anyMatch(f -> f.getName().startsWith("commons-lang3-")),
        "Gradle dependencies should contain commons-lang3"
    );

    // The default constructor for GroovyClassLoader uses context classloader as the parent.
    // We want to sure that the classpath is not tainted byt the test runner so we create an empty parent classloader.
    ClassLoader emptyParent = new RootLoader(new LoaderConfiguration());
    try (GroovyClassLoader loader = new GroovyClassLoader(emptyParent)) {
      for (File dep : dependencies) {
        loader.addURL(dep.toURI().toURL());
      }
      Class<?> stringUtils = loader.loadClass("org.apache.commons.lang3.StringUtils");
      boolean blank = (boolean) stringUtils.getMethod("isBlank", CharSequence.class).invoke(null, "   ");
      assertTrue(blank, "Expected StringUtils from commons-lang3 to be available via Gradle runtime dependencies");
    }
  }
}
