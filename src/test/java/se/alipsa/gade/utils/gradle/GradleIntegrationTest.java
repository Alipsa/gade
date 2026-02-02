package se.alipsa.gade.utils.gradle;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.tools.LoaderConfiguration;
import org.codehaus.groovy.tools.RootLoader;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.utils.FileUtils;

import java.io.File;

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
}
