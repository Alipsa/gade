package se.alipsa.gade.utils.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradleCompatibilityTest {

  @Test
  void supportedRangeCheckWorks() {
    assertTrue(GradleCompatibility.isSupported("5.0"));
    assertTrue(GradleCompatibility.isSupported("9.3.1"));
    assertFalse(GradleCompatibility.isSupported("4.10"));
    assertFalse(GradleCompatibility.isSupported("10.0"));
  }

  @Test
  void extractsVersionFromWrapperProperties(@TempDir Path tempDir) throws Exception {
    Path wrapperProps = tempDir.resolve("gradle-wrapper.properties");
    Files.writeString(wrapperProps,
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.10.2-bin.zip\n");
    assertEquals("8.10.2", GradleCompatibility.extractVersionFromWrapper(wrapperProps.toFile()));
  }

  @Test
  void extractsVersionFromGradleCoreJar(@TempDir Path tempDir) throws Exception {
    Path libDir = tempDir.resolve("lib");
    Files.createDirectories(libDir);
    Files.writeString(libDir.resolve("gradle-core-9.3.1.jar"), "stub");
    String version = GradleCompatibility.extractVersion(tempDir.toFile()).get(2, TimeUnit.SECONDS);
    assertEquals("9.3.1", version);
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void fallsBackToGradleCommandWhenCoreJarMissing(@TempDir Path tempDir) throws Exception {
    Path binDir = tempDir.resolve("bin");
    Files.createDirectories(binDir);
    Path gradle = binDir.resolve("gradle");
    Files.writeString(gradle, "#!/bin/sh\necho \"Gradle 8.7\"\n");
    gradle.toFile().setExecutable(true);
    String version = GradleCompatibility.extractVersion(tempDir.toFile()).get(6, TimeUnit.SECONDS);
    assertEquals("8.7", version);
  }

  @Test
  void returnsNullForMissingWrapperProperties() {
    assertNull(GradleCompatibility.extractVersionFromWrapper(null));
  }
}
