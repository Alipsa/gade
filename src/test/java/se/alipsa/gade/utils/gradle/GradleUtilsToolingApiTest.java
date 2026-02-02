package se.alipsa.gade.utils.gradle;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.idea.IdeaProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to reproduce the Gradle Tooling API issue with Gradle 9.0+
 */
public class GradleUtilsToolingApiTest {

  @Test
  public void testToolingApiWithGradle900(@TempDir Path tempDir) throws Exception {
    // Create a minimal Gradle project
    File projectDir = tempDir.toFile();

    // Create build.gradle
    Files.writeString(tempDir.resolve("build.gradle"),
        "plugins { id 'java' }\ngroup = 'test'\nversion = '1.0'");

    // Create settings.gradle
    Files.writeString(tempDir.resolve("settings.gradle"),
        "rootProject.name = 'test-project'");

    // Create gradle wrapper properties
    Files.createDirectories(tempDir.resolve("gradle/wrapper"));
    Files.writeString(tempDir.resolve("gradle/wrapper/gradle-wrapper.properties"),
        "distributionBase=GRADLE_USER_HOME\n" +
        "distributionPath=wrapper/dists\n" +
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.0.0-bin.zip\n" +
        "zipStoreBase=GRADLE_USER_HOME\n" +
        "zipStorePath=wrapper/dists\n");

    // Try to connect using Tooling API
    GradleConnector connector = GradleConnector.newConnector();
    connector.forProjectDirectory(projectDir);
    connector.useBuildDistribution();

    try (ProjectConnection connection = connector.connect()) {
      // This should trigger the same error we're seeing in Gade
      IdeaProject project = connection.model(IdeaProject.class)
          .setJvmArguments("-Dorg.gradle.java.home=" + System.getProperty("java.home"))
          .get();

      assertNotNull(project, "Should be able to fetch IdeaProject model");
      System.out.println("Successfully fetched IdeaProject: " + project.getName());
    }
  }

  @Test
  public void testToolingApiWithoutJavaHomeArg(@TempDir Path tempDir) throws Exception {
    // Create a minimal Gradle project
    File projectDir = tempDir.toFile();

    Files.writeString(tempDir.resolve("build.gradle"),
        "plugins { id 'java' }\ngroup = 'test'\nversion = '1.0'");

    Files.writeString(tempDir.resolve("settings.gradle"),
        "rootProject.name = 'test-project'");

    Files.createDirectories(tempDir.resolve("gradle/wrapper"));
    Files.writeString(tempDir.resolve("gradle/wrapper/gradle-wrapper.properties"),
        "distributionBase=GRADLE_USER_HOME\n" +
        "distributionPath=wrapper/dists\n" +
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.0.0-bin.zip\n" +
        "zipStoreBase=GRADLE_USER_HOME\n" +
        "zipStorePath=wrapper/dists\n");

    GradleConnector connector = GradleConnector.newConnector();
    connector.forProjectDirectory(projectDir);
    connector.useBuildDistribution();

    try (ProjectConnection connection = connector.connect()) {
      // Try WITHOUT the -Dorg.gradle.java.home argument
      IdeaProject project = connection.model(IdeaProject.class).get();

      assertNotNull(project, "Should be able to fetch IdeaProject model without java.home arg");
      System.out.println("Successfully fetched IdeaProject: " + project.getName());
    }
  }
}
