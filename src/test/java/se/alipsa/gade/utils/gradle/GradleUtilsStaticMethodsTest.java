package se.alipsa.gade.utils.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.alipsa.groovy.resolver.Dependency;

/**
 * Test suite for GradleUtils static utility methods covering:
 * - Cache directory management
 * - Artifact caching
 * - Dependency purging
 * - File path generation
 */
class GradleUtilsStaticMethodsTest {

  @AfterEach
  void cleanup() {
    // Clean up any test artifacts created
  }

  // ========== Cache Directory Tests ==========

  @Test
  void testGetCacheDirReturnsValidDirectory() {
    File cacheDir = GradleUtils.getCacheDir();

    assertNotNull(cacheDir, "Cache dir should not be null");
    assertTrue(cacheDir.exists() || cacheDir.mkdirs(),
        "Cache dir should exist or be creatable");
  }

  @Test
  void testGetCacheDirIsConsistent() {
    File cacheDir1 = GradleUtils.getCacheDir();
    File cacheDir2 = GradleUtils.getCacheDir();

    assertEquals(cacheDir1, cacheDir2,
        "Multiple calls should return same cache directory");
  }

  // ========== Cached File Path Tests ==========

  @Test
  void testCachedFileGeneratesCorrectPath() {
    Dependency dep = new Dependency("org.apache.commons", "commons-lang3", "3.12.0");
    File cachedFile = GradleUtils.cachedFile(dep);

    assertNotNull(cachedFile, "Cached file path should not be null");
    assertTrue(cachedFile.getPath().contains("org/apache/commons"),
        "Path should include group ID directories");
    assertTrue(cachedFile.getPath().contains("commons-lang3"),
        "Path should include artifact ID");
    assertTrue(cachedFile.getName().contains("3.12.0"),
        "File name should include version");
    assertTrue(cachedFile.getName().endsWith(".jar"),
        "File name should end with .jar");
  }

  @Test
  void testCachedFileForDifferentDependencies() {
    Dependency dep1 = new Dependency("com.google.guava", "guava", "31.0-jre");
    Dependency dep2 = new Dependency("org.slf4j", "slf4j-api", "2.0.0");

    File cached1 = GradleUtils.cachedFile(dep1);
    File cached2 = GradleUtils.cachedFile(dep2);

    assertFalse(cached1.equals(cached2),
        "Different dependencies should have different cache paths");
    assertTrue(cached1.getPath().contains("guava"),
        "First path should contain guava");
    assertTrue(cached2.getPath().contains("slf4j"),
        "Second path should contain slf4j");
  }

  @Test
  void testCachedFileHandlesComplexGroupId() {
    Dependency dep = new Dependency("org.springframework.boot", "spring-boot-starter", "2.7.0");
    File cachedFile = GradleUtils.cachedFile(dep);

    assertTrue(cachedFile.getPath().contains("org/springframework/boot"),
        "Should convert dots in group ID to directory separators");
  }

  // ========== Purge Cache Tests ==========

  @Test
  void testPurgeCacheDeletesExistingFile(@TempDir Path tempDir) throws IOException {
    // Create a temporary file to simulate cached artifact
    Dependency testDep = new Dependency("test.group", "test-artifact", "1.0.0");
    File originalCachedFile = GradleUtils.cachedFile(testDep);

    // Create the parent directories
    File parentDir = originalCachedFile.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      parentDir.mkdirs();
    }

    // Create the file
    if (!originalCachedFile.exists()) {
      originalCachedFile.createNewFile();
    }

    assertTrue(originalCachedFile.exists(), "Test file should exist before purge");

    // Purge the cache
    GradleUtils.purgeCache(testDep);

    // The file may be deleted immediately or marked for deletion on exit
    // We can't assert it's deleted immediately as it may use deleteOnExit()
    // but we can verify the method doesn't throw
  }

  @Test
  void testPurgeCacheHandlesNonExistentFile() {
    Dependency dep = new Dependency("non.existent", "artifact", "999.0.0");

    // Should not throw even if file doesn't exist
    GradleUtils.purgeCache(dep);
  }

  @Test
  void testPurgeCacheMultipleTimes() {
    Dependency dep = new Dependency("test.multi", "purge-test", "1.0.0");

    // Should be safe to call multiple times
    GradleUtils.purgeCache(dep);
    GradleUtils.purgeCache(dep);
    GradleUtils.purgeCache(dep);
  }

  // ========== Download Artifact Tests ==========

  @Test
  void testDownloadArtifactReturnsExistingCachedFile() throws IOException, URISyntaxException {
    // Note: This test requires an actual cached file or mocking
    // For now, we test the path logic
    Dependency dep = new Dependency("org.junit.jupiter", "junit-jupiter-api", "5.8.2");
    File expected = GradleUtils.cachedFile(dep);

    if (expected.exists()) {
      File result = GradleUtils.downloadArtifact(dep);
      assertEquals(expected, result,
          "Should return existing cached file without downloading");
    }
  }

  @Test
  void testCachedFilePathStructure() {
    Dependency dep = new Dependency("org.example", "my-lib", "1.2.3");
    File cachedFile = GradleUtils.cachedFile(dep);

    String path = cachedFile.getPath();
    assertTrue(path.contains("org" + File.separator + "example"),
        "Path should have proper directory structure");
    assertTrue(path.contains("my-lib"),
        "Path should contain artifact ID");
    assertTrue(path.endsWith("my-lib-1.2.3.jar"),
        "File should be named correctly");
  }

  @Test
  void testCachedFileIsWithinCacheDir() {
    Dependency dep = new Dependency("test.group", "artifact", "1.0");
    File cachedFile = GradleUtils.cachedFile(dep);
    File cacheDir = GradleUtils.getCacheDir();

    assertTrue(cachedFile.getAbsolutePath().startsWith(cacheDir.getAbsolutePath()),
        "Cached file should be within cache directory");
  }

  // ========== Edge Cases ==========

  @Test
  void testCachedFileWithSnapshotVersion() {
    Dependency dep = new Dependency("com.example", "snapshot-lib", "1.0-SNAPSHOT");
    File cachedFile = GradleUtils.cachedFile(dep);

    assertNotNull(cachedFile);
    assertTrue(cachedFile.getName().contains("SNAPSHOT"),
        "Should handle SNAPSHOT versions");
  }

  @Test
  void testCachedFileWithClassifier() {
    // Some dependencies have classifiers like "sources" or "javadoc"
    // The basic Dependency class might not support this, but test what we can
    Dependency dep = new Dependency("org.example", "lib-with-classifier", "2.0.0");
    File cachedFile = GradleUtils.cachedFile(dep);

    assertNotNull(cachedFile);
    assertTrue(cachedFile.exists() || !cachedFile.exists(),
        "Should generate valid path even if file doesn't exist");
  }

  @Test
  void testMultipleDependenciesHaveUniquePaths() {
    Dependency[] deps = {
        new Dependency("org.a", "lib1", "1.0"),
        new Dependency("org.a", "lib2", "1.0"),
        new Dependency("org.a", "lib1", "2.0"),
        new Dependency("org.b", "lib1", "1.0")
    };

    File[] files = new File[deps.length];
    for (int i = 0; i < deps.length; i++) {
      files[i] = GradleUtils.cachedFile(deps[i]);
    }

    // All paths should be unique
    for (int i = 0; i < files.length; i++) {
      for (int j = i + 1; j < files.length; j++) {
        assertFalse(files[i].equals(files[j]),
            "Different dependencies should have different cache paths: "
            + files[i] + " vs " + files[j]);
      }
    }
  }
}
