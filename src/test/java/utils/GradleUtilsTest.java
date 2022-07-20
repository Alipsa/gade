package utils;

import org.junit.jupiter.api.Test;
import se.alipsa.grade.utils.FileUtils;
import se.alipsa.grade.utils.gradle.GradleUtils;

import java.io.File;
import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

public class GradleUtilsTest {

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
}
