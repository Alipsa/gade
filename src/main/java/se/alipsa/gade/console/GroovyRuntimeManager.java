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
 *   <li>RuntimeProcessRunner lifecycle for subprocess execution (all runtimes)</li>
 *   <li>Groovy version detection</li>
 * </ul>
 *
 * @see ConsoleComponent
 * @see RuntimeProcessRunner
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
        processRunner = null;

        if (RuntimeType.GRADLE.equals(targetRuntime.getType())) {
          console.appendFx("* Initializing Gradle runtime (first time can take a while)...", true);
        }

        classLoader = runtimeClassLoaderFactory.create(targetRuntime, testContext, console);
        File projectDir = gui.getInoutComponent() != null ? gui.getInoutComponent().projectDir() : null;
        processRunner = new RuntimeProcessRunner(targetRuntime, buildClassPathEntries(), console, gui.guiInteractions, projectDir);

        activeRuntime = targetRuntime;
        runtimeTestContext = (RuntimeType.GRADLE.equals(targetRuntime.getType())
            || RuntimeType.MAVEN.equals(targetRuntime.getType())) && testContext;
        cachedGroovyVersion = null; // clear cached value since the runtime just changed
        return null;

      } catch (Exception ex) {
        if (!RuntimeType.GADE.equals(targetRuntime.getType()) && !retriedWithGade) {
          // Check for corrupted Gradle distributions ONLY if the error indicates corruption
          // Only attempt corruption fix once to avoid infinite retry loops
          if (RuntimeType.GRADLE.equals(targetRuntime.getType()) && !corruptionFixAttempted
              && GradleDaemonRecovery.isDaemonOrCacheCorruption(ex)) {
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
   * <p>
   * Only Groovy, Ivy, and the runner JAR are included. All other libraries
   * (logging, Jackson, Gade application classes, project dependencies) are
   * excluded so the subprocess has a fully isolated classpath. Users add
   * their own dependencies via {@code @Grab}.
   *
   * @return list of classpath entry paths
   */
  List<String> buildClassPathEntries() {
    Set<String> entries = new LinkedHashSet<>();
    addGroovyJarsFromDir(entries);
    addRunnerJar(entries);
    // Fallback: if no directory-based jars found, use classloader URLs (development mode)
    if (entries.isEmpty() && classLoader != null) {
      for (URL url : classLoader.getURLs()) {
        try {
          File file = Paths.get(url.toURI()).toFile();
          String name = file.getName().toLowerCase(java.util.Locale.ROOT);
          if (file.isFile() && name.endsWith(".jar")
              && (name.startsWith("groovy-") || name.startsWith("groovy.") || name.startsWith("ivy-"))) {
            entries.add(file.getAbsolutePath());
          }
        } catch (Exception e) {
          log.debug("Failed to add classpath url {}", url, e);
        }
      }
      addRunnerJar(entries); // retry runner jar for dev mode
    }

    if (entries.isEmpty()) {
      log.warn("No classpath entries collected for runtime runner");
    } else {
      log.debug("Classpath entries for runtime runner ({}):\n{}", entries.size(), String.join("\n", entries));
    }
    return new ArrayList<>(entries);
  }

  /**
   * Resolves the lib/ directory from the Gade jar's code source location.
   * In distribution the Gade jar is in lib/app/, so we go up one level.
   *
   * @return the lib/ directory, or null if it cannot be determined
   */
  private File resolveLibDir() {
    try {
      URL gadeLocation = gui.getClass().getProtectionDomain().getCodeSource().getLocation();
      if (gadeLocation != null) {
        File gadeFile = Paths.get(gadeLocation.toURI()).toFile();
        File parentDir = gadeFile.isDirectory() ? gadeFile : gadeFile.getParentFile();
        // In distribution: Gade jar is in lib/app/, go up to lib/
        if ("app".equals(parentDir.getName()) && parentDir.getParentFile() != null) {
          return parentDir.getParentFile();
        }
        return parentDir;
      }
    } catch (Exception e) {
      log.debug("Failed to resolve lib directory from Gade code source", e);
    }
    return null;
  }

  /**
   * Scans lib/groovy/ for all jar files and adds them to the classpath entries.
   * In distribution mode, all Groovy and Ivy jars live in this directory.
   */
  private void addGroovyJarsFromDir(Set<String> entries) {
    File libDir = resolveLibDir();
    if (libDir == null) {
      return;
    }
    File groovyDir = new File(libDir, "groovy");
    if (groovyDir.isDirectory()) {
      File[] jars = groovyDir.listFiles((dir, name) -> name.endsWith(".jar"));
      if (jars != null) {
        for (File jar : jars) {
          entries.add(jar.getAbsolutePath());
        }
        log.debug("Added {} Groovy/Ivy jars from {}", jars.length, groovyDir);
      }
    }
  }

  /**
   * Locates gade-runner.jar and adds it to the classpath entries.
   * <p>
   * In distribution: scans lib/runtimes/ for gade-runner*.jar.
   * In development: looks in build/libs/ for gade-runner*.jar.
   * Fallback: resolves via GadeRunnerMain code source (for test compatibility).
   */
  private void addRunnerJar(Set<String> entries) {
    // Try distribution lib/runtimes/ directory first
    File libDir = resolveLibDir();
    if (libDir != null) {
      File runtimesDir = new File(libDir, "runtimes");
      if (runtimesDir.isDirectory()) {
        File found = findRunnerJar(runtimesDir);
        if (found != null) {
          entries.add(found.getAbsolutePath());
          return;
        }
      }
      // Also check lib/ directly (backward compatibility)
      File found = findRunnerJar(libDir);
      if (found != null) {
        entries.add(found.getAbsolutePath());
        return;
      }
    }

    // Try build/libs/ directory (development mode: ./gradlew run)
    File buildLibs = new File("build/libs");
    if (buildLibs.isDirectory()) {
      File found = findRunnerJar(buildLibs);
      if (found != null) {
        entries.add(found.getAbsolutePath());
        return;
      }
    }

    // Fallback: resolve via GadeRunnerMain code source (for test compatibility)
    try {
      URL location = GadeRunnerMain.class.getProtectionDomain().getCodeSource().getLocation();
      if (location != null) {
        entries.add(Paths.get(location.toURI()).toFile().getAbsolutePath());
        log.debug("Added runner classes via GadeRunnerMain code source (fallback)");
      }
    } catch (Exception e) {
      log.warn("Failed to locate runner jar or classes", e);
    }
  }

  private File findRunnerJar(File dir) {
    if (dir == null || !dir.isDirectory()) {
      return null;
    }
    File[] matches = dir.listFiles((d, name) ->
        name.startsWith("gade-runner") && name.endsWith(".jar"));
    if (matches != null && matches.length > 0) {
      log.debug("Found runner jar: {}", matches[0]);
      return matches[0];
    }
    return null;
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

  RuntimeProcessRunner getProcessRunner() {
    return processRunner;
  }

  void addVariableToSession(String key, Object value) {
    // Variables are passed as bindings when eval() is called on the processRunner.
    log.debug("addVariableToSession: {} (will be passed via eval bindings)", key);
  }

  void removeVariableFromSession(String varName) {
    // Variables are managed per-eval in the subprocess.
    log.debug("removeVariableFromSession: {} (subprocess manages bindings per-eval)", varName);
  }

  Map<String, Object> getContextObjects() {
    log.info("getContextObjects");
    if (activeRuntime == null) {
      log.warn("No active runtime when fetching context objects");
      return Collections.emptyMap();
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
    if (processRunner == null) {
      log.warn("Process runner is not available when fetching var {}", varName);
      return null;
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
