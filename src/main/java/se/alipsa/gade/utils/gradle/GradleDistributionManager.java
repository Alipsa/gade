package se.alipsa.gade.utils.gradle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gradle.tooling.GradleConnector;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages Gradle distribution selection and fallback strategies.
 * <p>
 * This class determines which Gradle distribution to use based on:
 * <ul>
 *   <li>Explicit installation directory (if provided)</li>
 *   <li>Gradle wrapper (if available in the project)</li>
 *   <li>Embedded/tooling API version (as fallback)</li>
 * </ul>
 * <p>
 * It also handles fallback logic when a distribution fails to connect.
 *
 * @see GradleUtils
 */
final class GradleDistributionManager {

  private static final Logger log = LogManager.getLogger(GradleDistributionManager.class);

  /**
   * Enum representing the different Gradle distribution modes.
   */
  enum DistributionMode {
    /** Use an explicitly configured Gradle installation directory */
    INSTALLATION,
    /** Use the Gradle wrapper from the project */
    WRAPPER,
    /** Use the embedded Gradle version from the Tooling API */
    EMBEDDED
  }

  private final File projectDir;
  private final File gradleInstallationDir;
  private final boolean wrapperAvailable;
  private final List<DistributionMode> distributionOrder = new ArrayList<>();
  private final GradleConfigurationManager configManager;

  private int currentDistributionIndex = 0;
  private GradleConnector connector;

  /**
   * Creates a new distribution manager.
   *
   * @param projectDir the Gradle project directory
   * @param gradleInstallationDir optional explicit Gradle installation directory
   * @param wrapperAvailable whether the project has a Gradle wrapper
   * @param configManager the configuration manager for accessing Gradle settings
   */
  GradleDistributionManager(File projectDir, File gradleInstallationDir,
                            boolean wrapperAvailable, GradleConfigurationManager configManager) {
    this.projectDir = projectDir;
    this.gradleInstallationDir = gradleInstallationDir;
    this.wrapperAvailable = wrapperAvailable;
    this.configManager = configManager;
    configureDistribution();
  }

  /**
   * Configures the distribution order based on available options.
   * <p>
   * The order of preference is:
   * <ol>
   *   <li>Explicit installation (if provided and exists)</li>
   *   <li>Gradle wrapper (if available)</li>
   *   <li>Embedded/tooling API version</li>
   * </ol>
   */
  void configureDistribution() {
    distributionOrder.clear();
    // Prefer provided installation if explicitly given, then wrapper, then embedded/tooling API.
    if (gradleInstallationDir != null && gradleInstallationDir.exists()) {
      distributionOrder.add(DistributionMode.INSTALLATION);
    }
    if (wrapperAvailable) {
      distributionOrder.add(DistributionMode.WRAPPER);
    }
    distributionOrder.add(DistributionMode.EMBEDDED);
    currentDistributionIndex = 0;
    applyCurrentDistribution();
  }

  /**
   * Applies the current distribution to the connector.
   * <p>
   * This creates a new connector and configures it with the appropriate
   * distribution based on the current distribution mode.
   */
  void applyCurrentDistribution() {
    File gradleUserHomeDir = configManager.getGradleUserHomeDir();
    boolean useCustom = configManager.isUsingCustomGradleUserHome();

    if (useCustom && gradleUserHomeDir != null && !gradleUserHomeDir.exists() && !gradleUserHomeDir.mkdirs()) {
      log.warn("Failed to recreate Gradle user home {}", gradleUserHomeDir);
    }

    connector = GradleConnector.newConnector();
    connector.forProjectDirectory(projectDir);

    if (useCustom && gradleUserHomeDir != null) {
      connector.useGradleUserHomeDir(gradleUserHomeDir);
    }

    DistributionMode mode = distributionOrder.get(currentDistributionIndex);
    if (useCustom && gradleUserHomeDir != null) {
      log.info("Using Gradle user home {}", gradleUserHomeDir.getAbsolutePath());
    } else {
      log.info("Using Gradle user home ~/.gradle");
    }

    switch (mode) {
      case INSTALLATION -> {
        connector.useInstallation(gradleInstallationDir);
        log.info("Using Gradle installation at {}", gradleInstallationDir);
      }
      case WRAPPER -> {
        connector.useBuildDistribution();
        log.info("Using Gradle wrapper distribution for {}", projectDir);
      }
      case EMBEDDED -> {
        String version = GradleVersion.current().getVersion();
        connector.useGradleVersion(version);
        log.info("Using embedded/tooling API Gradle version {} for {}", version, projectDir);
      }
    }
  }

  /**
   * Checks if the error indicates a distribution installation failure.
   *
   * @param throwable the exception to check
   * @return true if the error indicates a distribution installation failure
   */
  static boolean isDistributionInstallFailure(Throwable throwable) {
    Throwable t = throwable;
    while (t != null) {
      String msg = t.getMessage();
      if (msg != null && msg.contains("Could not install Gradle distribution")) {
        return true;
      }
      t = t.getCause();
    }
    return false;
  }

  /**
   * Tries to advance to the next distribution in the fallback order.
   *
   * @return true if there was another distribution to try, false if all exhausted
   */
  boolean tryNextDistribution() {
    if (currentDistributionIndex < distributionOrder.size() - 1) {
      currentDistributionIndex++;
      log.warn("Retrying with fallback distribution {}", distributionOrder.get(currentDistributionIndex));
      applyCurrentDistribution();
      return true;
    }
    return false;
  }

  /**
   * Resets the distribution index and reapplies the first distribution.
   */
  void reset() {
    currentDistributionIndex = 0;
    applyCurrentDistribution();
  }

  /**
   * Returns the current Gradle connector.
   *
   * @return the configured GradleConnector
   */
  GradleConnector getConnector() {
    return connector;
  }

  /**
   * Returns the current distribution mode.
   *
   * @return the current DistributionMode
   */
  DistributionMode getCurrentMode() {
    return distributionOrder.get(currentDistributionIndex);
  }

  /**
   * Returns an immutable copy of the distribution order.
   *
   * @return list of distribution modes in order of preference
   */
  List<DistributionMode> getDistributionOrder() {
    return List.copyOf(distributionOrder);
  }
}
