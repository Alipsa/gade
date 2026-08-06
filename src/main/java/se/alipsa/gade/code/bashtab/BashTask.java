package se.alipsa.gade.code.bashtab;

import javafx.application.Platform;
import se.alipsa.gade.Gade;
import se.alipsa.gade.TaskListener;
import se.alipsa.gade.console.AppenderWriter;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.console.CountDownTask;
import se.alipsa.gade.console.ScriptThread;
import se.alipsa.gade.console.WarningAppenderWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public abstract class BashTask extends CountDownTask<Void> {

  private final String content;
  private final File file;
  private final List<String> args;
  private final Gade gui;
  private volatile Process process;
  private volatile boolean stopRequested;

  public BashTask(String content, File file, List<String> args, Gade gui, TaskListener taskListener) {
    super(taskListener);
    this.content = content;
    this.file = file;
    this.args = args == null ? List.of() : args;
    this.gui = gui;
  }

  /**
   * Parse a raw argument string into a list of tokens.
   * Whitespace separates arguments; both single and double quotes can be used
   * to group arguments containing spaces. Quotes are stripped from the token.
   */
  static List<String> parseArgs(String text) {
    List<String> tokens = new ArrayList<>();
    if (text == null || text.isBlank()) {
      return tokens;
    }
    StringBuilder current = new StringBuilder();
    Character quote = null;
    boolean tokenStarted = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quote != null) {
        if (c == quote) {
          quote = null;
        } else {
          current.append(c);
        }
      } else if (c == '\"' || c == '\'') {
        quote = c;
        tokenStarted = true;
      } else if (Character.isWhitespace(c)) {
        if (tokenStarted) {
          tokens.add(current.toString());
          current.setLength(0);
          tokenStarted = false;
        }
      } else {
        current.append(c);
        tokenStarted = true;
      }
    }
    if (tokenStarted) {
      tokens.add(current.toString());
    }
    return tokens;
  }

  @Override
  public ScriptThread createThread() {
    ScriptThread thread = new ScriptThread(this, taskListener) {
      @Override
      public void interrupt() {
        stopRequested = true;
        destroyProcess();
        super.interrupt();
      }
    };
    thread.setDaemon(false);
    return thread;
  }

  @Override
  public Void execute() throws Exception {
    ConsoleComponent consoleComponent = gui.getConsoleComponent();
    ConsoleTextArea console = consoleComponent.getConsole();

    List<String> command = buildCommand(content, file, args);
    File workingDir = file == null ? null : file.getParentFile();
    String title = file == null ? "bash" : file.getName();

    ProcessBuilder pb = new ProcessBuilder(command);
    if (workingDir != null) {
      pb.directory(workingDir);
    }
    pb.redirectErrorStream(false);

    final String runTitle = title;
    Platform.runLater(() -> console.append(runTitle, true));

    try {
      process = pb.start();
      if (stopRequested || Thread.currentThread().isInterrupted()) {
        destroyProcess();
        throw new InterruptedException("Bash execution interrupted");
      }
    } catch (IOException e) {
      Platform.runLater(() -> console.appendWarningFx("Failed to start bash: " + e.getMessage()));
      throw e;
    }

    try (
        AppenderWriter out = new AppenderWriter(console);
        WarningAppenderWriter err = new WarningAppenderWriter(console);
        BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()))
    ) {
      Thread errThread = new Thread(() -> drainStream(stdErr, err));
      errThread.setDaemon(true);
      errThread.start();

      String line;
      while ((line = stdOut.readLine()) != null) {
        out.write(line.toCharArray(), 0, line.length());
        out.write("\n".toCharArray(), 0, 1);
      }

      int exitCode = process.waitFor();
      errThread.join(1000);

      if (exitCode != 0) {
        throw new RuntimeException("Bash script exited with code " + exitCode);
      }
    } catch (InterruptedException e) {
      destroyProcess();
      Thread.currentThread().interrupt();
      throw new RuntimeException("Bash execution interrupted", e);
    } catch (IOException e) {
      if (stopRequested || Thread.currentThread().isInterrupted()) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Bash execution interrupted", e);
      }
      throw e;
    } finally {
      destroyProcess();
      process = null;
    }
    return null;
  }

  static List<String> buildCommand(String content, File file, List<String> args) {
    List<String> command = new ArrayList<>();
    command.add("bash");
    command.add("-c");
    command.add(content);
    // bash -c assigns the first argument after the script to $0.
    command.add(file == null ? "_" : file.getAbsolutePath());
    command.addAll(args == null ? List.of() : args);
    return command;
  }

  private void destroyProcess() {
    Process current = process;
    if (current == null || !current.isAlive()) {
      return;
    }
    current.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
    current.destroyForcibly();
  }

  private void drainStream(BufferedReader reader, WarningAppenderWriter writer) {
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        writer.write(line.toCharArray(), 0, line.length());
        writer.write("\n".toCharArray(), 0, 1);
      }
    } catch (IOException e) {
      // Stream closed, ignore
    }
  }
}
