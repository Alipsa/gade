package se.alipsa.gade.utils.gradle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

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
final class GradleDaemonRecovery {

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
