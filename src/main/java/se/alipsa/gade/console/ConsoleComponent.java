package se.alipsa.gade.console;

import static se.alipsa.gade.Constants.*;
import static se.alipsa.gade.menu.GlobalOptions.*;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovySystem;
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
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.jetbrains.annotations.Nullable;
import java.lang.reflect.InvocationTargetException;
import se.alipsa.gade.Constants;
import se.alipsa.gade.Gade;
import se.alipsa.gade.TaskListener;
import se.alipsa.gade.runtime.RuntimeClassLoaderFactory;
import se.alipsa.gade.runtime.RuntimeConfig;
import se.alipsa.gade.runtime.RuntimeIsolation;
import se.alipsa.gade.runtime.RuntimeManager;
import se.alipsa.gade.runtime.RuntimeSelectionDialog;
import se.alipsa.gade.runtime.RuntimeType;
import se.alipsa.gade.environment.EnvironmentComponent;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.FileUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import se.alipsa.gi.GuiInteraction;

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
  private GroovyClassLoader classLoader;
  private RuntimeConfig activeRuntime;
  private final RuntimeClassLoaderFactory runtimeClassLoaderFactory;

  private ScriptThread runningThread;
  private final Map<Thread, String> threadMap = new HashMap<>();
  private GroovyEngine engine;

  public ConsoleComponent(Gade gui) {
    this.gui = gui;
    runtimeClassLoaderFactory = new RuntimeClassLoaderFactory(gui);
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
    RuntimeConfig runtimeToUse = ensureRuntime(runtime);
    if (runtimeToUse == null) {
      return;
    }
    Task<Void> initTask = new Task<>() {

      @Override
      protected Void call() throws Exception {
        return resetClassloaderAndGroovy(runtimeToUse);
      }
    };
    initTask.setOnSucceeded(e -> {
      printVersionInfoToConsole();
      autoRunScripts();
      updateEnvironment();
    });
    initTask.setOnFailed(e -> {
      Throwable throwable = initTask.getException();
      Throwable ex = throwable.getCause();
      if (ex == null) {
        ex = throwable;
      }
      String msg = createMessageFromEvalException(ex);
      ExceptionAlert.showAlert(msg + ex.getMessage(), ex);
      promptAndScrollToEnd();
    });
    Thread thread = new Thread(initTask);
    thread.setDaemon(false);
    thread.start();
  }

  private RuntimeConfig ensureRuntime(RuntimeConfig requestedRuntime) {
    RuntimeManager manager = gui.getRuntimeManager();
    File projectDir = gui.getProjectDir();
    RuntimeConfig candidate = requestedRuntime == null ? manager.getSelectedRuntime(projectDir) : requestedRuntime;
    if (manager.isAvailable(candidate, projectDir)) {
      manager.setSelectedRuntime(projectDir, candidate);
      return candidate;
    }
    List<RuntimeConfig> alternatives = manager.getAllRuntimes().stream()
        .filter(r -> manager.isAvailable(r, projectDir))
        .toList();
    RuntimeSelectionDialog dialog = new RuntimeSelectionDialog();
    Optional<RuntimeConfig> selected = dialog.select(candidate, alternatives);
    RuntimeConfig resolved = selected.orElse(manager.defaultRuntime(projectDir));
    manager.setSelectedRuntime(projectDir, resolved);
    return resolved;
  }

  private void printVersionInfoToConsole() {
    String greeting = "* Groovy " + GroovySystem.getVersion() + " *";
    String surround = getStars(greeting.length());
    console.appendFx(surround, true);
    console.appendFx(greeting, true);
    console.appendFx(surround + "\n>", false);
  }

  @Nullable
  private Void resetClassloaderAndGroovy(RuntimeConfig runtime) throws Exception {
    try {

      if (gui.getInoutComponent() == null) {
        log.warn("InoutComponent is null, timing is off");
        throw new RuntimeException("resetClassloaderAndGroovy called too soon, InoutComponent is null, timing is off");
      }

      classLoader = runtimeClassLoaderFactory.create(runtime, console);
      activeRuntime = runtime;
      engine = new GroovyEngineReflection(classLoader);
      if (RuntimeType.GADE.equals(runtime.getType())) {
        addObjectsToBindings(gui.guiInteractions);
      }
      return null;
    } catch (RuntimeException e) {
      // RuntimeExceptions (such as EvalExceptions is not caught so need to wrap all in an exception
      // this way we can get to the original one by extracting the cause from the thrown exception
      System.out.println("Exception caught, rethrowing as wrapped Exception");
      throw new Exception(e);
    }
  }

  private void addObjectsToBindings(Map<String, GuiInteraction> map)
      throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    for (Map.Entry<String, GuiInteraction> entry : gui.guiInteractions.entrySet()) {
      addVariableToSession(entry.getKey(), entry.getValue());
    }
  }


  private void autoRunScripts() {
    File file = null;
    boolean wasWaiting = gui.isWaitCursorSet();
    gui.setWaitCursor();
    try {
      if(gui.getPrefs().getBoolean(AUTORUN_GLOBAL, false)) {
        file = new File(gui.getGadeBaseDir(), Constants.AUTORUN_FILENAME);
        if (file.exists()) {
          runScriptSilent(FileUtils.readContent(file));
        }
      }
      if(gui.getPrefs().getBoolean(AUTORUN_PROJECT, false)) {
        file = new File(gui.getInoutComponent().projectDir(), Constants.AUTORUN_FILENAME);
        if (file.exists()) {
          runScriptSilent(FileUtils.readContent(file));
        }
      }
      if (!wasWaiting) {
        gui.setNormalCursor();
      }
    } catch (Exception e) {
      String path = file == null ? "" : file.getAbsolutePath();
      Platform.runLater(() -> ExceptionAlert.showAlert("Failed to run " + Constants.AUTORUN_FILENAME + " in " + path, e));
    }
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

  /**
   * TODO: while we can stop the timeline with this we cannot interrupt the scriptengines eval.
   *  see https://docs.groovy-lang.org/next/html/documentation/#_safer_scripting for some possible options
   */
  public void interruptProcess() {
    log.info("Interrupting running process");
    if (runningThread != null && runningThread.isAlive()) {
      console.appendFx("\nInterrupting process...", true);
      Task<Void> task = new Task<>() {
        @Override
        protected Void call() {
          runningThread.interrupt();
          // allow two seconds for graceful shutdown
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
    if (engine == null) {
      Alerts.infoFx("Scriptengine not ready", "Groovy is still starting up, please wait a few seconds");
      return null;
    }
    if (activeRuntime == null) {
      Alerts.infoFx("Runtime not ready", "No runtime is active yet, please wait a few seconds");
      return null;
    }
    //log.info("engine is {}, gui is {}", engine, gui);
    if (RuntimeType.GADE.equals(activeRuntime.getType())) {
      gui.guiInteractions.forEach(this::addVariableToSession);
    }
    if (additionalParams != null) {
      for (Map.Entry<String, Object> entry : additionalParams.entrySet()) {
        addVariableToSession(entry.getKey(), entry.getValue());
      }
    }
    return RuntimeIsolation.run(classLoader, activeRuntime.getType(), () -> engine.eval(script));
  }



  /*
  public Object runScriptSilent(String script, Map<String, Object> additionalParams) throws Exception {
    for (Map.Entry<String, Object> entry : additionalParams.entrySet()) {
      addVariableToSession(entry.getKey(), entry.getValue());
    }
    return runScriptSilent(script);
  }

   */

  public Object runScriptSilent(String script) throws Exception {
    if (activeRuntime == null) {
      Alerts.infoFx("Runtime not ready", "No runtime is active yet, please wait a few seconds");
      return null;
    }
    try (PrintWriter out = new PrintWriter(System.out);
         PrintWriter err = new PrintWriter(System.err)) {
      running();
      engine.setOutputWriters(out, err);
      log.debug("Running script: {}", script);
      var result = RuntimeIsolation.run(classLoader, activeRuntime.getType(), () -> engine.eval(script));
      waiting();
      return result;
    } catch (Exception e) {
      log.warn("Failed to run script: {}", script, e);
      waiting();
      throw e;
    }
  }

  public Object fetchVar(String varName) {
    return engine.fetchVar(varName);
  }

  public void runScriptAsync(String script, String title, TaskListener taskListener) {

    running();

    GroovyTask task = new GroovyTask(taskListener) {
      @Override
      public Void execute() throws Exception {
        try {
          executeScriptAndReport(script, title);
        } catch (RuntimeException e) {
          // RuntimeExceptions (such as EvalExceptions is not caught so need to wrap all in an exception
          // this way we can get to the original one by extracting the cause from the thrown exception
          System.out.println("Exception caught, rethrowing as wrapped Exception");
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
      if (ex.getCause() != null && ex.getCause() instanceof InterruptedException) {
        log.info("Groovy script execution was interrupted");
        // let the interruptProcess() method handle the rest
      } else {
        String msg = createMessageFromEvalException(ex);
        log.warn("Error running script {}", script);
        ExceptionAlert.showAlert(msg + ex.getMessage(), ex);
        promptAndScrollToEnd();
      }
    });
    startTaskWhenOthersAreFinished(task, "runScriptAsync: " + title);
  }

  public String createMessageFromEvalException(Throwable ex) {
    String msg = "";

    if (ex instanceof RuntimeException) {
      msg = "An unknown error occurred running Groovy script: ";
    } else if (ex instanceof IOException) {
      msg = "Failed to close writer capturing groovy results ";
    } else if (ex instanceof RuntimeScriptException) {
      msg = "An unknown error occurred running Groovy script: ";
    } else if (ex instanceof Exception) {
      msg = "An Exception occurred: ";
    } else if (ex != null){
      msg = "Unknwn exception of type " + ex.getClass() + ": " + ex.getMessage();
    } else {
      // this should never happen
      msg = "An unknown error occurred (the Throwable is null): ";
    }
    return msg;
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
          // TODO get library dependencies from Grab and maven?
          gui.getEnvironmentComponent().setEnvironment(getContextObjects());
          refreshPackages();
        } catch (RuntimeException e) {
          // RuntimeExceptions (such as EvalExceptions is not caught so need to wrap all in an exception
          // this way we can get to the original one by extracting the cause from the thrown exception
          System.out.println("Exception caught, rethrowing as wrapped Exception");
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
      String msg = createMessageFromEvalException(ex);
      ExceptionAlert.showAlert(msg + ex.getMessage(), ex);
    });
    startTaskWhenOthersAreFinished(task, "updateEnvironment");
  }

  public Map<String, Object> getContextObjects() {
    log.info("getContextObjects");
    return engine.getContextObjects();
  }

  private void refreshPackages() {
    try {
      gui.getInoutComponent().setPackages(Collections.emptyList());
    } catch (Exception e) {
      log.debug("Failed to refresh packages", e);
    }
  }

  /*
  private void runTests(GroovyTab rTab) {
    running();
    String script = rTab.getTextContent();
    String title = rTab.getTitle();
    File file = rTab.getFile();
    console.append("", true);
    console.append("Running tests", true);
    console.append("-------------", true);

    if (file == null || !file.exists()) {
      console.append("Unable to determine script location, you must save the R script first.", true);
      return;
    }

    Task<Void> task = new Task<>() {

      long start;
      long end;

      @Override
      public Void call() {
        ((TaskListener) rTab).taskStarted();
        start = System.currentTimeMillis();
        gui.guiInteractions.forEach((k,v) -> addVariableToSession(k, v));
        List<TestResult> results = new ArrayList<>();
        try (StringWriter out = new StringWriter();
             StringWriter err = new StringWriter();
             PrintWriter outputWriter = new PrintWriter(out);
             PrintWriter errWriter = new PrintWriter(err)
        ) {
          engine.getContext().setWriter(outputWriter);
          engine.getContext().setErrorWriter(errWriter);
          //TODO not sure how to handle working dir
          //FileObject orgWd = session.getWorkingDirectory();
          //File scriptDir = file.getParentFile();
          //console.appendFx(DOUBLE_INDENT + "- Setting working directory to " + scriptDir, true);
          //session.setWorkingDirectory(scriptDir);



          TestResult result = runTest(script, title);
          // TODO: working dir
          //console.appendFx(DOUBLE_INDENT + "- Setting working directory back to " + orgWd, true);
          //session.setWorkingDirectory(orgWd);
          results.add(result);
          Platform.runLater(() -> printResult(title, out, err, result, DOUBLE_INDENT));

          end = System.currentTimeMillis();
          Map<TestResult.OutCome, List<TestResult>> resultMap = results.stream()
              .collect(Collectors.groupingBy(TestResult::getResult));

          List<TestResult> successResults = resultMap.get(TestResult.OutCome.SUCCESS);
          List<TestResult> failureResults = resultMap.get(TestResult.OutCome.FAILURE);
          List<TestResult> errorResults = resultMap.get(TestResult.OutCome.ERROR);
          long successCount = successResults == null ? 0 : successResults.size();
          long failCount = failureResults == null ? 0 : failureResults.size();
          long errorCount = errorResults == null ? 0 : errorResults.size();

          String duration = DurationFormatUtils.formatDuration(end - start, "mm 'minutes, 'ss' seconds, 'SSS' millis '");
          console.appendFx("\nR tests summary:", true);
          console.appendFx("----------------", true);
          console.appendFx(format("Tests run: {}, Successes: {}, Failures: {}, Errors: {}",
              results.size(), successCount, failCount, errorCount), true);
          console.appendFx("Time: " + duration + "\n", true);
        } catch (IOException e) {
          console.appendWarningFx("Failed to run test");
          ExceptionAlert.showAlert("Failed to run test", e);
        }
        return null;
      }
    };
    task.setOnSucceeded(e -> {
      ((TaskListener) rTab).taskEnded();
      waiting();
      updateEnvironment();
      promptAndScrollToEnd();
    });
    task.setOnFailed(e -> {
      ((TaskListener) rTab).taskEnded();
      waiting();
      updateEnvironment();
      Throwable throwable = task.getException();
      Throwable ex = throwable.getCause();
      if (ex == null) {
        ex = throwable;
      }

      String msg = createMessageFromEvalException(ex);

      ExceptionAlert.showAlert(msg + ex.getMessage(), ex);
      promptAndScrollToEnd();
    });
    Thread thread = new Thread(task);
    thread.setDaemon(false);
    startThreadWhenOthersAreFinished(thread, "runTests: " + title);
  }

  private void printResult(String title, StringWriter out, StringWriter err, TestResult result, String indent) {
    String lines = prefixLines(out, indent);
    if (!"".equals(lines.trim())) {
      console.append(lines, true);
    }
    out.getBuffer().setLength(0);
    lines = prefixLines(err, indent);
    if (!"".equals(lines.trim())) {
      console.append(lines, true);
    }
    err.getBuffer().setLength(0);
    if (TestResult.OutCome.SUCCESS.equals(result.getResult())) {
      console.append(indent + format("# {}: Success", title), true);
    } else {
      console.appendWarning(indent + format("# {}: Failure detected: {}", title, formatMessage(result.getError())));
    }
  }

   */

  /*
  private String prefixLines(StringWriter out, String prefix) {
    StringBuilder buf = new StringBuilder();
    String lines = out == null ? "" : out.toString();
    for(String line : lines.trim().split("\n")) {
      buf.append(prefix).append(line).append("\n");
    }
    return prefix + buf.toString().trim();
  }

   */

  /*
  private TestResult runTest(String script, String title, String... indentOpt) {
    String indent = INDENT;
    if (indentOpt.length > 0) {
      indent = indentOpt[0];
    }
    TestResult result = new TestResult(title);
    String issue;
    Exception exception;
    console.appendFx(indent + format("# Running test {}", title).trim(), true);
    try {
      engine.eval(script);
      result.setResult(TestResult.OutCome.SUCCESS);
      return result;
    } catch (ScriptException e) {
      exception = e;
      issue = e.getClass().getSimpleName() + " executing test " + title;
    } catch (RuntimeException e) {
      exception = e;
      issue = e.getClass().getSimpleName() + " occurred running Groovy script " + title;
    } catch (Exception e) {
      exception = e;
      issue = e.getClass().getSimpleName() + " thrown when running script " + title;
    }
    result.setResult(TestResult.OutCome.FAILURE);
    result.setError(exception);
    result.setIssue(issue);
    return result;
  }

   */

  /*
  private String formatMessage(final Throwable error) {
    return error.getMessage().trim().replace("\n", ", ");
  }

   */

  private void executeScriptAndReport(String script, String title) throws Exception {
    PrintStream sysOut = System.out;
    PrintStream sysErr = System.err;
    EnvironmentComponent env = gui.getEnvironmentComponent();
    if (activeRuntime == null) {
      Alerts.warnFx("Runtime not ready", "No runtime is active yet, cannot execute scripts");
      return;
    }
    try (
        AppenderWriter out = new AppenderWriter(console, true);
        WarningAppenderWriter err = new WarningAppenderWriter(console);
        PrintWriter outputWriter = new PrintWriter(out);
        PrintWriter errWriter = new PrintWriter(err);
        PrintStream outStream = new PrintStream(WriterOutputStream.builder().setWriter(outputWriter).get());
        PrintStream errStream = new PrintStream(WriterOutputStream.builder().setWriter(errWriter).get());
    ) {
      if (engine == null) {
        Alerts.warnFx("Engine has not started yet", "There seems to be some issue with initialization");
        return;
      }
      if (RuntimeType.GADE.equals(activeRuntime.getType())) {
        addObjectsToBindings(gui.guiInteractions);
      }
      //gui.guiInteractions.forEach((k,v) -> engine.put(k, v));

      Platform.runLater(() -> {
        console.append(title, true);
        env.addInputHistory(script);
      });
      engine.setOutputWriters(outputWriter, errWriter);
      System.setOut(outStream);
      System.setErr(errStream);
      var result = RuntimeIsolation.run(classLoader, activeRuntime.getType(), () -> engine.eval(script));
      // TODO: add config to opt out of printing the result to the console
      if (result != null) {
        gui.getConsoleComponent().getConsole().appendFx(result.toString(), true);
      }
      Platform.runLater(() -> env.addOutputHistory(out.getCachedText()));
    } catch (RuntimeException re) {
      throw new Exception(re.getMessage(), re);
    } finally {
      System.setOut(sysOut);
      System.setErr(sysErr);
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

    // TODO: not sure how to do this, user.dir property only partially works
    //  and ORACLE recommends not to change it
    // System.setProperty("user.dir", dir.getAbsolutePath());
    /*
    try {
      if (session != null) {
        session.setWorkingDirectory(dir);
      }
      workingDir = dir;
    } catch (FileSystemException e) {
      log.warn("Error setting working dir to {} for session", dir, e);
    }

     */
  }

  /**
   * Return a reference to the current scripting classloader
   *
   * @return a reference to the current scripting classloader
   */
  public ClassLoader getSessionClassloader() {
    return engine.getClass().getClassLoader();
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

  public void startTaskWhenOthersAreFinished(CountDownTask<?> task, String context) {
    ScriptThread thread = task.createThread();
    thread.setContextClassLoader(classLoader);
    if (runningThread == null) {
      log.debug("Starting thread {}", context);
      thread.start();
    } else if (runningThread.getState() == Thread.State.WAITING || runningThread.getState() == Thread.State.TIMED_WAITING) {
      log.debug("Waiting for thread {} to finish", threadMap.get(runningThread));
      try {
        task.countDownLatch.await();
        // runningThread.join();
        thread.start();
      } catch (InterruptedException e) {
        task.cancel(true);
        log.warn("Thread was interrupted", e);
        log.info("Running thread {}", context);
        thread.start();
      }

    } else if (runningThread.isAlive() && runningThread.getState() != Thread.State.TERMINATED) {
      log.warn("There is already a process running: {} in state {}, Overriding existing running thread", threadMap.get(runningThread), runningThread.getState());
      thread.start();
    } else {
      if (runningThread.getState() == Thread.State.NEW) {
        // if we get here, we have created a thread but forgot to start it
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
    return classLoader;
  }

  public OutputStream getOutputStream() {
    return new ConsoleOutputStream(this);
  }

  public void addVariableToSession(String key, Object value) {
    log.info("adding {} to session", key);
    engine.addVariableToSession(key, value);
  }

  /**
   * Remove a binding from the engine.
   *
   * @param varName the bound variable to remove.
   */
  public void removeVariableFromSession(String varName) {
    engine.removeVariableFromSession(varName);
  }
}
