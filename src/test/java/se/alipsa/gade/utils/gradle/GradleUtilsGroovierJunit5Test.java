package se.alipsa.gade.utils.gradle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.idea.IdeaProject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to reproduce the issue with the groovier-junit5 project specifically
 */
public class GradleUtilsGroovierJunit5Test {

  private static final Logger log = LogManager.getLogger(GradleUtilsGroovierJunit5Test.class);

  @Test
  public void testGroovierJunit5Project() throws Exception {
    File projectDir = new File("/Users/pernyf/project/groovier-junit5");
    if (!projectDir.exists()) {
      log.info("Skipping test - project directory does not exist");
      return;
    }

    String javaHome = System.getProperty("java.home");
    log.info("Using Java home: {}", javaHome);

    GradleConnector connector = GradleConnector.newConnector();
    connector.forProjectDirectory(projectDir);
    connector.useBuildDistribution();

    try (ProjectConnection connection = connector.connect()) {
      // Replicate exactly what GradleUtils does
      String[] jvmArgs = new String[] {
          "-Dorg.gradle.java.home=" + javaHome,
          "-Dorg.gradle.ignoreInitScripts=true"
      };

      Map<String, String> env = new HashMap<>(System.getenv());
      env.put("JAVA_HOME", javaHome);

      log.info("Fetching IdeaProject with JVM args: {}", String.join(", ", jvmArgs));

      IdeaProject project = connection.model(IdeaProject.class)
          .setJvmArguments(jvmArgs)
          .setEnvironmentVariables(env)
          .get();

      assertNotNull(project, "Should be able to fetch IdeaProject model");
      log.info("Successfully fetched IdeaProject: {}", project.getName());
      log.info("Modules: {}", project.getModules().size());
    }
  }

  @Test
  public void testGroovierJunit5WithGradleUtils() throws Exception {
    File projectDir = new File("/Users/pernyf/project/groovier-junit5");
    if (!projectDir.exists()) {
      log.info("Skipping test - project directory does not exist");
      return;
    }

    GradleUtils utils = new GradleUtils(null, projectDir, null);
    log.info("Created GradleUtils for project: {}", projectDir);

    // This should trigger the same code path as Gade
    var deps = utils.getProjectDependencies();
    log.info("Successfully resolved {} dependencies", deps.size());
    assertNotNull(deps);
  }
}
