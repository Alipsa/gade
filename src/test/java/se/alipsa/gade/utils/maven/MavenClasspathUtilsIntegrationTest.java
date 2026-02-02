package se.alipsa.gade.utils.maven;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.tools.LoaderConfiguration;
import org.codehaus.groovy.tools.RootLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import se.alipsa.gade.console.ConsoleTextArea;

import java.nio.file.Files;
import java.nio.file.Path;

class MavenClasspathUtilsIntegrationTest {

  @Test
  void testContextIncludesTestDependencies(@TempDir Path tempDir) throws Exception {
    Path pom = tempDir.resolve("pom.xml");
    Files.writeString(pom, """
        <project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
          xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">
          <modelVersion>4.0.0</modelVersion>
          <groupId>org.example</groupId>
          <artifactId>demo</artifactId>
          <version>1.0</version>
          <dependencies>
            <dependency>
              <groupId>org.apache.commons</groupId>
              <artifactId>commons-lang3</artifactId>
              <version>3.12.0</version>
            </dependency>
            <dependency>
              <groupId>org.junit.jupiter</groupId>
              <artifactId>junit-jupiter-api</artifactId>
              <version>5.10.2</version>
              <scope>test</scope>
            </dependency>
          </dependencies>
        </project>
        """
    );

    ConsoleTextArea console = Mockito.mock(ConsoleTextArea.class);
    ClassLoader emptyParent = new RootLoader(new LoaderConfiguration());

    try (GroovyClassLoader mainLoader = new GroovyClassLoader(emptyParent)) {
      MavenClasspathUtils.addPomDependenciesTo(mainLoader, tempDir.toFile(), false, console);
      assertNotNull(mainLoader.loadClass("org.apache.commons.lang3.StringUtils"),
          "Expected compile dependency to be available");
    }

    try (GroovyClassLoader testLoader = new GroovyClassLoader(emptyParent)) {
      MavenClasspathUtils.addPomDependenciesTo(testLoader, tempDir.toFile(), true, console);
      assertNotNull(testLoader.loadClass("org.junit.jupiter.api.Test"),
          "Expected test dependency to be available when testContext=true");
    }
  }
}
