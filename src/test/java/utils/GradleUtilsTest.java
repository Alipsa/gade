package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.model.Dependency;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.gade.utils.gradle.GradleUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

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

    //System.out.println("Task names:" + gradleUtil.getGradleTaskNames());

    var depNames = gradleUtil.getProjectDependencyNames();
    assertTrue(depNames.contains("commons-math3-3.6.1.jar"), "Failed to find dependency commons-math3-3.6.1.jar, was: " + depNames);
    assertTrue(depNames.contains("guava-31.0.1-jre.jar"), "Failed to find dependency guava-31.0.1-jre.jar, was: " + depNames);

    var dependencies = gradleUtil.getProjectDependencies();
    //dependencies.forEach(d -> System.out.println(d.getAbsolutePath()));
    assertNotNull(dependencies.stream().filter(f -> f.getName().equals("commons-math3-3.6.1.jar")).findAny().orElse(null), "Failed to find dependency commons-math3-3.6.1.jar");
    assertNotNull(dependencies.stream().filter(f -> f.getName().equals("guava-31.0.1-jre.jar")).findAny().orElse(null), "Failed to find dependency guava-31.0.1-jre.jar");
  }

  @Test
  public void testDownloadArtifact() throws IOException {
    Dependency dependency = new Dependency("org.slf4j:slf4j-api:1.7.36");
    File artifactDir = GradleUtils.cachedFile(dependency);
    if (artifactDir.exists()) {
      log.info("Deleting files in {} to ensure remote download works", artifactDir.getAbsolutePath());
      GradleUtils.purgeCache(dependency);
    } else {
      log.info("{} does not exist: no problem, we are going to fetch it!", artifactDir.getAbsolutePath());
    }
    File file = GradleUtils.downloadArtifact(dependency);
    assertTrue(file.exists(), "File does not exist");
    assertEquals("slf4j-api-1.7.36.jar", file.getName(), "File name is wrong");
  }
}
