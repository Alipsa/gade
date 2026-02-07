package se.alipsa.gade.console;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroovyRuntimeManagerTest {

  @TempDir
  Path tempDir;

  @Test
  void isTestSourceDetectsDefaultTestPaths() throws Exception {
    Path script = tempDir.resolve("src/test/groovy/SampleTest.groovy");
    Files.createDirectories(script.getParent());
    Files.createFile(script);

    boolean result = GroovyRuntimeManager.isTestSource(tempDir.toFile(), script.toFile());

    assertTrue(result);
  }

  @Test
  void isTestSourceUsesConfiguredTestDirectories() throws Exception {
    Path script = tempDir.resolve("src/integration/groovy/SmokeTest.groovy");
    Files.createDirectories(script.getParent());
    Files.createFile(script);
    Set<Path> configured = Set.of(tempDir.resolve("src/integration/groovy"));

    boolean result = GroovyRuntimeManager.isTestSource(
        tempDir.toFile(),
        script.toFile(),
        configured
    );

    assertTrue(result);
  }

  @Test
  void isTestSourceRejectsFilesOutsideProject() throws Exception {
    Path external = Files.createTempFile("outside", ".groovy");
    boolean result = GroovyRuntimeManager.isTestSource(tempDir.toFile(), external.toFile(), Set.of());
    Files.deleteIfExists(external);
    assertFalse(result);
  }
}
