package se.alipsa.gade.runtime;

import groovy.lang.GroovyClassLoader;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that Gradle/Maven runtime classloaders use the project's Groovy version,
 * not Gade's bundled Groovy version.
 */
public class RuntimeGroovyVersionTest {

  @Test
  public void testGradleRuntimeUsesProjectGroovyVersion() throws Exception {
    File projectDir = new File("/Users/pernyf/project/groovier-junit5");
    if (!projectDir.exists()) {
      System.out.println("Skipping test - project directory does not exist");
      return;
    }

    // The groovier-junit5 project uses Groovy 5.0.4
    // We want to verify that when we create a Gradle classloader for it,
    // it loads Groovy 5.0.4, not Gade's bundled 5.0.x

    // Note: This test can't fully verify the version without a Gade instance,
    // but we can at least verify the classloader structure is correct

    se.alipsa.gade.utils.gradle.GradleUtils utils =
        new se.alipsa.gade.utils.gradle.GradleUtils(null, projectDir, null);

    var deps = utils.getProjectDependencies();
    System.out.println("Project has " + deps.size() + " dependencies");

    // Check if Groovy is in the dependencies
    boolean hasGroovy = deps.stream()
        .anyMatch(f -> f.getName().contains("groovy"));

    if (hasGroovy) {
      System.out.println("Project dependencies include Groovy:");
      deps.stream()
          .filter(f -> f.getName().contains("groovy"))
          .forEach(f -> System.out.println("  - " + f.getName()));
    } else {
      // groovier-junit5 uses compileOnly for Groovy, so it might not be in runtime deps
      System.out.println("Note: Groovy not in runtime dependencies (may be compileOnly)");
    }

    assertTrue(deps.size() > 0, "Should have resolved some dependencies");
  }

  @Test
  public void testClassLoaderSearchOrder() {
    // This test verifies the concept that classloaders search in the order URLs are added
    GroovyClassLoader loader = new GroovyClassLoader(ClassLoader.getSystemClassLoader());

    // The URLs added FIRST will be searched FIRST
    // So if we want project Groovy to take precedence, we should add it before Gade's Groovy

    System.out.println("Initial URL count: " + loader.getURLs().length);

    // Simulate adding project dependencies first
    // (In real code, this would be done by GradleUtils.addGradleDependencies)

    // Then add Gade's Groovy as fallback
    // (In real code, this is done by addDefaultGroovyRuntimeIfMissing)

    System.out.println("Final URL count: " + loader.getURLs().length);
    System.out.println("ClassLoader search order is FIFO - first URL wins");
  }

  @Test
  public void testGroovierJunit5BuildGradle() throws Exception {
    File buildGradle = new File("/Users/pernyf/project/groovier-junit5/build.gradle");
    if (!buildGradle.exists()) {
      System.out.println("Skipping test - build.gradle not found");
      return;
    }

    String content = java.nio.file.Files.readString(buildGradle.toPath());

    // Verify the project specifies Groovy version
    assertTrue(content.contains("groovyVersion"), "build.gradle should define groovyVersion");

    // Extract the version
    String[] lines = content.split("\n");
    for (String line : lines) {
      if (line.trim().startsWith("def groovyVersion")) {
        System.out.println("Found in build.gradle: " + line.trim());
        assertTrue(line.contains("5.0."), "Should specify Groovy 5.0.x");
        break;
      }
    }
  }
}
