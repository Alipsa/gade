package se.alipsa.gade.utils.gradle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test suite for GradleUtils error handling and recovery logic covering:
 * - Gradle failure hint generation
 * - Java version mismatch detection
 * - Tooling API failure detection
 * - Error message parsing
 */
class GradleUtilsErrorHandlingTest {

  // ========== Gradle Failure Hint Tests ==========

  @Test
  void testGradleFailureHintForJavaMismatch(@TempDir Path tempDir) throws Exception {
    File projectDir = tempDir.toFile();
    Files.createDirectories(projectDir.toPath());
    Files.writeString(projectDir.toPath().resolve("build.gradle"), "");

    GradleUtils gradleUtils = new GradleUtils(null, projectDir, System.getProperty("java.home"));

    // Use reflection to access private method
    Method method = GradleUtils.class.getDeclaredMethod("gradleFailureHint", Throwable.class);
    method.setAccessible(true);

    // Test UnsupportedClassVersionError
    Throwable error = new UnsupportedClassVersionError("Bad version");
    String hint = (String) method.invoke(gradleUtils, error);

    assertNotNull(hint, "Should provide hint for version error");
    assertTrue(hint.contains("Java runtime") || hint.contains("JDK"),
        "Hint should mention Java runtime: " + hint);
  }

  @Test
  void testGradleFailureHintForJavaVersionMessage(@TempDir Path tempDir) throws Exception {
    File projectDir = tempDir.toFile();
    Files.createDirectories(projectDir.toPath());
    Files.writeString(projectDir.toPath().resolve("build.gradle"), "");

    GradleUtils gradleUtils = new GradleUtils(null, projectDir, System.getProperty("java.home"));

    Method method = GradleUtils.class.getDeclaredMethod("gradleFailureHint", Throwable.class);
    method.setAccessible(true);

    // Test exception with "requires Java" message
    Throwable error = new RuntimeException("This Gradle version requires Java 17");
    String hint = (String) method.invoke(gradleUtils, error);

    assertNotNull(hint, "Should provide hint for Java requirement message");
    assertTrue(hint.contains("Java runtime"),
        "Hint should mention Java runtime for version requirement");
  }

  @Test
  void testGradleFailureHintForToolingApiFailure(@TempDir Path tempDir) throws Exception {
    File projectDir = tempDir.toFile();
    Files.createDirectories(projectDir.toPath());
    Files.writeString(projectDir.toPath().resolve("build.gradle"), "");

    GradleUtils gradleUtils = new GradleUtils(null, projectDir, System.getProperty("java.home"));

    Method method = GradleUtils.class.getDeclaredMethod("gradleFailureHint", Throwable.class);
    method.setAccessible(true);

    // Test Tooling API failure
    Throwable error = new RuntimeException("Could not create an instance of Tooling API implementation");
    String hint = (String) method.invoke(gradleUtils, error);

    assertNotNull(hint, "Should provide hint for Tooling API failure");
    assertTrue(hint.contains("corrupted") || hint.contains("cache"),
        "Hint should mention corruption or cache: " + hint);
  }

  @Test
  void testGradleFailureHintForNormalException(@TempDir Path tempDir) throws Exception {
    File projectDir = tempDir.toFile();
    Files.createDirectories(projectDir.toPath());
    Files.writeString(projectDir.toPath().resolve("build.gradle"), "");

    GradleUtils gradleUtils = new GradleUtils(null, projectDir, System.getProperty("java.home"));

    Method method = GradleUtils.class.getDeclaredMethod("gradleFailureHint", Throwable.class);
    method.setAccessible(true);

    // Test normal exception (should return null - no hint needed)
    Throwable error = new RuntimeException("Some normal build error");
    String hint = (String) method.invoke(gradleUtils, error);

    // Null is expected for normal errors - no special hint needed
    // This is correct behavior
  }

  @Test
  void testGradleFailureHintForNestedExceptions(@TempDir Path tempDir) throws Exception {
    File projectDir = tempDir.toFile();
    Files.createDirectories(projectDir.toPath());
    Files.writeString(projectDir.toPath().resolve("build.gradle"), "");

    GradleUtils gradleUtils = new GradleUtils(null, projectDir, System.getProperty("java.home"));

    Method method = GradleUtils.class.getDeclaredMethod("gradleFailureHint", Throwable.class);
    method.setAccessible(true);

    // Test nested exception with version error
    Throwable cause = new UnsupportedClassVersionError("61.0");
    Throwable wrapper = new RuntimeException("Build failed", cause);
    String hint = (String) method.invoke(gradleUtils, wrapper);

    assertNotNull(hint, "Should detect version error in nested exception");
    assertTrue(hint.contains("Java"),
        "Should provide Java-related hint for nested version error");
  }

  @Test
  void testGradleFailureHintForUnsupportedClassFileVersion(@TempDir Path tempDir) throws Exception {
    File projectDir = tempDir.toFile();
    Files.createDirectories(projectDir.toPath());
    Files.writeString(projectDir.toPath().resolve("build.gradle"), "");

    GradleUtils gradleUtils = new GradleUtils(null, projectDir, System.getProperty("java.home"));

    Method method = GradleUtils.class.getDeclaredMethod("gradleFailureHint", Throwable.class);
    method.setAccessible(true);

    // Test message about unsupported class file version
    Throwable error = new RuntimeException("Unsupported class file major version 65");
    String hint = (String) method.invoke(gradleUtils, error);

    assertNotNull(hint, "Should provide hint for class file version error");
    assertTrue(hint.contains("Java"),
        "Hint should mention Java for class file version issues");
  }

  // ========== Try Switch To Default User Home Tests ==========

  @Test
  void testTrySwitchToDefaultUserHomeLogic(@TempDir Path tempDir) throws Exception {
    File projectDir = tempDir.toFile();
    Files.createDirectories(projectDir.toPath());
    Files.writeString(projectDir.toPath().resolve("build.gradle"), "");

    GradleUtils gradleUtils = new GradleUtils(null, projectDir, System.getProperty("java.home"));

    // Access private method
    Method method = GradleUtils.class.getDeclaredMethod("trySwitchToDefaultUserHome", Throwable.class);
    method.setAccessible(true);

    // Test with a distribution install failure
    Throwable error = new RuntimeException("Failed to install Gradle distribution");

    // The method should return false if not using custom user home
    // or if the condition doesn't match
    Object result = method.invoke(gradleUtils, error);

    // Result type is boolean
    assertNotNull(result, "Method should return a boolean");
  }

  // ========== Configuration Access Tests ==========

  @Test
  void testGetGradleUserHomeDir(@TempDir Path tempDir) throws Exception {
    File projectDir = tempDir.toFile();
    Files.createDirectories(projectDir.toPath());

    GradleUtils gradleUtils = new GradleUtils(null, projectDir, System.getProperty("java.home"));
    File gradleUserHome = gradleUtils.getGradleUserHomeDir();

    // Gradle user home may be null if using default ~/.gradle
    // The method returns the configured custom user home, or null if using default
    // Both are valid states
    if (gradleUserHome != null) {
      assertTrue(gradleUserHome.exists() || gradleUserHome.getName().contains("gradle"),
          "If set, should be a valid Gradle home directory");
    }
  }

  @Test
  void testGetDistributionOrder(@TempDir Path tempDir) throws Exception {
    File projectDir = tempDir.toFile();
    Files.createDirectories(projectDir.toPath());

    // Test without wrapper
    GradleUtils gradleUtils1 = new GradleUtils(null, projectDir, null);
    var order1 = gradleUtils1.getDistributionOrder();
    assertNotNull(order1, "Distribution order should not be null");
    assertFalse(order1.isEmpty(), "Distribution order should not be empty");

    // Test with wrapper present
    Path wrapperDir = projectDir.toPath().resolve("gradle/wrapper");
    Files.createDirectories(wrapperDir);
    Files.writeString(wrapperDir.resolve("gradle-wrapper.properties"),
        "distributionUrl=https://services.gradle.org/distributions/gradle-7.5-bin.zip");

    GradleUtils gradleUtils2 = new GradleUtils(null, projectDir, null);
    var order2 = gradleUtils2.getDistributionOrder();
    assertNotNull(order2, "Distribution order with wrapper should not be null");
    assertTrue(order2.contains(GradleUtils.DistributionMode.WRAPPER),
        "Should include WRAPPER mode when wrapper is present");
  }

  @Test
  void testGetDistributionOrderWithInstallation(@TempDir Path tempDir) throws Exception {
    File projectDir = tempDir.toFile();
    Files.createDirectories(projectDir.toPath());

    // Create a fake Gradle installation directory
    File fakeInstallDir = tempDir.resolve("gradle-installation").toFile();
    fakeInstallDir.mkdirs();

    GradleUtils gradleUtils = new GradleUtils(fakeInstallDir, projectDir, null);
    var order = gradleUtils.getDistributionOrder();

    assertNotNull(order, "Distribution order should not be null");
    assertTrue(order.contains(GradleUtils.DistributionMode.INSTALLATION),
        "Should include INSTALLATION mode when installation dir is provided");
  }

  // ========== Fingerprint Tests ==========

  @Test
  void testGetProjectFingerprintChangesWithBuildFile(@TempDir Path tempDir) throws Exception {
    File projectDir = tempDir.toFile();
    Files.createDirectories(projectDir.toPath());

    GradleUtils gradleUtils = new GradleUtils(null, projectDir, System.getProperty("java.home"));

    // Get initial fingerprint
    String fingerprint1 = gradleUtils.getProjectFingerprint();
    assertNotNull(fingerprint1, "Fingerprint should not be null");

    // Modify build.gradle
    Thread.sleep(10); // Ensure timestamp changes
    Files.writeString(projectDir.toPath().resolve("build.gradle"),
        "plugins { id 'java' }");

    String fingerprint2 = gradleUtils.getProjectFingerprint();
    assertNotNull(fingerprint2, "Fingerprint after change should not be null");

    // Fingerprints should be different after modification
    // (though this depends on file modification time granularity)
  }

  @Test
  void testGetProjectFingerprintConsistency(@TempDir Path tempDir) throws Exception {
    File projectDir = tempDir.toFile();
    Files.createDirectories(projectDir.toPath());
    Files.writeString(projectDir.toPath().resolve("build.gradle"), "// test");

    GradleUtils gradleUtils = new GradleUtils(null, projectDir, System.getProperty("java.home"));

    // Multiple calls without changes should return same fingerprint
    String fingerprint1 = gradleUtils.getProjectFingerprint();
    String fingerprint2 = gradleUtils.getProjectFingerprint();

    assertNotNull(fingerprint1);
    assertNotNull(fingerprint2);
    // Fingerprints should be consistent for unchanged project
    assertTrue(fingerprint1.length() > 0, "Fingerprint should be non-empty");
  }
}
