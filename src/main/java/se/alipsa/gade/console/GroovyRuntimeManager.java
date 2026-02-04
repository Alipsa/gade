package se.alipsa.gade.console;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovySystem;
import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Constants;
import se.alipsa.gade.Gade;
import se.alipsa.gade.runtime.RuntimeClassLoaderFactory;
import se.alipsa.gade.runtime.RuntimeConfig;
import se.alipsa.gade.runtime.RuntimeManager;
import se.alipsa.gade.runtime.RuntimeProcessRunner;
import se.alipsa.gade.runtime.RuntimeSelectionDialog;
import se.alipsa.gade.runtime.RuntimeType;
import se.alipsa.gade.runner.GadeRunnerMain;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.gade.utils.gradle.GradleDaemonRecovery;
import se.alipsa.gi.GuiInteraction;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages Groovy runtime lifecycle including classloader setup, engine initialization,
 * and runtime switching.
 * <p>
 * This class handles:
 * <ul>
 *   <li>Runtime initialization and switching between GADE/Gradle/Maven/Custom</li>
 *   <li>Classloader creation and management</li>
 *   <li>GroovyEngine lifecycle for in-process (GADE) execution</li>
 *   <li>RuntimeProcessRunner lifecycle for subprocess execution</li>
 *   <li>Groovy version detection</li>
 * </ul>
 *
 * @see ConsoleComponent
 * @see RuntimeProcessRunner
 * @see GroovyEngine
 */
final class GroovyRuntimeManager {

  private static final Logger log = LogManager.getLogger(GroovyRuntimeManager.class);

  private final Gade gui;
  private final RuntimeClassLoaderFactory runtimeClassLoaderFactory;

  private GroovyClassLoader classLoader;
  private RuntimeConfig activeRuntime;
  private boolean runtimeTestContext;
  private String cachedGroovyVersion;
  private RuntimeProcessRunner processRunner;
  private GroovyEngine engine;

  /**
   * Creates a new runtime manager.
   *
   * @param gui the main Gade application instance
   */
  GroovyRuntimeManager(Gade gui) {
    this.gui = gui;
    this.runtimeClassLoaderFactory = new RuntimeClassLoaderFactory(gui);
  }

  /**
   * Ensures a valid runtime is selected, falling back to alternatives if needed.
   *
   * @param requestedRuntime the requested runtime, or null to use selected
   * @return the resolved runtime configuration, or null if none available
   */
  RuntimeConfig ensureRuntime(RuntimeConfig requestedRuntime) {
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

  /**
   * Resets the classloader and Groovy engine for the specified runtime.
   *
   * @param runtime the runtime configuration to use
   * @param testContext whether to include test dependencies
   * @param console the console for output messages
   * @return null on success
   * @throws Exception if initialization fails
   */
  Void resetClassloaderAndGroovy(RuntimeConfig runtime, boolean testContext, ConsoleTextArea console) throws Exception {
    RuntimeConfig targetRuntime = runtime;
    boolean retriedWithGade = false;
    boolean corruptionFixAttempted = false;
    while (true) {
      try {
        if (gui.getInoutComponent() == null) {
          log.warn("InoutComponent is null, timing is off");
          throw new RuntimeException("resetClassloaderAndGroovy called too soon, InoutComponent is null");
        }

        if (processRunner != null) {
          processRunner.stop();
        }
        engine = null;
        processRunner = null;

        if (RuntimeType.GRADLE.equals(targetRuntime.getType())) {
          console.appendFx("* Initializing Gradle runtime (first time can take a while)...", true);
        }

        classLoader = runtimeClassLoaderFactory.create(targetRuntime, testContext, console);

        if (RuntimeType.GADE.equals(targetRuntime.getType())) {
          engine = new GroovyEngineReflection(classLoader);
          addObjectsToBindings(gui.guiInteractions);
        } else {
          processRunner = new RuntimeProcessRunner(targetRuntime, buildClassPathEntries(), console);
        }

        activeRuntime = targetRuntime;
        runtimeTestContext = (RuntimeType.GRADLE.equals(targetRuntime.getType())
            || RuntimeType.MAVEN.equals(targetRuntime.getType())) && testContext;
        cachedGroovyVersion = null; // clear cached value since the runtime just changed
        return null;

      } catch (Exception ex) {
        if (!RuntimeType.GADE.equals(targetRuntime.getType()) && !retriedWithGade) {
          // Check for corrupted Gradle distributions (both wrapper and embedded)
          // Only attempt corruption fix once to avoid infinite retry loops
          if (RuntimeType.GRADLE.equals(targetRuntime.getType()) && !corruptionFixAttempted) {
            corruptionFixAttempted = true;
            // Embedded Gradle version from Tooling API (matches gradle-tooling-api dependency)
            String embeddedVersion = "9.3.1";
            List<GradleDaemonRecovery.CorruptedDistribution> corrupted =
                GradleDaemonRecovery.findAllCorruptedDistributions(gui.getProjectDir(), embeddedVersion);

            if (!corrupted.isEmpty()) {
              String distNames = corrupted.stream()
                  .map(GradleDaemonRecovery.CorruptedDistribution::name)
                  .collect(Collectors.joining(", "));
              String reasons = corrupted.stream()
                  .map(d -> "  - " + d.name() + ": " + d.reason())
                  .collect(Collectors.joining("\n"));

              log.warn("Detected {} corrupted Gradle distribution(s): {}", corrupted.size(), distNames);

              boolean userConfirmed = Alerts.confirmFx("Corrupted Gradle Distribution(s)",
                  "The following Gradle distribution(s) appear to be corrupted:\n\n"
                      + reasons + "\n\n"
                      + "Would you like to delete them and retry?\n"
                      + "(The distributions will be re-downloaded)");

              if (userConfirmed) {
                boolean allDeleted = true;
                for (GradleDaemonRecovery.CorruptedDistribution dist : corrupted) {
                  boolean deleted = GradleDaemonRecovery.deleteCorruptedDistribution(dist);
                  if (deleted) {
                    console.appendFx("Deleted corrupted distribution: " + dist.name(), true);
                  } else {
                    console.appendWarningFx("Failed to delete: " + dist.name());
                    allDeleted = false;
                  }
                }
                if (allDeleted) {
                  continue; // Retry with the same runtime after deletion
                } else {
                  console.appendWarningFx("Some distributions could not be deleted. "
                      + "Try Tools > Purge Gradle Cache manually.");
                }
              }
            }
          }

          retriedWithGade = true;
          log.warn("Failed to initialize runtime {}, falling back to Gade runtime: {}",
              targetRuntime.getName(), ex.toString());
          console.appendWarningFx("Runtime '" + targetRuntime.getName()
              + "' is not ready, using Gade runtime instead (" + ex.getMessage() + ")");
          targetRuntime = new RuntimeConfig(RuntimeManager.RUNTIME_GADE, RuntimeType.GADE);
          testContext = false;
          gui.getRuntimeManager().setSelectedRuntime(gui.getProjectDir(), targetRuntime);
          continue;
        }
        if (ex instanceof RuntimeException re) {
          throw new Exception(re);
        }
        throw ex;
      }
    }
  }

  private void addObjectsToBindings(Map<String, GuiInteraction> map)
      throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    for (Map.Entry<String, GuiInteraction> entry : gui.guiInteractions.entrySet()) {
      addVariableToSession(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Runs auto-run scripts (global and project-local) after initialization.
   *
   * @param console the console for output
   * @param executor function to execute scripts silently
   */
  void autoRunScripts(ConsoleTextArea console, ScriptExecutor executor) {
    File file = null;
    boolean wasWaiting = gui.isWaitCursorSet();
    gui.setWaitCursor();
    try {
      if (gui.getPrefs().getBoolean(se.alipsa.gade.menu.GlobalOptions.AUTORUN_GLOBAL, false)) {
        file = new File(gui.getGadeBaseDir(), Constants.AUTORUN_FILENAME);
        if (file.exists()) {
          executor.runScriptSilent(FileUtils.readContent(file));
        }
      }
      if (gui.getPrefs().getBoolean(se.alipsa.gade.menu.GlobalOptions.AUTORUN_PROJECT, false)) {
        file = new File(gui.getInoutComponent().projectDir(), Constants.AUTORUN_FILENAME);
        if (file.exists()) {
          executor.runScriptSilent(FileUtils.readContent(file));
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

  /**
   * Gets the active Groovy version from the runtime classloader.
   *
   * @return the Groovy version string
   */
  String getActiveGroovyVersion() {
    if (cachedGroovyVersion != null) {
      return cachedGroovyVersion;
    }
    if (classLoader == null) {
      cachedGroovyVersion = GroovySystem.getVersion();
      return cachedGroovyVersion;
    }
    // Try to resolve GroovySystem from the active runtime classloader
    try {
      Class<?> gs = classLoader.loadClass("groovy.lang.GroovySystem");
      Object version = gs.getMethod("getVersion").invoke(null);
      cachedGroovyVersion = String.valueOf(version);
      return cachedGroovyVersion;
    } catch (Exception e) {
      log.warn("Failed to resolve Groovy version from runtime classloader, falling back to host GroovySystem", e);
      cachedGroovyVersion = GroovySystem.getVersion();
      return cachedGroovyVersion;
    }
  }

  /**
   * Gets the configured Java version from the runtime's Java home.
   *
   * @return the Java version, or empty if not configured
   */
  Optional<String> getConfiguredJavaVersion() {
    if (activeRuntime == null || activeRuntime.getJavaHome() == null || activeRuntime.getJavaHome().isBlank()) {
      return Optional.empty();
    }
    File releaseFile = new File(activeRuntime.getJavaHome(), "release");
    if (!releaseFile.exists()) {
      log.warn("Java home {} does not contain a release file", activeRuntime.getJavaHome());
      return Optional.empty();
    }
    Properties props = new Properties();
    try (var in = new FileInputStream(releaseFile)) {
      props.load(in);
      String version = props.getProperty("JAVA_VERSION");
      if (version == null || version.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(version.replace("\"", "").trim());
    } catch (IOException e) {
      log.warn("Failed to read Java version from {}", releaseFile, e);
      return Optional.empty();
    }
  }

  /**
   * Checks if the configured Java version differs from the running JVM.
   *
   * @param configuredVersion the configured version string
   * @return true if versions differ
   */
  boolean isConfiguredJavaDifferent(String configuredVersion) {
    try {
      int configuredFeature = Runtime.Version.parse(configuredVersion).feature();
      return configuredFeature != Runtime.version().feature();
    } catch (IllegalArgumentException e) {
      log.debug("Could not parse configured Java version '{}'", configuredVersion, e);
      return !System.getProperty("java.runtime.version").startsWith(configuredVersion);
    }
  }

  /**
   * Builds the classpath entries for subprocess execution.
   *
   * @return list of classpath entry paths
   */
  List<String> buildClassPathEntries() {
    Set<String> entries = new LinkedHashSet<>();
    // Primary source: runtime classloader URLs
    if (classLoader != null) {
      for (URL url : classLoader.getURLs()) {
        try {
          entries.add(Paths.get(url.toURI()).toFile().getAbsolutePath());
        } catch (Exception e) {
          log.debug("Failed to add classpath url {}", url, e);
        }
      }
    }
    // Ensure runner classes are available
    addCodeSource(entries, GadeRunnerMain.class);
    // Jackson is needed for the IPC protocol
    addCodeSource(entries, com.fasterxml.jackson.databind.ObjectMapper.class);
    addCodeSource(entries, com.fasterxml.jackson.core.JsonFactory.class);
    addCodeSource(entries, com.fasterxml.jackson.annotation.JsonCreator.class);
    addCodeSource(entries, com.fasterxml.jackson.core.json.PackageVersion.class);

    if (entries.isEmpty()) {
      log.warn("No classpath entries collected for runtime runner");
    } else {
      log.debug("Classpath entries for runtime runner ({}):\n{}", entries.size(), String.join("\n", entries));
    }
    return new ArrayList<>(entries);
  }

  private void addCodeSource(Set<String> entries, Class<?> cls) {
    try {
      URL location = cls.getProtectionDomain().getCodeSource().getLocation();
      if (location != null) {
        entries.add(Paths.get(location.toURI()).toFile().getAbsolutePath());
      }
    } catch (Exception e) {
      log.debug("Failed adding code source for {}", cls.getName(), e);
    }
  }

  /**
   * Ensures the runtime context matches the source file location (main vs test).
   *
   * @param sourceFile the source file being executed
   * @param console the console for output
   * @throws Exception if context switch fails
   */
  void ensureRuntimeContextForSource(File sourceFile, ConsoleTextArea console) throws Exception {
    if (activeRuntime == null || activeRuntime.getType() == null) {
      return;
    }
    if (!(RuntimeType.GRADLE.equals(activeRuntime.getType())
        || RuntimeType.MAVEN.equals(activeRuntime.getType()))) {
      return;
    }
    File projectDir = gui.getProjectDir();
    boolean wantsTestContext = isTestSource(projectDir, sourceFile);
    if (runtimeTestContext == wantsTestContext) {
      return;
    }
    log.debug("Switching runtime context (testContext={}) for {}", wantsTestContext, sourceFile);
    resetClassloaderAndGroovy(activeRuntime, wantsTestContext, console);
  }

  /**
   * Checks if the source file is in a test directory.
   */
  static boolean isTestSource(File projectDir, File sourceFile) {
    if (projectDir == null || sourceFile == null) {
      return false;
    }
    Path projectPath = projectDir.toPath().toAbsolutePath().normalize();
    Path sourcePath = sourceFile.toPath().toAbsolutePath().normalize();
    if (!sourcePath.startsWith(projectPath)) {
      return false;
    }
    Path rel;
    try {
      rel = projectPath.relativize(sourcePath);
    } catch (IllegalArgumentException e) {
      return false;
    }
    return rel.startsWith(Path.of("src", "test")) || rel.startsWith(Path.of("test"));
  }

  // Accessors for ConsoleComponent

  GroovyClassLoader getClassLoader() {
    return classLoader;
  }

  RuntimeConfig getActiveRuntime() {
    return activeRuntime;
  }

  boolean isRuntimeTestContext() {
    return runtimeTestContext;
  }

  GroovyEngine getEngine() {
    return engine;
  }

  RuntimeProcessRunner getProcessRunner() {
    return processRunner;
  }

  void addVariableToSession(String key, Object value) {
    log.info("adding {} to session", key);
    if (engine != null) {
      engine.addVariableToSession(key, value);
    }
  }

  void removeVariableFromSession(String varName) {
    if (engine != null) {
      engine.removeVariableFromSession(varName);
    }
  }

  Map<String, Object> getContextObjects() {
    log.info("getContextObjects");
    if (activeRuntime == null) {
      log.warn("No active runtime when fetching context objects");
      return Collections.emptyMap();
    }
    if (RuntimeType.GADE.equals(activeRuntime.getType())) {
      if (engine == null) {
        log.warn("Groovy engine not initialized when fetching context objects for {}", activeRuntime.getName());
        return Collections.emptyMap();
      }
      return engine.getContextObjects();
    }
    if (processRunner == null) {
      log.warn("Process runner is not available when fetching bindings for {}", activeRuntime.getName());
      return Collections.emptyMap();
    }
    try {
      Map<String, String> bindings = processRunner.fetchBindings().get();
      return new HashMap<>(bindings);
    } catch (Exception e) {
      log.debug("Failed to get bindings from process runner", e);
      return Collections.emptyMap();
    }
  }

  Object fetchVar(String varName) {
    if (RuntimeType.GADE.equals(activeRuntime.getType())) {
      return engine.fetchVar(varName);
    }
    try {
      Map<String, String> bindings = processRunner.fetchBindings().get();
      return bindings.get(varName);
    } catch (Exception e) {
      log.debug("Failed to fetch var {}", varName, e);
      return null;
    }
  }

  /**
   * Functional interface for silent script execution.
   */
  @FunctionalInterface
  interface ScriptExecutor {
    Object runScriptSilent(String script) throws Exception;
  }
}
