package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.junit.jupiter.api.Test;
import se.alipsa.grade.utils.FileUtils;
import se.alipsa.grade.utils.gradle.GradleUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class GradleUtilsTest {

  private static final Logger log = LogManager.getLogger();

  @Test
  public void testDependencies() throws FileNotFoundException {
    if (System.getenv("GRADLE_HOME") == null) {
      fail("GRADLE_HOME is not set cannot continue test");
    }
    var gradleInstallationDir = new File(System.getenv("GRADLE_HOME"));
    var gradleProjectDir = FileUtils.getResource("utils/gradle/package");

    var gradleUtil = new GradleUtils(gradleInstallationDir, gradleProjectDir);

    var depNames = gradleUtil.getProjectDependencyNames();
    assertTrue(depNames.contains("commons-math3-3.6.1.jar"), "Failed to find dependency commons-math3-3.6.1.jar");
    assertTrue(depNames.contains("guava-31.0.1-jre.jar"), "Failed to find dependency guava-31.0.1-jre.jar");

    var dependencies = gradleUtil.getProjectDependencies();
    assertNotNull(dependencies.stream().filter(f -> f.getName().equals("commons-math3-3.6.1.jar")).findAny().orElse(null), "Failed to find dependency commons-math3-3.6.1.jar");
    assertNotNull(dependencies.stream().filter(f -> f.getName().equals("guava-31.0.1-jre.jar")).findAny().orElse(null), "Failed to find dependency guava-31.0.1-jre.jar");
  }

  @Test
  public void testDownloadArtifact() throws SettingsBuildingException, ArtifactResolutionException {
    File localRepo = GradleUtils.getLocalRepository().getBasedir();
    assertNotNull(localRepo);
    File artifactDir = new File(localRepo, "org/slf4j/slf4j-api/1.7.32");
    if (artifactDir.exists()) {
      log.info("Deleting files in {} to ensure remote download works", artifactDir.getAbsolutePath());
      for (File file : Objects.requireNonNull(artifactDir.listFiles())) {
        if ("slf4j-api-1.7.32.jar".equals(file.getName()) || "slf4j-api-1.7.32.jar.sha1".equals(file.getName())) {
          log.info("Deleting {}", file.getName());
          assertTrue(file.delete(), "Failed to delete " + file.getAbsolutePath());
        }
      }
    } else {
      log.info("{} does not exist: no problem, we are going to fetch it!", artifactDir.getAbsolutePath());
    }
    File file = GradleUtils.resolveArtifact("org.slf4j", "slf4j-api", null, "jar", "1.7.32");
    assertTrue(file.exists(), "File does not exist");
    assertEquals("slf4j-api-1.7.32.jar", file.getName(), "File name is wrong");
  }
}
