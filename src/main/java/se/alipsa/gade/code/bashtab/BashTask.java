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
  private final Gade gui;

  public BashTask(String content, File file, Gade gui, TaskListener taskListener) {
    super(taskListener);
    this.content = content;
    this.file = file;
    this.gui = gui;
  }

  @Override
  public ScriptThread createThread() {
    ScriptThread thread = new ScriptThread(this, taskListener);
    thread.setDaemon(false);
    return thread;
  }

  @Override
  public Void execute() throws Exception {
    ConsoleComponent consoleComponent = gui.getConsoleComponent();
    ConsoleTextArea console = consoleComponent.getConsole();

    List<String> command = new ArrayList<>();
    command.add("bash");
    File workingDir = null;
    String title;
    if (file != null && file.isFile()) {
      command.add(file.getAbsolutePath());
      workingDir = file.getParentFile();
      title = file.getName();
    } else {
      command.add("-c");
      command.add(content);
      title = "bash";
    }

    ProcessBuilder pb = new ProcessBuilder(command);
    if (workingDir != null) {
      pb.directory(workingDir);
    }
    pb.redirectErrorStream(false);

    final String runTitle = title;
    Platform.runLater(() -> console.append(runTitle, true));

    Process process;
    try {
      process = pb.start();
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
      process.destroy();
      Thread.currentThread().interrupt();
      throw new RuntimeException("Bash execution interrupted", e);
    }
    return null;
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
