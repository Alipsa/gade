package se.alipsa.gade.utils.maven;

import static se.alipsa.gade.menu.GlobalOptions.RESTART_SESSION_AFTER_GRADLE_RUN;

import javafx.application.Platform;
import javafx.concurrent.Task;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationOutputHandler;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import se.alipsa.gade.Gade;
import se.alipsa.gade.TaskListener;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.console.WarningAppenderWriter;
import se.alipsa.mavenutils.EnvUtils;
import se.alipsa.mavenutils.MavenUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class MavenBuildUtils {

  private static final Logger log = LogManager.getLogger(MavenBuildUtils.class);

  private final File projectDir;
  private final String javaHomeOverride;

  public MavenBuildUtils(File projectDir, String javaHomeOverride) {
    this.projectDir = projectDir;
    this.javaHomeOverride = javaHomeOverride;
  }

  public void buildProject(String rawArgs, ConsoleComponent consoleComponent, TaskListener taskListener) {
    if (projectDir == null || !projectDir.exists()) {
      throw new IllegalArgumentException("Project dir does not exist");
    }
    File pom = new File(projectDir, "pom.xml");
    if (!pom.exists()) {
      throw new IllegalArgumentException("No pom.xml found in " + projectDir.getAbsolutePath());
    }
    ParsedArgs parsed = parseArgs(rawArgs);
    if (parsed.goals.isEmpty()) {
      throw new IllegalArgumentException("No Maven goals were provided");
    }
    runBuild(pom, parsed, consoleComponent, taskListener);
  }

  private void runBuild(File pom, ParsedArgs parsed, ConsoleComponent consoleComponent, TaskListener taskListener) {
    ConsoleTextArea console = consoleComponent.getConsole();
    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        if (taskListener != null) {
          Platform.runLater(taskListener::taskStarted);
        }
        InvocationRequest request = new DefaultInvocationRequest()
            .setBatchMode(true)
            .setPomFile(pom)
            .setBaseDirectory(projectDir)
            .setGoals(parsed.goals);
        if (!parsed.profiles.isEmpty()) {
          request.setProfiles(parsed.profiles);
        }
        if (!parsed.properties.isEmpty()) {
          request.getProperties().putAll(parsed.properties);
        }
        if (javaHomeOverride != null && !javaHomeOverride.isBlank()) {
          File javaHomeDir = new File(javaHomeOverride);
          if (javaHomeDir.exists()) {
            request.setJavaHome(javaHomeDir);
          } else {
            console.appendWarningFx("Java home does not exist: " + javaHomeOverride);
          }
        }
        try (OutputStream outputStream = consoleComponent.getOutputStream();
             WarningAppenderWriter err = new WarningAppenderWriter(console);
             PrintStream errStream = new PrintStream(WriterOutputStream.builder().setWriter(err).get())) {
          Invoker invoker = new DefaultInvoker();
          configureMavenHome(invoker, console);
          invoker.setOutputHandler(streamingHandler(outputStream));
          invoker.setErrorHandler(line -> errStream.println(line));
          InvocationResult result = invoker.execute(request);
          if (result.getExecutionException() != null) {
            throw result.getExecutionException();
          }
          int exitCode = result.getExitCode();
          if (exitCode != 0) {
            throw new MavenInvocationException("Maven build failed with exit code " + exitCode);
          }
          return null;
        }
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

  private static void configureMavenHome(Invoker invoker, ConsoleTextArea console) {
    String mavenHome = MavenUtils.locateMavenHome();
    if (mavenHome == null || mavenHome.isBlank()) {
      return;
    }
    File mavenHomeDir = new File(mavenHome);
    if (mavenHomeDir.exists()) {
      invoker.setMavenHome(mavenHomeDir);
      return;
    }
    console.appendWarningFx("No MAVEN_HOME set (or it does not exist): " + mavenHome);
  }

  private static InvocationOutputHandler streamingHandler(OutputStream out) {
    return line -> {
      try {
        out.write((line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    };
  }

  private static ParsedArgs parseArgs(String rawArgs) {
    String trimmed = rawArgs == null ? "" : rawArgs.trim();
    if (trimmed.isEmpty()) {
      return new ParsedArgs(List.of(), List.of(), new Properties());
    }
    String[] tokens = Arrays.stream(trimmed.split("\\s+"))
        .filter(t -> !t.isBlank())
        .toArray(String[]::new);

    Properties properties = EnvUtils.parseArguments(tokens);
    List<String> profiles = new ArrayList<>();
    List<String> goals = new ArrayList<>();
    for (String token : tokens) {
      if (token.startsWith("-D")) {
        continue;
      }
      if (token.startsWith("-P") && token.length() > 2) {
        String rest = token.substring(2);
        profiles.addAll(Arrays.stream(rest.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList());
        continue;
      }
      goals.add(token);
    }
    return new ParsedArgs(goals, profiles, properties);
  }

  private record ParsedArgs(List<String> goals, List<String> profiles, Properties properties) {}

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

