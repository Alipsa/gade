package se.alipsa.gade.console;

import static se.alipsa.gade.Constants.*;

import groovy.lang.GroovyClassLoader;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.gade.Gade;
import se.alipsa.gade.TaskListener;
import se.alipsa.gade.runtime.RuntimeConfig;
import se.alipsa.gade.runtime.RuntimeType;
import se.alipsa.gade.environment.EnvironmentComponent;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.FileUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class ConsoleComponent extends BorderPane {

  private static final Image IMG_RUNNING = new Image(Objects.requireNonNull(FileUtils
      .getResourceUrl("image/running.png")).toExternalForm(), ICON_WIDTH, ICON_HEIGHT, true, true);
  private static final Image IMG_WAITING = new Image(Objects.requireNonNull(FileUtils
      .getResourceUrl("image/waiting.png")).toExternalForm(), ICON_WIDTH, ICON_HEIGHT, true, true);
  private static final String DOUBLE_INDENT = INDENT + INDENT;
  private static final Logger log = LogManager.getLogger(ConsoleComponent.class);

  private final ImageView runningView;
  private final Button statusButton;
  private final ConsoleTextArea console;
  private final Gade gui;

  // Extracted runtime management
  private final GroovyRuntimeManager runtimeManager;

  // Thread management
  private ScriptThread runningThread;
  private final Map<Thread, String> threadMap = new HashMap<>();

  public ConsoleComponent(Gade gui) {
    this.gui = gui;
    this.runtimeManager = new GroovyRuntimeManager(gui);
    console = new ConsoleTextArea(gui);
    console.setEditable(false);

    Button clearButton = new Button("Clear");
    clearButton.setOnAction(e -> {
      console.clear();
      console.appendText(">");
    });
    FlowPane topPane = new FlowPane();
    topPane.setPadding(new Insets(1, 10, 1, 5));
    topPane.setHgap(10);

    runningView = new ImageView();
    statusButton = new Button();
    statusButton.setOnAction(e -> interruptProcess());
    statusButton.setGraphic(runningView);
    waiting();

    topPane.getChildren().addAll(statusButton, clearButton);
    setTop(topPane);

    VirtualizedScrollPane<ConsoleTextArea> vPane = new VirtualizedScrollPane<>(console);
    vPane.setMaxWidth(Double.MAX_VALUE);
    vPane.setMaxHeight(Double.MAX_VALUE);
    setCenter(vPane);
  }

  /**
   * Initialize the groovy engine
   *
   * @param runtime the runtime configuration to use
   */
  public void initGroovy(RuntimeConfig runtime) {
    RuntimeConfig runtimeToUse = runtimeManager.ensureRuntime(runtime);
    if (runtimeToUse == null) {
      return;
    }
    running();
    Task<Void> initTask = new Task<>() {
      @Override
      protected Void call() throws Exception {
        return runtimeManager.resetClassloaderAndGroovy(runtimeToUse, false, console);
      }
    };
    initTask.setOnSucceeded(e -> {
      printVersionInfoToConsole();
      updateEnvironment();
      waiting();
      Thread autoRunThread = new Thread(() ->
          runtimeManager.autoRunScripts(console, this::runScriptSilent));
      autoRunThread.setDaemon(true);
      autoRunThread.start();
    });
    initTask.setOnFailed(e -> {
      Throwable throwable = initTask.getException();
      Throwable ex = throwable.getCause();
      if (ex == null) {
        ex = throwable;
      }
      String msg = ScriptExecutionHelper.createMessageFromEvalException(ex);
      ExceptionAlert.showAlert(msg + ex.getMessage(), ex);
      promptAndScrollToEnd();
      waiting();
    });
    Thread thread = new Thread(initTask);
    thread.setDaemon(false);
    thread.start();
  }

  private void printVersionInfoToConsole() {
    String greeting = "* Groovy " + runtimeManager.getActiveGroovyVersion() + " *";
    String surround = getStars(greeting.length());
    console.appendFx(surround, true);
    console.appendFx(greeting, true);
    console.appendFx(surround + "\n>", false);
  }

  private String getStars(int length) {
    return "*".repeat(Math.max(0, length));
  }

  /**
   * Restart (reset) the Groovy engine.
   */
  public void restartGroovy() {
    console.append("Restarting Groovy..\n");
    initGroovy(gui.getActiveRuntime());
    gui.getEnvironmentComponent().clearEnvironment();
  }

  public String getActiveGroovyVersion() {
    return runtimeManager.getActiveGroovyVersion();
  }

  public Optional<String> getConfiguredJavaVersion() {
    return runtimeManager.getConfiguredJavaVersion();
  }

  public boolean isConfiguredJavaDifferent(String configuredVersion) {
    return runtimeManager.isConfiguredJavaDifferent(configuredVersion);
  }

  /**
   * Interrupts the running process by sending an interrupt command to the subprocess.
   */
  public void interruptProcess() {
    log.info("Interrupting running process");
    if (runtimeManager.getProcessRunner() != null) {
      try {
        runtimeManager.getProcessRunner().interrupt();
      } catch (IOException e) {
        log.warn("Failed to interrupt process runner", e);
      }
    }
    if (runningThread != null && runningThread.isAlive()) {
      console.appendFx("\nInterrupting process...", true);
      Task<Void> task = new Task<>() {
        @Override
        protected Void call() {
          runningThread.interrupt();
          sleep(2000);
          return null;
        }
      };
      task.setOnSucceeded(e -> {
        console.appendFx("Process stopped!", false);
        threadMap.remove(runningThread);
        Platform.runLater(() -> console.appendText("\n>"));
        gui.setNormalCursor();
        waiting();
      });
      new Thread(task).start();
    }
  }

  private void sleep(int millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      log.info("Sleep was interrupted");
    }
  }

  /**
   * Execute a script.
   *
   * @param script the script code to execute
   * @param additionalParams a map of parameters to bind.
   * @return the result of the script.
   * @throws Exception if something goes wrong.
   */
  public Object runScript(String script, Map<String, Object> additionalParams) throws Exception {
    RuntimeConfig activeRuntime = runtimeManager.getActiveRuntime();
    if (activeRuntime == null) {
      Alerts.infoFx("Runtime not ready", "No runtime is active yet, please wait a few seconds");
      return null;
    }
    if (runtimeManager.getProcessRunner() == null) {
      Alerts.warnFx("Engine has not started yet", "There seems to be some issue with initialization");
      return null;
    }
    running();
    try {
      Map<String, Object> bindings = ScriptExecutionHelper.prepareRunnerBindings(additionalParams, gui.guiInteractions);
      String result = runtimeManager.getProcessRunner().eval(script, bindings, false).get();
      waiting();
      return result;
    } catch (Exception e) {
      waiting();
      throw e;
    }
  }

  public Object runScriptSilent(String script) throws Exception {
    RuntimeConfig activeRuntime = runtimeManager.getActiveRuntime();
    if (activeRuntime == null) {
      Alerts.infoFx("Runtime not ready", "No runtime is active yet, please wait a few seconds");
      return null;
    }
    if (runtimeManager.getProcessRunner() == null) {
      Alerts.warnFx("Engine has not started yet", "There seems to be some issue with initialization");
      return null;
    }
    running();
    try {
      String result = runtimeManager.getProcessRunner().eval(script,
          ScriptExecutionHelper.prepareRunnerBindings(null, gui.guiInteractions), false).get();
      waiting();
      return result;
    } catch (Exception e) {
      log.warn("Failed to run script: {}", script, e);
      waiting();
      throw e;
    }
  }

  public Object fetchVar(String varName) {
    return runtimeManager.fetchVar(varName);
  }

  public void runScriptAsync(String script, String title, TaskListener taskListener) {
    runScriptAsync(script, title, taskListener, null);
  }

  public void runScriptAsync(String script, String title, TaskListener taskListener, File sourceFile) {
    running();
    boolean testContext = runtimeManager.resolveTestContextForSource(sourceFile);

    GroovyTask task = new GroovyTask(taskListener) {
      @Override
      public Void execute() throws Exception {
        try {
          executeScriptAndReport(script, title, testContext);
        } catch (RuntimeException e) {
          log.debug("Exception caught, rethrowing as wrapped Exception");
          throw new Exception(e);
        }
        return null;
      }
    };

    task.setOnSucceeded(e -> {
      taskListener.taskEnded();
      waiting();
      updateEnvironment();
      promptAndScrollToEnd();
    });
    task.setOnFailed(e -> {
      taskListener.taskEnded();
      waiting();
      updateEnvironment();
      Throwable throwable = task.getException();
      Throwable ex = throwable.getCause();
      if (ex == null) {
        ex = throwable;
      }
      if (isUserInitiatedStop(ex)) {
        log.info("Script execution was interrupted or runner was stopped");
      } else {
        String msg = ScriptExecutionHelper.createMessageFromEvalException(ex);
        log.warn("Error running script {}", script);
        ExceptionAlert.showAlert(msg + ex.getMessage(), ex);
        promptAndScrollToEnd();
      }
    });
    startTaskWhenOthersAreFinished(task, "runScriptAsync: " + title);
  }

  private void executeScriptAndReport(String script, String title, boolean testContext) throws Exception {
    EnvironmentComponent env = gui.getEnvironmentComponent();
    RuntimeConfig activeRuntime = runtimeManager.getActiveRuntime();
    if (activeRuntime == null) {
      Alerts.warnFx("Runtime not ready", "No runtime is active yet, cannot execute scripts");
      return;
    }
    if (runtimeManager.getProcessRunner() == null) {
      Alerts.warnFx("Engine has not started yet", "There seems to be some issue with initialization");
      return;
    }
    Platform.runLater(() -> {
      console.append(title, true);
      env.addInputHistory(script);
    });
    try {
      runtimeManager.getProcessRunner().eval(script,
          ScriptExecutionHelper.prepareRunnerBindings(null, gui.guiInteractions), testContext).get();
      Platform.runLater(() -> env.addOutputHistory(""));
    } catch (Exception e) {
      throw new Exception(e.getMessage(), e);
    }
  }

  public void addExternalMessage(String title, String content, boolean addPrompt, boolean addNewLine) {
    if (title != null && !title.isEmpty()) {
      console.appendWithStyle(title, "info", addNewLine);
    }
    console.appendWithStyle(content, "info", addNewLine);
    if (addPrompt) {
      promptAndScrollToEnd();
    } else {
      scrollToEnd();
    }
  }

  public void addOutput(String title, String content, boolean addPrompt, boolean addNewLine) {
    if (title != null && !title.isEmpty()) {
      console.append(title, true);
    }
    console.append(content, addNewLine);
    if (addPrompt) {
      promptAndScrollToEnd();
    } else {
      scrollToEnd();
    }
  }

  public void addWarning(String title, String content, boolean addPrompt) {
    console.appendWarning(title + ": ");
    console.appendWarning(content);
    if (addPrompt) {
      promptAndScrollToEnd();
    } else {
      scrollToEnd();
    }
  }

  /**
   * print the prompt (>) and scroll to the end of the output
   */
  public void promptAndScrollToEnd() {
    console.appendText(">");
    scrollToEnd();
  }

  /**
   * scroll to the end of the output
   */
  public void scrollToEnd() {
    console.moveTo(console.getLength());
    console.requestFollowCaret();
  }

  /**
   * Update the environment after a run
   */
  public void updateEnvironment() {
    TaskListener listener = new TaskListener() {
      @Override
      public void taskStarted() {
      }
      @Override
      public void taskEnded() {
      }
    };
    GroovyTask task = new GroovyTask(listener) {
      @Override
      public Void execute() throws Exception {
        try {
          gui.getEnvironmentComponent().setEnvironment(runtimeManager.getContextObjects());
          refreshPackages();
        } catch (RuntimeException e) {
          log.debug("Exception caught, rethrowing as wrapped Exception");
          throw new Exception(e);
        }
        return null;
      }
    };

    task.setOnFailed(e -> {
      Throwable throwable = task.getException();
      Throwable ex = throwable.getCause();
      if (ex == null) {
        ex = throwable;
      }
      String msg = ScriptExecutionHelper.createMessageFromEvalException(ex);
      ExceptionAlert.showAlert(msg + ex.getMessage(), ex);
    });
    startTaskWhenOthersAreFinished(task, "updateEnvironment");
  }

  public Map<String, Object> getContextObjects() {
    return runtimeManager.getContextObjects();
  }

  private void refreshPackages() {
    try {
      gui.getInoutComponent().setPackages(Collections.emptyList());
    } catch (Exception e) {
      log.debug("Failed to refresh packages", e);
    }
  }

  /**
   * Indicates that a script is running.
   */
  public void running() {
    Platform.runLater(() -> {
      runningView.setImage(IMG_RUNNING);
      statusButton.setTooltip(new Tooltip("Process is running, click to abort"));
      showTooltip(statusButton);
      gui.getMainMenu().enableInterruptMenuItem();
    });
    sleep(20);
  }

  /**
   * Indicates that the engine is idle.
   */
  public void waiting() {
    Platform.runLater(() -> {
      runningView.setImage(IMG_WAITING);
      statusButton.setTooltip(new Tooltip("Engine is idle"));
      gui.getMainMenu().disableInterruptMenuItem();
    });
  }

  private void showTooltip(Control control) {
    Tooltip customTooltip = control.getTooltip();
    Stage owner = gui.getStage();
    Point2D p = control.localToScene(10.0, 20.0);

    customTooltip.setAutoHide(true);

    customTooltip.show(owner, p.getX()
        + control.getScene().getX() + control.getScene().getWindow().getX(), p.getY()
        + control.getScene().getY() + control.getScene().getWindow().getY());

    Timer timer = new Timer();
    TimerTask task = new TimerTask() {
      public void run() {
        Platform.runLater(customTooltip::hide);
      }
    };
    timer.schedule(task, 800);
  }

  public void setWorkingDir(File dir) {
    if (dir == null) {
      return;
    }
    if (runtimeManager.getProcessRunner() != null) {
      runtimeManager.getProcessRunner().setWorkingDir(dir);
    }
  }

  /**
   * Return a reference to the current scripting classloader
   *
   * @return a reference to the current scripting classloader
   */
  public ClassLoader getSessionClassloader() {
    GroovyClassLoader cl = runtimeManager.getClassLoader();
    return cl == null ? getClass().getClassLoader() : cl;
  }

  public void setConsoleMaxSize(int size) {
    console.setConsoleMaxSize(size);
  }

  public int getConsoleMaxSize() {
    return console.getConsoleMaxSize();
  }

  public ConsoleTextArea getConsole() {
    return console;
  }

  public RuntimeConfig getActiveRuntimeConfig() {
    return runtimeManager.getActiveRuntime();
  }

  public void startTaskWhenOthersAreFinished(CountDownTask<?> task, String context) {
    ScriptThread thread = task.createThread();
    thread.setContextClassLoader(runtimeManager.getClassLoader());
    if (runningThread == null) {
      log.debug("Starting thread {}", context);
      thread.start();
    } else if (runningThread.getState() == Thread.State.WAITING || runningThread.getState() == Thread.State.TIMED_WAITING) {
      log.info("Previous thread {} is in {} state, starting new thread",
          threadMap.get(runningThread), runningThread.getState());
      thread.start();
    } else if (runningThread.isAlive() && runningThread.getState() != Thread.State.TERMINATED) {
      log.warn("There is already a process running: {} in state {}, Overriding existing running thread",
          threadMap.get(runningThread), runningThread.getState());
      thread.start();
    } else {
      if (runningThread.getState() == Thread.State.NEW) {
        log.error("Running thread {} is created but not started, this is bug that should be fixed", runningThread);
      } else if (runningThread.getState() != Thread.State.TERMINATED) {
        log.error("Missed some condition, running thread {} is {}", threadMap.get(runningThread), runningThread.getState());
      }
      thread.start();
    }
    threadMap.remove(runningThread);
    runningThread = thread;
    threadMap.put(thread, context);
  }

  /**
   * Set the cursor to running / busy mode
   */
  public void busy() {
    this.setCursor(Cursor.WAIT);
    console.setCursor(Cursor.WAIT);
  }

  /**
   * Set the cursor to ready/waiting mode
   */
  public void ready() {
    this.setCursor(Cursor.DEFAULT);
    console.setCursor(Cursor.DEFAULT);
  }

  public GroovyClassLoader getClassLoader() {
    return runtimeManager.getClassLoader();
  }

  public OutputStream getOutputStream() {
    return new ConsoleOutputStream(this);
  }

  public void addVariableToSession(String key, Object value) {
    runtimeManager.addVariableToSession(key, value);
  }

  /**
   * Remove a binding from the engine.
   *
   * @param varName the bound variable to remove.
   */
  public void removeVariableFromSession(String varName) {
    runtimeManager.removeVariableFromSession(varName);
  }

  public List<File> getProjectDependencies() {
    RuntimeConfig activeRuntime = runtimeManager.getActiveRuntime();
    if (activeRuntime == null) {
      return Collections.emptyList();
    }
    // Delegate to appropriate utility based on runtime type
    File projectDir = gui.getProjectDir();
    if (RuntimeType.GRADLE.equals(activeRuntime.getType())) {
      try {
        return new se.alipsa.gade.utils.gradle.GradleUtils(null, projectDir, activeRuntime.getJavaHome())
            .getProjectDependencies();
      } catch (Exception e) {
        log.warn("Failed to get Gradle dependencies", e);
        return Collections.emptyList();
      }
    }
    return Collections.emptyList();
  }

  public List<URL> getOutputDirs() throws MalformedURLException {
    RuntimeConfig activeRuntime = runtimeManager.getActiveRuntime();
    if (activeRuntime == null) {
      return Collections.emptyList();
    }
    File projectDir = gui.getProjectDir();
    if (RuntimeType.GRADLE.equals(activeRuntime.getType())) {
      return new se.alipsa.gade.utils.gradle.GradleUtils(null, projectDir, activeRuntime.getJavaHome())
          .getOutputDirs();
    }
    return Collections.emptyList();
  }

  /**
   * Creates a descriptive error message based on the exception type.
   *
   * @param ex the exception to describe
   * @return a message prefix describing the error type
   */
  public String createMessageFromEvalException(Throwable ex) {
    return ScriptExecutionHelper.createMessageFromEvalException(ex);
  }

  /**
   * Walks the cause chain to check if the exception was triggered by a user-initiated
   * interrupt or session restart (as opposed to a genuine script error).
   */
  private static boolean isUserInitiatedStop(Throwable t) {
    while (t != null) {
      if (t instanceof InterruptedException) return true;
      if (t instanceof IllegalStateException && "Runner stopped".equals(t.getMessage())) return true;
      t = t.getCause();
    }
    return false;
  }
}
