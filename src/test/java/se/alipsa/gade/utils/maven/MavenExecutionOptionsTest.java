package se.alipsa.gade.utils.maven;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.alipsa.mavenutils.MavenUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenExecutionOptionsTest {

  @AfterEach
  void clearEmbeddedMavenOverride() {
    System.clearProperty(MavenDistributionLocator.EMBEDDED_MAVEN_HOME_PROP);
  }

  @Test
  void executionOptionsUseConfiguredMavenHomeWhenProvided(@TempDir Path tempDir) {
    File configuredHome = tempDir.resolve("configured-maven").toFile();
    MavenUtils.MavenExecutionOptions options = MavenClasspathUtils.createExecutionOptions(
        tempDir.toFile(),
        configuredHome.getAbsolutePath()
    );
    assertEquals(configuredHome.getAbsolutePath(), options.getConfiguredMavenHome().getAbsolutePath());
    assertEquals(tempDir.toFile().getAbsolutePath(), options.getProjectDir().getAbsolutePath());
    assertTrue(options.isPreferWrapper(), "Expected wrapper to remain preferred");
  }

  @Test
  void executionOptionsUseEmbeddedMavenHomeOverrideWhenConfiguredHomeMissing(@TempDir Path tempDir) throws Exception {
    Path embeddedMaven = tempDir.resolve("embedded-maven");
    Files.createDirectories(embeddedMaven.resolve("bin"));
    Files.writeString(embeddedMaven.resolve("bin").resolve("mvn"), "#!/bin/sh\necho Maven\n");
    System.setProperty(MavenDistributionLocator.EMBEDDED_MAVEN_HOME_PROP, embeddedMaven.toString());

    MavenUtils.MavenExecutionOptions options = MavenClasspathUtils.createExecutionOptions(tempDir.toFile(), null);
    assertEquals(embeddedMaven.toFile().getAbsolutePath(), options.getConfiguredMavenHome().getAbsolutePath());
  }

  @Test
  void embeddedLocatorResolvesOverridePath(@TempDir Path tempDir) throws Exception {
    Path embeddedMaven = tempDir.resolve("bundled-maven");
    Files.createDirectories(embeddedMaven.resolve("bin"));
    Files.writeString(embeddedMaven.resolve("bin").resolve("mvn"), "#!/bin/sh\necho Maven\n");
    System.setProperty(MavenDistributionLocator.EMBEDDED_MAVEN_HOME_PROP, embeddedMaven.toString());

    assertEquals(embeddedMaven.toFile().getAbsolutePath(),
        MavenDistributionLocator.resolveBundledMavenHome().getAbsolutePath());
  }
}
