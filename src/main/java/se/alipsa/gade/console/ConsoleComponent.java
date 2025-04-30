package se.alipsa.gade.console;

import static se.alipsa.gade.Constants.*;
import static se.alipsa.gade.menu.GlobalOptions.*;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovySystem;
import groovy.transform.ThreadInterrupt;
import java.lang.reflect.InvocationTargetException;
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
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.jetbrains.annotations.Nullable;
import se.alipsa.gade.Constants;
import se.alipsa.gade.Gade;
import se.alipsa.gade.TaskListener;
import se.alipsa.gade.environment.EnvironmentComponent;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.ClassUtils;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.gade.utils.gradle.GradleUtils;

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

  private ScriptThread runningThread;
  private final Map<Thread, String> threadMap = new HashMap<>();
  private GroovyEngine engine;

  public ConsoleComponent(Gade gui) {
    this.gui = gui;
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



  /* not used to commented out
  public void initGroovy(ClassLoader parentClassLoader, boolean... sync) {
    if (sync.length > 0 && sync[0]) {
      try {
        resetClassloaderAndGroovy(parentClassLoader);
        printVersionInfoToConsole();
        autoRunScripts();
        updateEnvironment();
      } catch (Exception e) {
        ExceptionAlert.showAlert("Failed to reset classloader and Groovy, please report this!", e);
      }
    } else {
      Platform.runLater(() -> initGroovy(parentClassLoader));
    }
  }
   */

  /**
   * Initialize the groovy engine
   *
   * @param parentClassLoader the classloader to use as the parent classloader for the GroovyClassloader
   */
  public void initGroovy(GroovyClassLoader parentClassLoader) {
    Task<Void> initTask = new Task<>() {

      @Override
      protected Void call() throws Exception {
        return resetClassloaderAndGroovy(parentClassLoader);
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

  private void printVersionInfoToConsole() {
    String greeting = "* Groovy " + GroovySystem.getVersion() + " *";
    String surround = getStars(greeting.length());
    console.appendFx(surround, true);
    console.appendFx(greeting, true);
    console.appendFx(surround + "\n>", false);
  }

  @Nullable
  private Void resetClassloaderAndGroovy(GroovyClassLoader parentClassLoader) throws Exception {
    try {

      if (gui.getInoutComponent() == null) {
        log.warn("InoutComponent is null, timing is off");
        throw new RuntimeException("resetClassloaderAndGroovy called too soon, InoutComponent is null, timing is off");
      }

      //log.info("USE_GRADLE_CLASSLOADER pref is set to {}", gui.getPrefs().getBoolean(USE_GRADLE_CLASSLOADER, false));

      // TODO: consider making this a configurable option if performance is heavily affected
      CompilerConfiguration config = new CompilerConfiguration(CompilerConfiguration.DEFAULT);
      //automatically apply the @ThreadInterrupt AST transformations on all scripts
      // see https://docs.groovy-lang.org/next/html/documentation/#_safer_scripting for details
      config.addCompilationCustomizers(new ASTTransformationCustomizer(ThreadInterrupt.class));

      boolean useGradleCLassLoader = gui.getPrefs().getBoolean(USE_GRADLE_CLASSLOADER, false);
      if (useGradleCLassLoader) {
        classLoader = new GroovyClassLoader(ClassUtils.getBootstrapClassLoader(), config);
      } else {
        classLoader = new GroovyClassLoader(parentClassLoader, config);
      }

      if (gui.getInoutComponent() != null && gui.getInoutComponent().getRoot() != null) {
        File wd = gui.getInoutComponent().projectDir();
        if (gui.getPrefs().getBoolean(ADD_BUILDDIR_TO_CLASSPATH, true) && wd != null && wd.exists()) {
          // TODO: we should set this from the resolved build
          File classesDir = new File(wd, "build/classes/groovy/main/");
          List<URL> urlList = new ArrayList<>();
          try {
            if (classesDir.exists()) {
              urlList.add(classesDir.toURI().toURL());
            }
            File testClasses = new File(wd, "build/classes/groovy/test/");
            if (testClasses.exists()) {
              urlList.add(testClasses.toURI().toURL());
            }
            File javaClassesDir = new File(wd, "build/classes/java/main");
            if (javaClassesDir.exists()) {
              urlList.add(javaClassesDir.toURI().toURL());
            }
            File javaTestClassesDir = new File(wd, "build/classes/java/test");
            if (javaTestClassesDir.exists()) {
              urlList.add(javaTestClassesDir.toURI().toURL());
            }
          } catch (MalformedURLException e) {
            log.warn("Failed to find classes dir", e);
          }
          if (!urlList.isEmpty()) {
            log.trace("Adding compile dirs to classloader: {}", urlList);
            urlList.forEach(url -> classLoader.addURL(url));
            //classLoader = new URLClassLoader(urlList.toArray(new URL[0]), classLoader);
          }
        }

        if (useGradleCLassLoader) {
          File projectDir = gui.getInoutComponent().projectDir();
          File gradleHome = new File(gui.getPrefs().get(GRADLE_HOME, GradleUtils.locateGradleHome()));
          File gradleFile = new File(projectDir, "build.gradle");
          if (gradleFile.exists() && gradleHome.exists()) {
            log.debug("Parsing build.gradle to use gradle classloader");
            console.appendFx("* Parsing build.gradle to create Gradle classloader...", true);
            var gradleUtils = new GradleUtils(gui);
            gradleUtils.addGradleDependencies(classLoader, console);
            //classLoader = new GroovyClassLoader(gradleUtils.createGradleCLassLoader(classLoader, console));
          } else {
            log.info("Use gradle class loader is set but gradle build file {} does not exist", gradleFile);
          }
        }
      }
      //engine = new GroovyEngineReflection(classLoader);
      engine = new GroovyEngineInvocation(classLoader);

      //gui.guiInteractions.forEach((k,v) -> engine.put(k, v));
      addObjectsToBindings(gui.guiInteractions);
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
    //initGroovy(getStoredRemoteRepositories(), gui.getClass().getClassLoader());
    initGroovy(gui.dynamicClassLoader);
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
    //log.info("engine is {}, gui is {}", engine, gui);
    gui.guiInteractions.forEach(this::addVariableToSession);
    if (additionalParams != null) {
      for (Map.Entry<String, Object> entry : additionalParams.entrySet()) {
        addVariableToSession(entry.getKey(), entry.getValue());
      }
    }
    return engine.eval(script);
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
    try (PrintWriter out = new PrintWriter(System.out);
         PrintWriter err = new PrintWriter(System.err)) {
      running();
      engine.setOutputWriters(out, err);
      log.debug("Running script: {}", script);
      var result = engine.eval(script);
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
    return engine.getContextObjects();
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
      addObjectsToBindings(gui.guiInteractions);
      //gui.guiInteractions.forEach((k,v) -> engine.put(k, v));

      Platform.runLater(() -> {
        console.append(title, true);
        env.addInputHistory(script);
      });
      engine.setOutputWriters(outputWriter, errWriter);
      System.setOut(outStream);
      System.setErr(errStream);
      var result = engine.eval(script);
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
