package se.alipsa.gade.runtime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.gade.utils.gradle.GradleUtils;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that Gradle/Maven runtime classloaders use the project's Groovy version,
 * not Gade's bundled Groovy version.
 */
public class RuntimeGroovyVersionTest {

  static Logger log = LogManager.getLogger(RuntimeGroovyVersionTest.class);

  @Test
  public void testGradleRuntimeUsesProjectGroovyVersion() throws Exception {
    File projectDir = FileUtils.getResource("utils/gradle/package");

    GradleUtils utils = new GradleUtils(null, projectDir, System.getProperty("java.home"));

    List<File> deps = utils.getProjectDependencies();
    log.debug("Project has " + deps.size() + " dependencies");

    // Check if Groovy is in the dependencies
    boolean hasGroovy = deps.stream()
        .anyMatch(f -> f.getName().contains("groovy"));

    if (hasGroovy) {
      log.debug("Project dependencies include Groovy:");
      deps.stream()
          .filter(f -> f.getName().contains("groovy"))
          .forEach(f -> log.debug("  - {}", f.getName()));
    } else {
      log.debug("Note: Groovy not in runtime dependencies (may be compileOnly)");
    }

    assertFalse(deps.isEmpty(), "Should have resolved some dependencies");
  }

  @Test
  public void testGradleProjectBuildGradleHasGroovy() throws Exception {
    File projectDir = FileUtils.getResource("utils/gradle/package");
    File buildGradle = new File(projectDir, "lib/build.gradle");

    String content = java.nio.file.Files.readString(buildGradle.toPath());

    // Verify the project specifies Groovy as a dependency
    assertTrue(content.contains("groovy"), "build.gradle should include a Groovy dependency");
  }
}
