package se.alipsa.gade.utils.gradle;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.tools.LoaderConfiguration;
import org.codehaus.groovy.tools.RootLoader;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.utils.FileUtils;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class GradleIntegrationTest {

  @Test
  void testContextAddsTestDependencies() throws Exception {
    File gradleProjectDir = FileUtils.getResource("utils/gradle/package");
    GradleUtils utils = new GradleUtils(null, gradleProjectDir, System.getProperty("java.home"));
    ConsoleTextArea console = Mockito.mock(ConsoleTextArea.class);

    ClassLoader emptyParent = new RootLoader(new LoaderConfiguration());
    try (GroovyClassLoader mainLoader = new GroovyClassLoader(emptyParent)) {
      utils.addGradleDependencies(mainLoader, console, false);
      assertNotNull(mainLoader.loadClass("org.apache.commons.lang3.StringUtils"),
          "Expected compile dependency to be available in main classpath");
    }

    try (GroovyClassLoader testLoader = new GroovyClassLoader(emptyParent)) {
      utils.addGradleDependencies(testLoader, console, true);
      assertNotNull(testLoader.loadClass("org.junit.Test"),
          "Expected test dependency to be available when testContext=true");
    }
  }

  @Test
  void candlesProjectDoesNotExposeMultipleLog4jOrSlf4jApiVersions() {
    File candlesProjectDir = new File("examples/candles");
    assumeTrue(candlesProjectDir.exists(), "examples/candles must exist");

    GradleUtils utils = new GradleUtils(null, candlesProjectDir, System.getProperty("java.home"));
    List<File> deps = utils.getProjectDependencies();

    assertSingleVersion(deps, "log4j-api");
    assertSingleVersion(deps, "slf4j-api");
  }

  private static void assertSingleVersion(List<File> deps, String moduleName) {
    String prefix = moduleName + "-";
    Set<String> versions = new LinkedHashSet<>();
    for (File dep : deps) {
      String name = dep.getName();
      if (!name.startsWith(prefix) || !name.endsWith(".jar")) {
        continue;
      }
      versions.add(name.substring(prefix.length(), name.length() - 4));
    }
    assumeTrue(!versions.isEmpty(), "Expected " + moduleName + " to be present in classpath");
    assertEquals(1, versions.size(), "Expected a single resolved version for " + moduleName + ", found " + versions);
  }
}
