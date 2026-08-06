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
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BashTask extends CountDownTask<Void> {

  /**
   * The charset a child process writes its output in. This is the platform native encoding,
   * which since JDK 18 is no longer the same thing as {@link Charset#defaultCharset()}.
   */
  private static final Charset PROCESS_CHARSET = Charset.forName(
      System.getProperty("native.encoding", Charset.defaultCharset().name()),
      Charset.defaultCharset());

  /** How long to wait for the stderr drain thread after the process has exited. */
  private static final long STDERR_DRAIN_TIMEOUT_MS = 5000;

  /** Processes started by this task type that have not yet terminated. */
  private static final Set<Process> runningProcesses = ConcurrentHashMap.newKeySet();

  static {
    // A child process outlives the JVM that started it, so kill whatever is still
    // running when Gade shuts down rather than leaving orphans behind.
    Runtime.getRuntime().addShutdownHook(new Thread(() -> runningProcesses.forEach(BashTask::destroy)));
  }

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
      runningProcesses.add(process);
      // Nothing ever writes to the child stdin, so close it right away. Otherwise a script
      // that reads stdin blocks forever on a pipe that will never receive anything.
      closeQuietly(process.getOutputStream());
      if (stopRequested || Thread.currentThread().isInterrupted()) {
        destroyProcess();
        runningProcesses.remove(process);
        throw new InterruptedException("Bash execution interrupted");
      }
    } catch (IOException e) {
      console.appendWarningFx("Failed to start bash: " + e.getMessage());
      throw e;
    }

    try (
        AppenderWriter out = new AppenderWriter(console);
        WarningAppenderWriter err = new WarningAppenderWriter(console);
        BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream(), PROCESS_CHARSET));
        BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream(), PROCESS_CHARSET))
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
      // The process has exited so the remaining stderr is buffered and readable at once,
      // which the drain thread gets through in no time. The wait is still bounded because
      // a background child of the script can keep the stderr pipe open indefinitely.
      errThread.join(STDERR_DRAIN_TIMEOUT_MS);

      if (exitCode != 0 && !stopRequested) {
        // A non-zero exit is a normal outcome for a shell script, so report it in the
        // console instead of raising an error dialog.
        console.appendWarningFx("Script exited with code " + exitCode);
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
      runningProcesses.remove(process);
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
    destroy(process);
  }

  private static void destroy(Process current) {
    if (current == null || !current.isAlive()) {
      return;
    }
    current.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
    current.destroyForcibly();
  }

  private static void closeQuietly(Closeable closeable) {
    try {
      closeable.close();
    } catch (IOException e) {
      // Nothing useful to do about it
    }
  }

  private void drainStream(BufferedReader reader, WarningAppenderWriter writer) {
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        // No trailing newline needed, appendWarningFx adds one.
        writer.write(line.toCharArray(), 0, line.length());
      }
    } catch (IOException e) {
      // Stream closed, ignore
    }
  }
}
