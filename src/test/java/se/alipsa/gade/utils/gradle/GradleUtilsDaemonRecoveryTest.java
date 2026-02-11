package se.alipsa.gade.utils.gradle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify the daemon cache clearing functionality works correctly
 */
public class GradleUtilsDaemonRecoveryTest {

  private static final Logger log = LogManager.getLogger(GradleUtilsDaemonRecoveryTest.class);

  @Test
  public void testFindGradleCommand() throws Exception {
    File projectDir = new File("/Users/pernyf/project/groovier-junit5");
    if (!projectDir.exists()) {
      log.info("Skipping test - project directory does not exist");
      return;
    }

    // Create a GradleDaemonRecovery instance via reflection since it's package-private
    var configManagerConstructor = GradleConfigurationManager.class.getDeclaredConstructor(
        File.class, File.class, String.class);
    configManagerConstructor.setAccessible(true);
    var configManager = configManagerConstructor.newInstance(
        projectDir, new File(projectDir, ".gradle-gade-tooling"), null);

    var daemonRecoveryConstructor = GradleDaemonRecovery.class.getDeclaredConstructor(
        File.class, File.class, GradleConfigurationManager.class);
    daemonRecoveryConstructor.setAccessible(true);
    var daemonRecovery = daemonRecoveryConstructor.newInstance(
        projectDir, new File(projectDir, ".gradle-gade-tooling"), configManager);

    var method = GradleDaemonRecovery.class.getDeclaredMethod("findGradleCommand");
    method.setAccessible(true);
    String gradleCommand = (String) method.invoke(daemonRecovery);

    log.info("Found Gradle command: {}", gradleCommand);
    assertNotNull(gradleCommand, "Should find a Gradle command");
    assertTrue(new File(gradleCommand).exists(), "Gradle command should exist");
  }

  @Test
  public void testDaemonCacheDirectoryStructure() {
    String userHome = System.getProperty("user.home");
    File gradleHome = new File(userHome, ".gradle");
    File daemonDir = new File(gradleHome, "daemon");

    if (daemonDir.exists()) {
      log.info("Gradle daemon directory exists at: {}", daemonDir);
      File[] contents = daemonDir.listFiles();
      if (contents != null) {
        for (File f : contents) {
          log.info("  - {}{}", f.getName(), f.isDirectory() ? " (dir)" : "");
        }
      }
    } else {
      log.info("Gradle daemon directory does not exist (this is normal if no daemons have run)");
    }

    File cachesDir = new File(gradleHome, "caches");
    if (cachesDir.exists()) {
      log.info("Gradle caches directory exists at: {}", cachesDir);
      File[] versionDirs = cachesDir.listFiles(f -> f.isDirectory() && f.getName().matches("\\d+\\.\\d+.*"));
      if (versionDirs != null && versionDirs.length > 0) {
        for (File versionDir : versionDirs) {
          log.info("  Version-specific cache: {}", versionDir.getName());
        }
      }
    }
  }

  @Test
  public void testErrorDetection() {
    // These methods are now static in GradleDaemonRecovery - test directly

    // Create test exceptions for ACTUAL corruption (file-level issues)
    Exception zipException = new Exception("ZipException: invalid CEN header");
    Exception corruptedFileException = new Exception("File is corrupted and cannot be read");
    Exception truncatedDownloadException = new Exception("unexpected end of file in archive");

    // Create test exceptions for non-corruption issues (configuration/compatibility)
    Exception moduleException = new Exception("Cannot locate manifest for module 'gradle-core' in classpath: []");
    Exception classLoaderException = new Exception("Could not create service of type ClassLoaderRegistry");
    Exception toolingApiException = new Exception("Could not create an instance of Tooling API implementation");
    Exception normalException = new Exception("Some other error");

    // Actual corruption should be detected
    assertTrue(GradleDaemonRecovery.isDaemonOrCacheCorruption(zipException),
        "Should detect zip corruption");
    assertTrue(GradleDaemonRecovery.isDaemonOrCacheCorruption(corruptedFileException),
        "Should detect corrupted file");
    assertTrue(GradleDaemonRecovery.isDaemonOrCacheCorruption(truncatedDownloadException),
        "Should detect truncated download");

    // Non-corruption issues should NOT be detected as corruption
    // (these are configuration/compatibility issues, not file corruption)
    assertFalse(GradleDaemonRecovery.isDaemonOrCacheCorruption(moduleException),
        "Module manifest error is not file corruption (may be Tooling API issue)");
    assertFalse(GradleDaemonRecovery.isDaemonOrCacheCorruption(classLoaderException),
        "ClassLoader error is not file corruption");
    assertFalse(GradleDaemonRecovery.isDaemonOrCacheCorruption(toolingApiException),
        "Tooling API error is not file corruption");
    assertFalse(GradleDaemonRecovery.isDaemonOrCacheCorruption(normalException),
        "Should not detect normal exceptions as corruption");

  }

  @Test
  public void testExtractErrorType() {
    // This method is now static in GradleDaemonRecovery - test directly

    Exception zipException = new Exception("ZipException: invalid CEN header (bad signature)");
    String errorType = GradleDaemonRecovery.extractErrorType(zipException);

    log.info("Extracted error type: {}", errorType);
    // extractErrorType is used for actual corruption errors, not Tooling API issues
    assertTrue(errorType != null && !errorType.isEmpty(), "Should extract error type from zip corruption");
  }

  @Test
  public void testShouldPurgeDistributionCache() {
    // Test the shouldPurgeDistributionCache static method
    // Should only purge for actual file corruption, not Tooling API issues

    Exception zipCorruptionException = new Exception("ZipException: invalid CEN header");
    Exception corruptedFileException = new Exception("File is corrupted");
    Exception toolingApiException = new Exception("Error with gradle-runtime-api-info module");
    Exception normalException = new Exception("Some other error");

    assertTrue(GradleDaemonRecovery.shouldPurgeDistributionCache(zipCorruptionException),
        "Should purge for zip corruption");
    assertTrue(GradleDaemonRecovery.shouldPurgeDistributionCache(corruptedFileException),
        "Should purge for corrupted files");
    assertFalse(GradleDaemonRecovery.shouldPurgeDistributionCache(toolingApiException),
        "Should not purge for Tooling API errors");
    assertFalse(GradleDaemonRecovery.shouldPurgeDistributionCache(normalException),
        "Should not purge for normal exceptions");
  }

  @Test
  public void testStaleLockFileDetection() {
    // Test that stale lock file detection works
    // This test verifies the method exists and returns properly structured data

    Exception gradleException = new Exception(
        "Could not fetch model using Gradle distribution 'https://services.gradle.org/distributions/gradle-8.13-all.zip'");

    var staleLocks = GradleDaemonRecovery.findStaleLockFiles(gradleException);

    // The method should complete without error
    assertNotNull(staleLocks, "findStaleLockFiles should return an Optional");

  }
}
