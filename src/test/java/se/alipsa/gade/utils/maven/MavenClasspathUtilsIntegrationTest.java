package se.alipsa.gade.utils.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.tools.LoaderConfiguration;
import org.codehaus.groovy.tools.RootLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import se.alipsa.gade.console.ConsoleTextArea;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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

  @Test
  void candlesPomResolvesPoiAndSingleSlf4jApiVersion() throws Exception {
    Path projectDir = Path.of("examples", "candles");
    assumeTrue(Files.exists(projectDir.resolve("pom.xml")), "examples/candles/pom.xml must exist");

    ConsoleTextArea console = Mockito.mock(ConsoleTextArea.class);
    ClassLoader emptyParent = new RootLoader(new LoaderConfiguration());

    try (GroovyClassLoader loader = new GroovyClassLoader(emptyParent)) {
      MavenClasspathUtils.addPomDependenciesTo(loader, projectDir.toFile(), false, console);

      assertNotNull(loader.loadClass("org.apache.poi.ss.usermodel.CellStyle"),
          "Expected tablesaw-excel transitive Apache POI dependency to be available");

      Set<String> slf4jApiVersions = List.of(loader.getURLs()).stream()
          .map(url -> new File(url.getPath()).getName())
          .filter(name -> name.toLowerCase(Locale.ROOT).startsWith("slf4j-api-") && name.endsWith(".jar"))
          .map(name -> name.substring("slf4j-api-".length(), name.length() - ".jar".length()))
          .collect(Collectors.toSet());

      assertEquals(1, slf4jApiVersions.size(),
          "Expected exactly one effective slf4j-api version, found: " + slf4jApiVersions.stream()
              .sorted(Comparator.naturalOrder())
              .toList());

      String selectedSlf4jVersion = slf4jApiVersions.iterator().next();
      assertTrue(selectedSlf4jVersion.startsWith("2."),
          "Expected slf4j-api 2.x to match log4j-slf4j2-impl, but got: " + selectedSlf4jVersion);
    }
  }
}
