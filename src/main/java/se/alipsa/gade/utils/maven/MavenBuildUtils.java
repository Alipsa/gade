package se.alipsa.gade.utils.maven;

import static se.alipsa.gade.menu.GlobalOptions.RESTART_SESSION_AFTER_GRADLE_RUN;

import javafx.application.Platform;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.shared.invoker.MavenInvocationException;
import se.alipsa.gade.Gade;
import se.alipsa.gade.TaskListener;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.mavenutils.MavenUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class MavenBuildUtils {

  private static final Logger log = LogManager.getLogger(MavenBuildUtils.class);

  private final File projectDir;
  private final String javaHomeOverride;
  private final String mavenHomeOverride;

  public MavenBuildUtils(File projectDir, String javaHomeOverride) {
    this(projectDir, javaHomeOverride, null);
  }

  public MavenBuildUtils(File projectDir, String javaHomeOverride, String mavenHomeOverride) {
    this.projectDir = projectDir;
    this.javaHomeOverride = javaHomeOverride;
    this.mavenHomeOverride = mavenHomeOverride;
  }

  public void buildProject(String rawArgs, ConsoleComponent consoleComponent, TaskListener taskListener) {
    if (projectDir == null || !projectDir.exists()) {
      throw new IllegalArgumentException("Project dir does not exist");
    }
    File pom = new File(projectDir, "pom.xml");
    if (!pom.exists()) {
      throw new IllegalArgumentException("No pom.xml found in " + projectDir.getAbsolutePath());
    }
    String trimmed = rawArgs == null ? "" : rawArgs.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("No Maven goals were provided");
    }
    String[] mvnArgs = Arrays.stream(trimmed.split("\\s+"))
        .filter(s -> !s.isBlank())
        .toArray(String[]::new);
    runBuild(pom, mvnArgs, consoleComponent, taskListener);
  }

  private void runBuild(File pom, String[] mvnArgs, ConsoleComponent consoleComponent, TaskListener taskListener) {
    ConsoleTextArea console = consoleComponent.getConsole();
    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        if (taskListener != null) {
          Platform.runLater(taskListener::taskStarted);
        }

        // Prepare Java home (null if not set or doesn't exist)
        File javaHome = null;
        if (javaHomeOverride != null && !javaHomeOverride.isBlank()) {
          File javaHomeDir = new File(javaHomeOverride);
          if (javaHomeDir.exists()) {
            javaHome = javaHomeDir;
          } else {
            console.appendWarningFx("Java home does not exist: " + javaHomeOverride);
          }
        }

        // Use MavenExecutionOptions so wrapper/home/default selection is consistent with runtime config.
        java.util.function.Consumer<String> outConsumer = line -> console.appendFx(line, true);
        java.util.function.Consumer<String> errConsumer = line -> console.appendWarningFx(line);
        MavenUtils.MavenExecutionOptions options = MavenClasspathUtils.createExecutionOptions(projectDir, mavenHomeOverride);
        int exitCode = MavenUtils.runMaven(pom, mvnArgs, javaHome, options, outConsumer, errConsumer);

        if (exitCode != 0) {
          throw new MavenInvocationException("Maven build failed with exit code " + exitCode);
        }
        return null;
      }
    };

    task.setOnSucceeded(e -> {
      if (taskListener != null) {
        taskListener.taskEnded();
      }
      Platform.runLater(consoleComponent::promptAndScrollToEnd);
      if (Gade.instance().getPrefs().getBoolean(RESTART_SESSION_AFTER_GRADLE_RUN, true)) {
        Platform.runLater(() -> Gade.instance().getConsoleComponent().restartGroovy());
      }
    });

    task.setOnFailed(e -> {
      if (taskListener != null) {
        taskListener.taskEnded();
      }
      Throwable exc = task.getException();
      log.warn("Maven build failed: {}", exc == null ? "(unknown)" : exc.toString(), exc);
      if (exc != null && exc.getMessage() != null) {
        console.appendWarningFx(exc.getMessage());
      }
      console.appendWarningFx("Build failed!");
      Platform.runLater(consoleComponent::promptAndScrollToEnd);
    });

    Thread t = new Thread(task);
    t.setDaemon(false);
    t.start();
  }


  public static String projectKey(File projectDir) {
    if (projectDir == null) {
      return "unknown";
    }
    String input = projectDir.getAbsolutePath();
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      return Integer.toHexString(input.hashCode());
    }
  }
}
