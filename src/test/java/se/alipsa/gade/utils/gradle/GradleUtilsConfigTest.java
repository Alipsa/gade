package se.alipsa.gade.utils.gradle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.utils.FileUtils;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class GradleUtilsConfigTest {

  private static final String USER_HOME_PROP = "gade.gradle.userHome";

  @AfterEach
  void restoreSystemProperties() {
    System.clearProperty(USER_HOME_PROP);
  }

  @Test
  void defaultsToSystemGradleUserHome() throws Exception {
    File gradleProjectDir = FileUtils.getResource("utils/gradle/package");
    GradleUtils gradleUtils = new GradleUtils(null, gradleProjectDir, System.getProperty("java.home"));
    assertNull(gradleUtils.getGradleUserHomeDir(), "Expected GradleUtils to use default ~/.gradle user home by default");
  }

  @Test
  void prefersWrapperWhenAvailable() throws Exception {
    File gradleProjectDir = FileUtils.getResource("utils/gradle/package");
    GradleUtils gradleUtils = new GradleUtils(null, gradleProjectDir, System.getProperty("java.home"));
    assertEquals(
        List.of(GradleUtils.DistributionMode.WRAPPER, GradleUtils.DistributionMode.EMBEDDED),
        gradleUtils.getDistributionOrder(),
        "Expected wrapper to be preferred over embedded/tooling API distribution when available"
    );
  }

  @Test
  void allowsProjectLocalGradleUserHomeViaProperty() throws Exception {
    System.setProperty(USER_HOME_PROP, "project");
    File gradleProjectDir = FileUtils.getResource("utils/gradle/package");
    GradleUtils gradleUtils = new GradleUtils(null, gradleProjectDir, System.getProperty("java.home"));
    assertEquals(
        new File(gradleProjectDir, ".gradle-gade-tooling").getAbsolutePath(),
        gradleUtils.getGradleUserHomeDir().getAbsolutePath(),
        "Expected GradleUtils to use project-local Gradle user home when configured"
    );
  }
}

