package se.alipsa.gade.utils.gradle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Handles Gradle daemon corruption detection and recovery.
 * <p>
 * This class provides functionality to detect, diagnose, and recover from
 * Gradle daemon and cache corruption issues. It can:
 * <ul>
 *   <li>Detect daemon or cache corruption from exception messages</li>
 *   <li>Classify error types for logging and diagnostics</li>
 *   <li>Clear daemon caches by stopping daemons and removing cache files</li>
 *   <li>Purge project-local Gradle user home directories</li>
 * </ul>
 *
 * @see GradleUtils
 */
public final class GradleDaemonRecovery {

  private static final Logger log = LogManager.getLogger(GradleDaemonRecovery.class);

  private final File projectDir;
  private final File projectLocalGradleUserHome;
  private final GradleConfigurationManager configManager;

  /**
   * Creates a new daemon recovery handler.
   *
   * @param projectDir the Gradle project directory
   * @param projectLocalGradleUserHome the project-local Gradle user home directory
   * @param configManager the configuration manager for accessing Gradle settings
   */
  GradleDaemonRecovery(File projectDir, File projectLocalGradleUserHome,
                       GradleConfigurationManager configManager) {
    this.projectDir = projectDir;
    this.projectLocalGradleUserHome = projectLocalGradleUserHome;
    this.configManager = configManager;
  }

  /**
   * Represents a corrupted Gradle distribution found in the cache.
   */
  public record CorruptedDistribution(String name, File directory, File zipFile, String reason) {}

  /**
   * Extracts the distribution URL from an error message.
   *
   * @param throwable the exception to analyze
   * @return the distribution URL if found, empty otherwise
   */
  public static Optional<String> extractDistributionUrl(Throwable throwable) {
    Pattern pattern = Pattern.compile("Gradle distribution '(https?://[^']+)'");
    Throwable t = throwable;
    while (t != null) {
      String msg = t.getMessage();
      if (msg != null) {
        Matcher matcher = pattern.matcher(msg);
        if (matcher.find()) {
          return Optional.of(matcher.group(1));
        }
      }
      t = t.getCause();
    }
    return Optional.empty();
  }

  /**
   * Finds a corrupted distribution based on the error message.
   *
   * @param throwable the exception from Gradle
   * @return the corrupted distribution if found, empty otherwise
   */
  public static Optional<CorruptedDistribution> findCorruptedDistribution(Throwable throwable) {
    Optional<String> urlOpt = extractDistributionUrl(throwable);
    if (urlOpt.isEmpty()) {
      return Optional.empty();
    }

    String url = urlOpt.get();
    // Extract distribution name from URL (e.g., gradle-8.13-all from gradle-8.13-all.zip)
    String fileName = url.substring(url.lastIndexOf('/') + 1);
    String distName = fileName.replace(".zip", "");

    return findCorruptedDistributionByName(distName, isDaemonOrCacheCorruption(throwable));
  }

  /**
   * Finds a corrupted distribution by name.
   *
   * @param distName the distribution name (e.g., "gradle-8.13-all")
   * @param assumeCorruptIfExists if true, assume corruption even if no obvious signs
   * @return the corrupted distribution if found, empty otherwise
   */
  private static Optional<CorruptedDistribution> findCorruptedDistributionByName(String distName, boolean assumeCorruptIfExists) {
    File wrapperDists = new File(FileUtils.getUserHome(), ".gradle/wrapper/dists");
    if (!wrapperDists.exists()) {
      return Optional.empty();
    }

    // Look for the distribution directory
    File distDir = new File(wrapperDists, distName);
    if (!distDir.exists() || !distDir.isDirectory()) {
      return Optional.empty();
    }

    // Find subdirectories (Gradle uses hash subdirectories)
    File[] hashDirs = distDir.listFiles(File::isDirectory);
    if (hashDirs == null || hashDirs.length == 0) {
      return Optional.of(new CorruptedDistribution(distName, distDir, null, "Empty distribution directory"));
    }

    // Check each hash directory for a corrupted zip or incomplete extraction
    for (File hashDir : hashDirs) {
      File[] zipFiles = hashDir.listFiles((dir, name) -> name.endsWith(".zip"));
      if (zipFiles != null && zipFiles.length > 0) {
        for (File zipFile : zipFiles) {
          String corruptReason = checkZipCorruption(zipFile);
          if (corruptReason != null) {
            return Optional.of(new CorruptedDistribution(distName, hashDir, zipFile, corruptReason));
          }
        }
      }

      // Check for incomplete extraction (missing essential files)
      File[] extractedDirs = hashDir.listFiles(f -> f.isDirectory() && f.getName().startsWith("gradle-"));
      if (extractedDirs != null && extractedDirs.length > 0) {
        for (File extractedDir : extractedDirs) {
          File libDir = new File(extractedDir, "lib");
          if (!libDir.exists() || !new File(libDir, "gradle-core-api-" + extractedDir.getName().replace("gradle-", "") + ".jar").exists()) {
            // Check for any gradle-core jar
            File[] coreJars = libDir.listFiles((dir, name) -> name.startsWith("gradle-core"));
            if (coreJars == null || coreJars.length == 0) {
              return Optional.of(new CorruptedDistribution(distName, hashDir, null, "Incomplete extraction - missing gradle-core"));
            }
          }
        }
      }
    }

    // If we got here with a corruption error but didn't find obvious corruption,
    // the distribution is likely corrupted in a way we can't easily detect
    if (assumeCorruptIfExists) {
      return Optional.of(new CorruptedDistribution(distName, hashDirs[0], null, "Distribution initialization failed"));
    }

    return Optional.empty();
  }

  /**
   * Finds all potentially corrupted distributions for a project.
   * This checks both the wrapper distribution (if specified) and the embedded distribution.
   *
   * @param projectDir the project directory to check for wrapper configuration
   * @param embeddedVersion the embedded Gradle version (from Tooling API)
   * @return list of corrupted distributions found
   */
  public static List<CorruptedDistribution> findAllCorruptedDistributions(File projectDir, String embeddedVersion) {
    List<CorruptedDistribution> corrupted = new ArrayList<>();

    // Check wrapper distribution if project has one
    File wrapperProps = new File(projectDir, "gradle/wrapper/gradle-wrapper.properties");
    if (wrapperProps.exists()) {
      try {
        java.util.Properties props = new java.util.Properties();
        try (java.io.FileInputStream fis = new java.io.FileInputStream(wrapperProps)) {
          props.load(fis);
        }
        String distUrl = props.getProperty("distributionUrl");
        if (distUrl != null) {
          // Extract distribution name from URL
          String fileName = distUrl.substring(distUrl.lastIndexOf('/') + 1);
          String distName = fileName.replace(".zip", "").replace("\\:", ":");
          // Clean up any URL encoding
          distName = distName.replace("%2F", "/");
          if (distName.contains("/")) {
            distName = distName.substring(distName.lastIndexOf('/') + 1);
          }
          findCorruptedDistributionByName(distName, true).ifPresent(corrupted::add);
        }
      } catch (IOException e) {
        log.debug("Failed to read wrapper properties", e);
      }
    }

    // Check embedded distribution
    if (embeddedVersion != null && !embeddedVersion.isEmpty()) {
      String embeddedDistName = "gradle-" + embeddedVersion + "-bin";
      findCorruptedDistributionByName(embeddedDistName, true).ifPresent(dist -> {
        // Avoid duplicates
        if (corrupted.stream().noneMatch(c -> c.name().equals(dist.name()))) {
          corrupted.add(dist);
        }
      });
    }

    return corrupted;
  }

  /**
   * Checks if a zip file is corrupted.
   *
   * @param zipFile the zip file to check
   * @return a reason string if corrupted, null if valid
   */
  public static String checkZipCorruption(File zipFile) {
    if (!zipFile.exists()) {
      return "File does not exist";
    }
    if (zipFile.length() == 0) {
      return "File is empty (0 bytes)";
    }
    // Check for incomplete download (presence of .part or .lck files)
    File partFile = new File(zipFile.getParent(), zipFile.getName() + ".part");
    File lckFile = new File(zipFile.getParent(), zipFile.getName() + ".lck");
    if (partFile.exists() || lckFile.exists()) {
      return "Download appears incomplete (.part or .lck file present)";
    }

    // Try to read the zip to verify it's valid
    try (ZipFile zip = new ZipFile(zipFile)) {
      // Just opening it validates the basic structure
      // Check it has some entries
      if (zip.size() == 0) {
        return "Zip file is empty (no entries)";
      }
    } catch (ZipException e) {
      return "Invalid zip format: " + e.getMessage();
    } catch (IOException e) {
      return "Cannot read zip file: " + e.getMessage();
    }

    return null; // No corruption detected
  }

  /**
   * Deletes a specific corrupted distribution.
   * Deletes the entire distribution directory (e.g., gradle-8.13-all/) to ensure
   * Gradle will perform a fresh download.
   *
   * @param distribution the distribution to delete
   * @return true if deletion was successful
   */
  public static boolean deleteCorruptedDistribution(CorruptedDistribution distribution) {
    if (distribution.directory() == null || !distribution.directory().exists()) {
      return true;
    }

    // Find the distribution's root directory (e.g., gradle-8.13-all/)
    // The distribution.directory() might be a hash subdirectory
    File wrapperDists = new File(FileUtils.getUserHome(), ".gradle/wrapper/dists");
    File distRootDir = new File(wrapperDists, distribution.name());

    if (!distRootDir.exists()) {
      // Fall back to the directory in the record
      distRootDir = distribution.directory();
    }

    log.info("Deleting corrupted distribution {} at {}", distribution.name(), distRootDir);

    // Stop daemons first to release file locks
    stopAllDaemons(new File(FileUtils.getUserHome(), ".gradle"));

    // Delete the entire distribution directory for a clean re-download
    final File targetDir = distRootDir;
    try (Stream<Path> walker = Files.walk(targetDir.toPath())) {
      walker.sorted((a, b) -> b.compareTo(a))
          .forEach(path -> {
            try {
              Files.deleteIfExists(path);
            } catch (IOException ex) {
              log.debug("Failed to delete {}", path, ex);
            }
          });
      log.info("Successfully deleted corrupted distribution {}", distribution.name());

      // Also clear version-specific caches that might prevent re-download
      clearVersionCaches(distribution.name());

      return true;
    } catch (IOException e) {
      log.warn("Failed to delete corrupted distribution {}", distribution.name(), e);
      return false;
    }
  }

  /**
   * Clears version-specific caches for a distribution.
   * This helps ensure Gradle will perform a clean re-download.
   *
   * @param distributionName the distribution name (e.g., "gradle-8.13-all")
   */
  private static void clearVersionCaches(String distributionName) {
    // Extract version number from distribution name (e.g., "8.13" from "gradle-8.13-all")
    String version = distributionName.replace("gradle-", "").replaceAll("-(all|bin)$", "");
    File cachesDir = new File(FileUtils.getUserHome(), ".gradle/caches");

    if (!cachesDir.exists()) {
      return;
    }

    // Clear version-specific caches
    File[] versionDirs = cachesDir.listFiles(f -> f.isDirectory() && f.getName().startsWith(version));
    if (versionDirs != null) {
      for (File versionDir : versionDirs) {
        log.debug("Clearing cache directory {}", versionDir);
        try (Stream<Path> walker = Files.walk(versionDir.toPath())) {
          walker.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
            try {
              Files.deleteIfExists(path);
            } catch (IOException ex) {
              log.debug("Failed to delete {}", path, ex);
            }
          });
        } catch (IOException e) {
          log.debug("Failed to clear cache directory {}", versionDir, e);
        }
      }
    }

    // Also clear modules-2/files-2.1 metadata that might reference the distribution
    File modulesDir = new File(cachesDir, "modules-2/metadata-" + version);
    if (modulesDir.exists()) {
      log.debug("Clearing modules metadata {}", modulesDir);
      try (Stream<Path> walker = Files.walk(modulesDir.toPath())) {
        walker.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
          try {
            Files.deleteIfExists(path);
          } catch (IOException ex) {
            log.debug("Failed to delete {}", path, ex);
          }
        });
      } catch (IOException e) {
        log.debug("Failed to clear modules metadata {}", modulesDir, e);
      }
    }
  }

  /**
   * Checks if the given throwable indicates daemon or cache corruption.
   *
   * @param throwable the exception to analyze
   * @return true if the error indicates daemon or cache corruption
   */
  static boolean isDaemonOrCacheCorruption(Throwable throwable) {
    Throwable t = throwable;
    while (t != null) {
      String msg = t.getMessage();
      if (msg != null) {
        // Common daemon/cache corruption indicators
        if (msg.contains("Cannot locate manifest for module")) {
          return true;
        }
        if (msg.contains("Could not create service of type ClassLoaderRegistry")) {
          return true;
        }
        if (msg.contains("Could not create an instance of Tooling API implementation")) {
          return true;
        }
      }
      String className = t.getClass().getName();
      if (className.contains("UnknownModuleException")) {
        return true;
      }
      t = t.getCause();
    }
    return false;
  }

  /**
   * Extracts a human-readable error type from the given throwable.
   *
   * @param throwable the exception to analyze
   * @return a descriptive error type string
   */
  static String extractErrorType(Throwable throwable) {
    Throwable t = throwable;
    while (t != null) {
      String msg = t.getMessage();
      if (msg != null) {
        if (msg.contains("Cannot locate manifest for module")) {
          return "module manifest not found";
        }
        if (msg.contains("Could not create service of type ClassLoaderRegistry")) {
          return "ClassLoaderRegistry creation failed";
        }
        if (msg.contains("Could not create an instance of Tooling API implementation")) {
          return "Tooling API initialization failed";
        }
      }
      String className = t.getClass().getName();
      if (className.contains("UnknownModuleException")) {
        return "unknown module: " + msg;
      }
      t = t.getCause();
    }
    return "unknown corruption";
  }

  /**
   * Checks if the distribution cache should be purged based on the error.
   *
   * @param throwable the exception to analyze
   * @return true if the distribution cache should be purged
   */
  static boolean shouldPurgeDistributionCache(Throwable throwable) {
    Throwable t = throwable;
    while (t != null) {
      String msg = t.getMessage();
      if (msg != null && msg.contains("gradle-runtime-api-info")) {
        return true;
      }
      if (t.getClass().getName().contains("UnknownModuleException")) {
        return true;
      }
      t = t.getCause();
    }
    return false;
  }

  /**
   * Clears the Gradle daemon cache by stopping all daemons and removing cache files.
   * <p>
   * This method:
   * <ol>
   *   <li>Stops all running Gradle daemons using the gradle --stop command</li>
   *   <li>Removes all files from the daemon directory</li>
   *   <li>Clears version-specific script caches</li>
   * </ol>
   */
  void clearDaemonCache() {
    File gradleHome = configManager.isUsingCustomGradleUserHome()
        ? configManager.getGradleUserHomeDir()
        : new File(FileUtils.getUserHome(), ".gradle");

    File daemonDir = new File(gradleHome, "daemon");
    File cachesDir = new File(gradleHome, "caches");

    log.info("Clearing Gradle daemon cache at {}", gradleHome);

    // Stop all Gradle daemons
    stopDaemons();

    // Clear daemon directory
    clearDaemonDirectory(daemonDir);

    // Clear version-specific caches that might be corrupted
    clearVersionCaches(cachesDir);
  }

  private void stopDaemons() {
    try {
      ProcessBuilder pb = new ProcessBuilder();
      String gradleCommand = findGradleCommand();
      if (gradleCommand != null) {
        pb.command(gradleCommand, "--stop");
        pb.directory(projectDir);
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode == 0) {
          log.info("Successfully stopped Gradle daemons");
        } else {
          log.warn("Gradle --stop command exited with code {}", exitCode);
        }
      } else {
        log.debug("No gradle command found, skipping daemon stop");
      }
    } catch (Exception e) {
      log.warn("Failed to stop Gradle daemons, proceeding with cache cleanup", e);
    }
  }

  private void clearDaemonDirectory(File daemonDir) {
    if (daemonDir.exists()) {
      try (Stream<Path> walker = Files.walk(daemonDir.toPath())) {
        walker.sorted((a, b) -> b.compareTo(a))
            .filter(path -> !path.equals(daemonDir.toPath()))
            .forEach(path -> {
              try {
                Files.deleteIfExists(path);
              } catch (IOException ex) {
                log.debug("Failed to delete {}", path, ex);
              }
            });
        log.info("Cleared Gradle daemon cache at {}", daemonDir);
      } catch (IOException e) {
        log.warn("Failed to clear daemon cache at {}", daemonDir, e);
      }
    }
  }

  private void clearVersionCaches(File cachesDir) {
    if (cachesDir.exists()) {
      try {
        File[] versionDirs = cachesDir.listFiles(f -> f.isDirectory() && f.getName().matches("\\d+\\.\\d+.*"));
        if (versionDirs != null) {
          for (File versionDir : versionDirs) {
            File[] subdirs = versionDir.listFiles(f -> f.isDirectory() &&
                (f.getName().equals("scripts") || f.getName().equals("scripts-remapped")));
            if (subdirs != null) {
              for (File subdir : subdirs) {
                try (Stream<Path> walker = Files.walk(subdir.toPath())) {
                  walker.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try {
                      Files.deleteIfExists(path);
                    } catch (IOException ex) {
                      log.debug("Failed to delete {}", path, ex);
                    }
                  });
                  log.debug("Cleared cache directory {}", subdir);
                } catch (IOException e) {
                  log.debug("Failed to clear cache directory {}", subdir, e);
                }
              }
            }
          }
        }
      } catch (Exception e) {
        log.warn("Failed to clear version-specific caches", e);
      }
    }
  }

  /**
   * Finds the gradle command to use for stopping daemons.
   *
   * @return the path to the gradle command, or null if not found
   */
  String findGradleCommand() {
    // Check for gradle wrapper first
    File gradlew = new File(projectDir, "gradlew");
    if (gradlew.exists() && gradlew.canExecute()) {
      return gradlew.getAbsolutePath();
    }

    // Check for gradle in PATH
    String gradleHome = System.getenv("GRADLE_HOME");
    if (gradleHome != null) {
      File gradle = new File(gradleHome, "bin/gradle");
      if (gradle.exists()) {
        return gradle.getAbsolutePath();
      }
    }

    // Try to find gradle on PATH
    String path = System.getenv("PATH");
    if (path != null) {
      for (String dir : path.split(File.pathSeparator)) {
        File gradle = new File(dir, "gradle");
        if (gradle.exists() && gradle.canExecute()) {
          return gradle.getAbsolutePath();
        }
      }
    }

    return null;
  }

  /**
   * Purges corrupted wrapper distributions from the Gradle user home.
   * <p>
   * This method removes all cached Gradle distributions from ~/.gradle/wrapper/dists/
   * which can become corrupted during interrupted downloads or other failures.
   * After purging, Gradle will re-download distributions as needed.
   *
   * @return true if the purge was successful or cache was empty
   */
  public static boolean purgeWrapperDistributions() {
    File gradleHome = new File(FileUtils.getUserHome(), ".gradle");
    File wrapperDists = new File(gradleHome, "wrapper/dists");

    if (!wrapperDists.exists()) {
      log.info("No wrapper distributions cache found at {}", wrapperDists);
      return true;
    }

    log.info("Purging Gradle wrapper distributions at {}", wrapperDists);

    // Stop daemons first to release any locks
    stopAllDaemons(gradleHome);

    try (Stream<Path> walker = Files.walk(wrapperDists.toPath())) {
      walker.sorted((a, b) -> b.compareTo(a))
          .filter(path -> !path.equals(wrapperDists.toPath()))
          .forEach(path -> {
            try {
              Files.deleteIfExists(path);
            } catch (IOException ex) {
              log.debug("Failed to delete {}", path, ex);
            }
          });
      log.info("Successfully purged Gradle wrapper distributions");
      return true;
    } catch (IOException e) {
      log.warn("Failed to purge wrapper distributions at {}", wrapperDists, e);
      return false;
    }
  }

  private static void stopAllDaemons(File gradleHome) {
    // Try to stop daemons using any gradle wrapper or system gradle
    String gradleCommand = findSystemGradle();
    if (gradleCommand == null) {
      log.debug("No system gradle found, skipping daemon stop");
      return;
    }
    try {
      ProcessBuilder pb = new ProcessBuilder(gradleCommand, "--stop");
      pb.directory(gradleHome);
      Process process = pb.start();
      int exitCode = process.waitFor();
      if (exitCode == 0) {
        log.info("Successfully stopped Gradle daemons");
      } else {
        log.debug("Gradle --stop exited with code {}", exitCode);
      }
    } catch (Exception e) {
      log.debug("Failed to stop Gradle daemons", e);
    }
  }

  private static String findSystemGradle() {
    String gradleHome = System.getenv("GRADLE_HOME");
    if (gradleHome != null) {
      File gradle = new File(gradleHome, "bin/gradle");
      if (gradle.exists()) {
        return gradle.getAbsolutePath();
      }
    }
    String path = System.getenv("PATH");
    if (path != null) {
      for (String dir : path.split(File.pathSeparator)) {
        File gradle = new File(dir, "gradle");
        if (gradle.exists() && gradle.canExecute()) {
          return gradle.getAbsolutePath();
        }
      }
    }
    return null;
  }

  /**
   * Purges the project-local Gradle user home cache.
   * <p>
   * This method will only purge the project-local Gradle user home directory
   * (the one automatically created by Gade). It will not purge user-specified
   * custom Gradle user homes or the default ~/.gradle directory.
   *
   * @param distributionManager the distribution manager to update after purging
   */
  void purgeGradleUserHomeCache(GradleDistributionManager distributionManager) {
    if (!configManager.isUsingCustomGradleUserHome() || configManager.getGradleUserHomeDir() == null) {
      log.warn("Skipping purge of Gradle user home because a shared/default location is in use");
      return;
    }
    if (!configManager.isProjectLocalGradleUserHome(projectLocalGradleUserHome)) {
      log.warn("Skipping purge of Gradle user home {} because it is not project-local",
          configManager.getGradleUserHomeDir());
      return;
    }
    if (!projectLocalGradleUserHome.exists()) {
      return;
    }

    // Switch to default user home before purging so we have a better chance of succeeding without downloads.
    configManager.switchToDefaultGradleUserHome();
    distributionManager.applyCurrentDistribution();

    Path backup = projectLocalGradleUserHome.toPath()
        .resolveSibling(projectLocalGradleUserHome.getName() + "-backup");
    try {
      if (Files.exists(backup)) {
        try (Stream<Path> backupWalker = Files.walk(backup)) {
          backupWalker.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
            try {
              Files.deleteIfExists(path);
            } catch (IOException ex) {
              log.warn("Failed to delete {}", path, ex);
            }
          });
        }
      }
      Files.move(projectLocalGradleUserHome.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
      log.warn("Purged corrupted Gradle user home {}, backup kept at {}", projectLocalGradleUserHome, backup);
      Files.createDirectories(projectLocalGradleUserHome.toPath());
    } catch (IOException ex) {
      log.warn("Failed to purge Gradle user home {}, proceeding with deletion attempt",
          projectLocalGradleUserHome, ex);
      try (Stream<Path> walker = Files.walk(projectLocalGradleUserHome.toPath())) {
        walker.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
          try {
            Files.deleteIfExists(path);
          } catch (IOException e) {
            log.warn("Failed to delete {}", path, e);
          }
        });
        Files.createDirectories(projectLocalGradleUserHome.toPath());
      } catch (IOException walkEx) {
        log.warn("Failed to delete Gradle user home {}", projectLocalGradleUserHome, walkEx);
      }
    }
  }
}
