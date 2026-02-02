package se.alipsa.gade.utils.gradle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages Gradle configuration settings including Java home, JVM arguments,
 * environment variables, and Gradle user home directory.
 * <p>
 * This class handles:
 * <ul>
 *   <li>Resolution of the Gradle user home directory (custom vs default)</li>
 *   <li>JVM arguments for Gradle operations</li>
 *   <li>Environment variables for Gradle processes</li>
 *   <li>Java home resolution with fallback logic</li>
 * </ul>
 *
 * @see GradleUtils
 */
final class GradleConfigurationManager {

  private static final Logger log = LogManager.getLogger(GradleConfigurationManager.class);

  /** Environment variable name for Gade Gradle user home */
  static final String GADE_GRADLE_USER_HOME_ENV = "GADE_GRADLE_USER_HOME";

  /** System property name for Gade Gradle user home */
  static final String GADE_GRADLE_USER_HOME_PROP = "gade.gradle.userHome";

  private final File projectDir;
  private final String javaHomeOverride;

  private File gradleUserHomeDir;
  private boolean useCustomGradleUserHome;

  /**
   * Creates a new configuration manager.
   *
   * @param projectDir the Gradle project directory
   * @param projectLocalGradleUserHome the project-local Gradle user home directory
   * @param javaHomeOverride optional Java home override
   */
  GradleConfigurationManager(File projectDir, File projectLocalGradleUserHome, String javaHomeOverride) {
    this.projectDir = projectDir;
    this.javaHomeOverride = javaHomeOverride;
    this.gradleUserHomeDir = resolveGradleUserHomeDir(projectDir, projectLocalGradleUserHome);
    this.useCustomGradleUserHome = gradleUserHomeDir != null;
    log.debug("Gradle user home set to {} (custom={})",
        useCustomGradleUserHome ? gradleUserHomeDir.getAbsolutePath() : "~/.gradle", useCustomGradleUserHome);
  }

  /**
   * Resolves the Gradle user home directory based on system properties and environment.
   * <p>
   * Resolution order:
   * <ol>
   *   <li>System property {@code gade.gradle.userHome}</li>
   *   <li>Environment variable {@code GADE_GRADLE_USER_HOME}</li>
   *   <li>Default to null (use ~/.gradle)</li>
   * </ol>
   * <p>
   * Special values:
   * <ul>
   *   <li>{@code default} - Use ~/.gradle</li>
   *   <li>{@code project} or {@code project-local} - Use project-local directory</li>
   *   <li>Absolute or relative path - Use specified directory</li>
   * </ul>
   *
   * @param projectDir the project directory
   * @param projectLocalGradleUserHome the project-local Gradle user home
   * @return the resolved Gradle user home directory, or null for default
   */
  static File resolveGradleUserHomeDir(File projectDir, File projectLocalGradleUserHome) {
    String configured = System.getProperty(GADE_GRADLE_USER_HOME_PROP);
    if (configured == null || configured.isBlank()) {
      configured = System.getenv(GADE_GRADLE_USER_HOME_ENV);
    }
    if (configured == null || configured.isBlank() || "default".equalsIgnoreCase(configured)) {
      return null;
    }
    final File userHomeDir;
    if ("project".equalsIgnoreCase(configured) || "project-local".equalsIgnoreCase(configured)) {
      userHomeDir = projectLocalGradleUserHome;
    } else {
      File configuredDir = new File(configured);
      userHomeDir = configuredDir.isAbsolute() ? configuredDir : new File(projectDir, configured);
    }
    if (userHomeDir.exists()) {
      return userHomeDir;
    }
    if (userHomeDir.mkdirs()) {
      return userHomeDir;
    }
    log.warn("Failed to create Gradle user home {} ({}={}, {}={}), falling back to default ~/.gradle",
        userHomeDir,
        GADE_GRADLE_USER_HOME_PROP, System.getProperty(GADE_GRADLE_USER_HOME_PROP),
        GADE_GRADLE_USER_HOME_ENV, System.getenv(GADE_GRADLE_USER_HOME_ENV));
    return null;
  }

  /**
   * Returns the JVM arguments for Gradle operations.
   *
   * @return array of JVM arguments
   */
  String[] gradleJvmArgs() {
    List<String> args = new ArrayList<>();
    String javaHome = pickJavaHome();
    args.add("-Dorg.gradle.java.home=" + javaHome);
    args.add("-Dorg.gradle.ignoreInitScripts=true");
    if (useCustomGradleUserHome && gradleUserHomeDir != null) {
      args.add("-Dgradle.user.home=" + gradleUserHomeDir.getAbsolutePath());
    }
    return args.toArray(new String[0]);
  }

  /**
   * Returns the environment variables for Gradle processes.
   *
   * @return map of environment variables
   */
  Map<String, String> gradleEnv() {
    Map<String, String> env = new HashMap<>(System.getenv());
    String javaHome = pickJavaHome();
    env.put("JAVA_HOME", javaHome);
    if (useCustomGradleUserHome && gradleUserHomeDir != null) {
      env.put("GRADLE_USER_HOME", gradleUserHomeDir.getAbsolutePath());
    }
    return env;
  }

  /**
   * Resolves the Java home to use for Gradle operations.
   * <p>
   * Resolution order:
   * <ol>
   *   <li>Explicit override (from constructor)</li>
   *   <li>JAVA_HOME environment variable</li>
   *   <li>java.home system property</li>
   * </ol>
   *
   * @return the resolved Java home path, or empty string if not found
   */
  String pickJavaHome() {
    String javaHome = javaHomeOverride;
    if (javaHome == null || javaHome.isBlank()) {
      javaHome = System.getenv("JAVA_HOME");
    }
    if (javaHome == null || javaHome.isBlank()) {
      javaHome = System.getProperty("java.home");
    }
    if (javaHome != null && !javaHome.isBlank() && !new File(javaHome).exists()) {
      log.warn("Configured JAVA_HOME {} does not exist, falling back to system default", javaHome);
      javaHome = System.getProperty("java.home", "");
    }
    if (javaHome == null || javaHome.isBlank()) {
      javaHome = "";
    }
    return javaHome;
  }

  /**
   * Returns the current Gradle user home directory.
   *
   * @return the Gradle user home directory, or null if using default
   */
  File getGradleUserHomeDir() {
    return gradleUserHomeDir;
  }

  /**
   * Checks if a custom Gradle user home is being used.
   *
   * @return true if using a custom Gradle user home
   */
  boolean isUsingCustomGradleUserHome() {
    return useCustomGradleUserHome;
  }

  /**
   * Checks if the current Gradle user home is the project-local one.
   *
   * @param projectLocalGradleUserHome the project-local Gradle user home to compare
   * @return true if using the project-local Gradle user home
   */
  boolean isProjectLocalGradleUserHome(File projectLocalGradleUserHome) {
    return gradleUserHomeDir != null &&
        projectLocalGradleUserHome.getAbsolutePath().equals(gradleUserHomeDir.getAbsolutePath());
  }

  /**
   * Switches to using the default Gradle user home (~/.gradle).
   */
  void switchToDefaultGradleUserHome() {
    useCustomGradleUserHome = false;
    gradleUserHomeDir = null;
  }

  /**
   * Updates the Gradle user home to the specified directory.
   *
   * @param gradleUserHomeDir the new Gradle user home directory
   */
  void setGradleUserHomeDir(File gradleUserHomeDir) {
    this.gradleUserHomeDir = gradleUserHomeDir;
    this.useCustomGradleUserHome = gradleUserHomeDir != null;
  }
}
