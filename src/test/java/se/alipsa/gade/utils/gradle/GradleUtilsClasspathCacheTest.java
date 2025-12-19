package se.alipsa.gade.utils.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GradleUtilsClasspathCacheTest {

  @Test
  void usesClasspathCacheWhenGradleBuildIsBroken(@TempDir Path tempDir) throws Exception {
    Path projectDir = tempDir.resolve("broken-gradle-project");
    Files.createDirectories(projectDir);
    Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'broken'");
    Files.writeString(projectDir.resolve("build.gradle"), "this is not valid groovy");

    File dummyJar = projectDir.resolve("dummy.jar").toFile();
    assertTrue(dummyJar.createNewFile(), "Failed to create dummy jar");

    Path outputDir = projectDir.resolve("build").resolve("classes").resolve("groovy").resolve("main");
    Files.createDirectories(outputDir);

    GradleUtils seed = new GradleUtils(null, projectDir.toFile(), System.getProperty("java.home"));
    String fingerprint = seed.getProjectFingerprint();
    File cacheFile = seed.getClasspathCacheFile(false);

    Properties props = new Properties();
    props.setProperty("schema", "1");
    props.setProperty("fingerprint", fingerprint);
    props.setProperty("createdAtEpochMs", String.valueOf(System.currentTimeMillis()));
    props.setProperty("dep.0", dummyJar.getAbsolutePath());
    props.setProperty("out.0", outputDir.toFile().getAbsolutePath());

    try (FileOutputStream out = new FileOutputStream(cacheFile)) {
      props.store(out, "test");
    }

    try {
      GradleUtils gradleUtils = new GradleUtils(null, projectDir.toFile(), System.getProperty("java.home"));
      List<File> deps = gradleUtils.getProjectDependencies();
      assertEquals(List.of(dummyJar), deps, "Expected dependencies to come from cache without invoking Gradle");
    } finally {
      // Ensure no cross-test pollution in the user's home directory cache.
      if (cacheFile.exists()) {
        cacheFile.delete();
      }
    }
  }
}
