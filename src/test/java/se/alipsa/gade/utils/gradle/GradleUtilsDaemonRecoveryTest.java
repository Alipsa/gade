package se.alipsa.gade.utils.gradle;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify the daemon cache clearing functionality works correctly
 */
public class GradleUtilsDaemonRecoveryTest {

  @Test
  public void testFindGradleCommand() {
    File projectDir = new File("/Users/pernyf/project/groovier-junit5");
    if (!projectDir.exists()) {
      System.out.println("Skipping test - project directory does not exist");
      return;
    }

    GradleUtils utils = new GradleUtils(null, projectDir, null);

    // Use reflection to call the private findGradleCommand method
    try {
      var method = GradleUtils.class.getDeclaredMethod("findGradleCommand");
      method.setAccessible(true);
      String gradleCommand = (String) method.invoke(utils);

      System.out.println("Found Gradle command: " + gradleCommand);
      assertNotNull(gradleCommand, "Should find a Gradle command");
      assertTrue(new File(gradleCommand).exists(), "Gradle command should exist");
    } catch (Exception e) {
      fail("Failed to test findGradleCommand: " + e.getMessage());
    }
  }

  @Test
  public void testDaemonCacheDirectoryStructure() {
    String userHome = System.getProperty("user.home");
    File gradleHome = new File(userHome, ".gradle");
    File daemonDir = new File(gradleHome, "daemon");

    if (daemonDir.exists()) {
      System.out.println("Gradle daemon directory exists at: " + daemonDir);
      System.out.println("Contents:");
      File[] contents = daemonDir.listFiles();
      if (contents != null) {
        for (File f : contents) {
          System.out.println("  - " + f.getName() + (f.isDirectory() ? " (dir)" : ""));
        }
      }
    } else {
      System.out.println("Gradle daemon directory does not exist (this is normal if no daemons have run)");
    }

    File cachesDir = new File(gradleHome, "caches");
    if (cachesDir.exists()) {
      System.out.println("\nGradle caches directory exists at: " + cachesDir);
      File[] versionDirs = cachesDir.listFiles(f -> f.isDirectory() && f.getName().matches("\\d+\\.\\d+.*"));
      if (versionDirs != null && versionDirs.length > 0) {
        System.out.println("Version-specific caches:");
        for (File versionDir : versionDirs) {
          System.out.println("  - " + versionDir.getName());
        }
      }
    }
  }

  @Test
  public void testErrorDetection() throws Exception {
    File projectDir = new File("/Users/pernyf/project/groovier-junit5");
    if (!projectDir.exists()) {
      System.out.println("Skipping test - project directory does not exist");
      return;
    }

    GradleUtils utils = new GradleUtils(null, projectDir, null);

    // Test isDaemonOrCacheCorruption method using reflection
    var isDaemonCorruptionMethod = GradleUtils.class.getDeclaredMethod("isDaemonOrCacheCorruption", Throwable.class);
    isDaemonCorruptionMethod.setAccessible(true);

    // Create test exceptions
    Exception moduleException = new Exception("Cannot locate manifest for module 'gradle-core' in classpath: []");
    Exception classLoaderException = new Exception("Could not create service of type ClassLoaderRegistry");
    Exception toolingApiException = new Exception("Could not create an instance of Tooling API implementation");
    Exception normalException = new Exception("Some other error");

    assertTrue((Boolean) isDaemonCorruptionMethod.invoke(utils, moduleException),
        "Should detect module manifest error");
    assertTrue((Boolean) isDaemonCorruptionMethod.invoke(utils, classLoaderException),
        "Should detect ClassLoaderRegistry error");
    assertTrue((Boolean) isDaemonCorruptionMethod.invoke(utils, toolingApiException),
        "Should detect Tooling API error");
    assertFalse((Boolean) isDaemonCorruptionMethod.invoke(utils, normalException),
        "Should not detect normal exceptions as corruption");

    System.out.println("Error detection working correctly");
  }

  @Test
  public void testExtractErrorType() throws Exception {
    File projectDir = new File("/Users/pernyf/project/groovier-junit5");
    if (!projectDir.exists()) {
      projectDir = new File(".");
    }

    GradleUtils utils = new GradleUtils(null, projectDir, null);

    var extractErrorTypeMethod = GradleUtils.class.getDeclaredMethod("extractErrorType", Throwable.class);
    extractErrorTypeMethod.setAccessible(true);

    Exception moduleException = new Exception("Cannot locate manifest for module 'gradle-core' in classpath: []");
    String errorType = (String) extractErrorTypeMethod.invoke(utils, moduleException);

    System.out.println("Extracted error type: " + errorType);
    assertEquals("module manifest not found", errorType);
  }
}
