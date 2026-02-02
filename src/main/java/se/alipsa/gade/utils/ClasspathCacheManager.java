package se.alipsa.gade.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Stream;

/**
 * Manages classpath caching for Gradle and Maven projects.
 * <p>
 * Provides unified cache storage and retrieval using Properties files with schema versioning
 * and fingerprint-based invalidation. Supports caching both dependencies and output directories.
 * <p>
 * <b>Cache Schema:</b>
 * <ul>
 *   <li>{@code schema=1} - Schema version for format changes</li>
 *   <li>{@code fingerprint=<hash>} - Project fingerprint for invalidation</li>
 *   <li>{@code createdAtEpochMs=<timestamp>} - Cache creation timestamp</li>
 *   <li>{@code dep.0=<path>, dep.1=<path>, ...} - Dependency file paths</li>
 *   <li>{@code out.0=<path>, out.1=<path>, ...} - Output directory paths (Gradle only)</li>
 * </ul>
 *
 * @see se.alipsa.gade.utils.gradle.GradleUtils
 * @see se.alipsa.gade.utils.maven.MavenClasspathUtils
 */
public final class ClasspathCacheManager {

  private static final Logger log = LogManager.getLogger(ClasspathCacheManager.class);

  // Cache schema constants
  public static final String CACHE_SCHEMA = "schema";
  public static final String CACHE_SCHEMA_VERSION = "1";
  public static final String CACHE_FINGERPRINT = "fingerprint";
  public static final String CACHE_DEP_PREFIX = "dep.";
  public static final String CACHE_OUT_PREFIX = "out.";
  public static final String CACHE_CREATED_AT = "createdAtEpochMs";

  private ClasspathCacheManager() {
    throw new AssertionError("No instances");
  }

  /**
   * Loads cached classpath from disk.
   * <p>
   * Returns null if:
   * <ul>
   *   <li>Cache file doesn't exist</li>
   *   <li>Schema version mismatch</li>
   *   <li>Fingerprint mismatch</li>
   *   <li>Cache is empty</li>
   *   <li>Any cached file no longer exists (validation enabled)</li>
   * </ul>
   *
   * @param cacheFile the cache file to read from
   * @param expectedFingerprint the expected fingerprint for validation
   * @param validateExistence whether to check that all cached files still exist
   * @return cached classpath result, or null if cache invalid/missing
   */
  public static CachedClasspath load(File cacheFile, String expectedFingerprint, boolean validateExistence) {
    if (cacheFile == null || !cacheFile.exists()) {
      return null;
    }

    Properties props = new Properties();
    try (InputStream in = new FileInputStream(cacheFile)) {
      props.load(in);
    } catch (IOException e) {
      log.debug("Failed to read classpath cache {}", cacheFile, e);
      return null;
    }

    // Validate schema version
    if (!CACHE_SCHEMA_VERSION.equals(props.getProperty(CACHE_SCHEMA))) {
      log.debug("Cache schema mismatch for {}", cacheFile);
      return null;
    }

    // Validate fingerprint
    if (!Objects.equals(expectedFingerprint, props.getProperty(CACHE_FINGERPRINT))) {
      log.debug("Cache fingerprint mismatch for {}", cacheFile);
      return null;
    }

    // Extract dependencies (preserving order)
    LinkedHashSet<File> deps = new LinkedHashSet<>();
    props.stringPropertyNames().stream()
        .filter(key -> key.startsWith(CACHE_DEP_PREFIX))
        .sorted(Comparator.comparingInt(ClasspathCacheManager::extractIndex))
        .forEach(key -> deps.add(new File(props.getProperty(key))));

    // Extract output directories (preserving order)
    LinkedHashSet<File> outs = new LinkedHashSet<>();
    props.stringPropertyNames().stream()
        .filter(key -> key.startsWith(CACHE_OUT_PREFIX))
        .sorted(Comparator.comparingInt(ClasspathCacheManager::extractIndex))
        .forEach(key -> outs.add(new File(props.getProperty(key))));

    // Cache must have at least one entry
    if (deps.isEmpty() && outs.isEmpty()) {
      log.debug("Cache is empty for {}", cacheFile);
      return null;
    }

    // Optionally validate that cached files still exist
    if (validateExistence) {
      if (!allExist(deps) || !allExist(outs)) {
        log.debug("Cached files no longer exist for {}", cacheFile);
        return null;
      }
    }

    return new CachedClasspath(List.copyOf(deps), List.copyOf(outs));
  }

  /**
   * Saves classpath to cache file.
   * <p>
   * Uses atomic write strategy (write to temp file, then atomic move) to prevent
   * corruption from concurrent writes or crashes.
   *
   * @param cacheFile the cache file to write to
   * @param fingerprint the project fingerprint
   * @param dependencies the dependency file paths to cache
   * @param outputDirs the output directory paths to cache (can be empty)
   * @param comment comment to include in Properties file header
   */
  public static void store(File cacheFile, String fingerprint, List<File> dependencies,
                           List<File> outputDirs, String comment) {
    if (cacheFile == null) {
      log.warn("Cannot store cache - cacheFile is null");
      return;
    }

    Properties props = new Properties();
    props.setProperty(CACHE_SCHEMA, CACHE_SCHEMA_VERSION);
    props.setProperty(CACHE_FINGERPRINT, fingerprint);
    props.setProperty(CACHE_CREATED_AT, String.valueOf(System.currentTimeMillis()));

    // Store dependencies
    int index = 0;
    for (File dep : dependencies) {
      if (dep != null) {
        props.setProperty(CACHE_DEP_PREFIX + index++, dep.getAbsolutePath());
      }
    }

    // Store output directories
    index = 0;
    for (File out : outputDirs) {
      if (out != null) {
        props.setProperty(CACHE_OUT_PREFIX + index++, out.getAbsolutePath());
      }
    }

    // Ensure parent directory exists
    File parentDir = cacheFile.getParentFile();
    if (parentDir == null) {
      log.warn("Cannot write cache - parent directory is null for {}", cacheFile);
      return;
    }
    if (!parentDir.exists() && !parentDir.mkdirs()) {
      log.warn("Failed to create cache directory {}", parentDir);
      return;
    }

    // Atomic write: write to temp file, then move
    File tmp = new File(parentDir, cacheFile.getName() + ".tmp");
    try (OutputStream out = new FileOutputStream(tmp)) {
      props.store(out, comment != null ? comment : "Gade classpath cache");
      Files.move(tmp.toPath(), cacheFile.toPath(),
          StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      log.debug("Wrote classpath cache to {}", cacheFile);
    } catch (Exception e) {
      log.debug("Failed to write classpath cache {}", cacheFile, e);
      // Clean up temp file if it still exists
      if (tmp.exists() && !tmp.delete()) {
        log.debug("Failed to delete temp cache file {}", tmp);
      }
    }
  }

  /**
   * Simplified store method for dependencies-only caching (Maven).
   */
  public static void store(File cacheFile, String fingerprint, List<File> dependencies, String comment) {
    store(cacheFile, fingerprint, dependencies, Collections.emptyList(), comment);
  }

  /**
   * Returns the base cache directory used by Gade.
   */
  public static File getCacheDir() {
    File dir = new File(FileUtils.getUserHome(), ".gade/cache");
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        throw new RuntimeException("Failed to create cache dir " + dir);
      }
    }
    return dir;
  }

  /**
   * Computes a fingerprint for classpath invalidation.
   *
   * @param projectDir the project directory
   * @param testContext whether test dependencies are in use
   * @param trackedFiles the ordered list of files to track
   * @param extraEntries additional key/value pairs to append (order preserved)
   * @return SHA-256 fingerprint
   */
  public static String computeFingerprint(File projectDir, boolean testContext, List<Path> trackedFiles,
                                          Map<String, String> extraEntries) {
    StringBuilder sb = new StringBuilder();
    sb.append("project=").append(projectDir.getAbsolutePath()).append('\n');
    sb.append("testContext=").append(testContext).append('\n');
    if (trackedFiles != null) {
      for (Path p : trackedFiles) {
        if (p == null) {
          continue;
        }
        sb.append(p).append('|');
        try {
          if (Files.exists(p)) {
            sb.append(Files.size(p)).append('|').append(Files.getLastModifiedTime(p).toMillis());
          } else {
            sb.append("missing");
          }
        } catch (IOException e) {
          sb.append("error:").append(e.getClass().getSimpleName());
        }
        sb.append('\n');
      }
    }
    if (extraEntries != null) {
      for (Map.Entry<String, String> entry : extraEntries.entrySet()) {
        sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
      }
    }
    return sha256Hex(sb.toString());
  }

  /**
   * Returns the newest modified timestamp for files under the given directory.
   */
  public static long latestModified(Path dir) {
    if (dir == null || !Files.exists(dir)) {
      return 0L;
    }
    try (Stream<Path> stream = Files.walk(dir)) {
      return stream
          .filter(Files::isRegularFile)
          .limit(2000)
          .mapToLong(path -> {
            try {
              return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
              return 0L;
            }
          })
          .max()
          .orElse(0L);
    } catch (IOException e) {
      return 0L;
    }
  }

  /**
   * Computes a SHA-256 hash from the provided string.
   */
  public static String sha256Hex(String str) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(str.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /**
   * Extracts numeric index from cache property key (e.g., "dep.5" → 5).
   */
  private static int extractIndex(String key) {
    int dot = key.lastIndexOf('.');
    if (dot < 0 || dot >= key.length() - 1) {
      return Integer.MAX_VALUE; // Invalid format, sort to end
    }
    try {
      return Integer.parseInt(key.substring(dot + 1));
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE; // Invalid number, sort to end
    }
  }

  /**
   * Checks if all files in the collection exist.
   */
  private static boolean allExist(LinkedHashSet<File> files) {
    for (File f : files) {
      if (f == null || !f.exists()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Result of loading cached classpath.
   *
   * @param dependencies list of dependency file paths
   * @param outputDirs list of output directory paths (empty for Maven)
   */
  public record CachedClasspath(List<File> dependencies, List<File> outputDirs) {
    public CachedClasspath {
      // Defensive copies
      dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
      outputDirs = outputDirs != null ? List.copyOf(outputDirs) : List.of();
    }
  }
}
