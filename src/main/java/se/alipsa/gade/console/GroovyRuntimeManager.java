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
import se.alipsa.gade.utils.gradle.GradleUtils;
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
  private Set<Path> testSourceDirectories = Set.of();
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
        ProjectDependencyEntries dependencyEntries = buildProjectDepEntries(targetRuntime, console);
        File projectDir = gui.getInoutComponent() != null ? gui.getInoutComponent().projectDir() : null;
        processRunner = new RuntimeProcessRunner(targetRuntime, buildClassPathEntries(targetRuntime),
            buildGroovyBootstrapEntries(targetRuntime),
            dependencyEntries.mainEntries(), dependencyEntries.testEntries(),
            console, gui.guiInteractions, projectDir);

        activeRuntime = targetRuntime;
        testSourceDirectories = resolveTestSourceDirectories(targetRuntime);
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
   * Builds the JVM {@code -cp} entries for subprocess execution.
   * <p>
   * For <b>Gradle/Maven</b> runtimes, only the runner JAR is included on
   * {@code -cp}. Groovy and project dependencies are loaded dynamically —
   * see {@link #buildGroovyBootstrapEntries(RuntimeConfig)} and
   * {@link #buildProjectDepEntries(RuntimeConfig, ConsoleTextArea)}.
   * <p>
   * For <b>GADE</b> runtime, Groovy/Ivy jars + runner JAR are on {@code -cp}.
   * Users add their own dependencies via {@code @Grab}.
   * <p>
   * For <b>Custom</b> runtime, Groovy/Ivy jars + additional configured jars +
   * classloader URLs + runner JAR are on {@code -cp}.
   *
   * @param runtime the runtime configuration to build classpath for
   * @return list of classpath entry paths
   */
  List<String> buildClassPathEntries(RuntimeConfig runtime) {
    Set<String> entries = new LinkedHashSet<>();

    if (RuntimeType.GRADLE.equals(runtime.getType()) || RuntimeType.MAVEN.equals(runtime.getType())) {
      // Gradle/Maven: runner jar only on -cp; deps loaded dynamically
      addRunnerJar(entries);
    } else {
      // GADE/Custom: Groovy/Ivy + classloader URLs (if Custom) + runner jar on -cp
      addGroovyJarsFromDir(entries);
      if (entries.isEmpty()) {
        addGroovyJarsByClassResolution(entries);
      }

      addDataUtilsJar(entries);

      // For Custom runtime, include additional jars from classloader
      if (classLoader != null && RuntimeType.CUSTOM.equals(runtime.getType())) {
        for (URL url : classLoader.getURLs()) {
          try {
            entries.add(Paths.get(url.toURI()).toFile().getAbsolutePath());
          } catch (Exception e) {
            log.debug("Failed to add classpath url {}", url, e);
          }
        }
      }

      addRunnerJar(entries);
    }

    if (entries.isEmpty()) {
      log.warn("No classpath entries collected for runtime runner");
    } else {
      log.debug("Classpath entries for runtime runner ({}):\n{}", entries.size(), String.join("\n", entries));
    }
    return new ArrayList<>(entries);
  }

  /**
   * Builds the Groovy/Ivy bootstrap entries for the subprocess.
   * <p>
   * For <b>Gradle/Maven</b> runtimes, returns Groovy and Ivy jar paths extracted
   * from the project's resolved dependencies. These are used to create a bootstrap
   * {@link java.net.URLClassLoader} in the subprocess so that a
   * {@code GroovyClassLoader} can be instantiated via reflection.
   * <p>
   * For <b>GADE/Custom</b> runtimes, returns an empty list — Groovy is already
   * on the JVM {@code -cp}.
   *
   * @param runtime the runtime configuration
   * @return list of Groovy/Ivy jar paths for the bootstrap classloader
   */
  List<String> buildGroovyBootstrapEntries(RuntimeConfig runtime) {
    if (!RuntimeType.GRADLE.equals(runtime.getType()) && !RuntimeType.MAVEN.equals(runtime.getType())) {
      return List.of();
    }
    if (classLoader == null) {
      log.warn("No classloader available for building Groovy bootstrap entries");
      return List.of();
    }
    Set<String> entries = new LinkedHashSet<>();
    boolean hasIvy = false;
    for (URL url : classLoader.getURLs()) {
      try {
        String path = Paths.get(url.toURI()).toFile().getAbsolutePath();
        String name = new File(path).getName();
        if (isGroovyOrIvyJar(name)) {
          entries.add(path);
          if (isIvyJar(name)) {
            hasIvy = true;
          }
        }
      } catch (Exception e) {
        log.debug("Failed to convert dependency url {}", url, e);
      }
    }
    // Ivy is needed for @Grab support. If the project's resolved deps don't
    // include Ivy (common in Groovy 4+ where Ivy is optional), fall back to
    // Gade's bundled Ivy jar so @Grab still works in Gradle/Maven mode.
    if (!hasIvy) {
      addIvyFallback(entries);
    }
    addDataUtilsJar(entries);
    if (!entries.isEmpty()) {
      log.debug("Groovy bootstrap entries ({}):\n{}", entries.size(), String.join("\n", entries));
    } else {
      log.warn("No Groovy/Ivy jars found in resolved dependencies for {} runtime", runtime.getType());
    }
    return new ArrayList<>(entries);
  }

  /**
   * Tries to add Ivy jars from Gade's bundled {@code lib/groovy/} directory
   * or by resolving the Ivy class from the host classloader.
   */
  private void addIvyFallback(Set<String> entries) {
    // Try lib/groovy/ directory (distribution mode)
    File libDir = resolveLibDir();
    if (libDir != null) {
      File groovyDir = new File(libDir, "groovy");
      if (groovyDir.isDirectory()) {
        File[] jars = groovyDir.listFiles((dir, name) -> isIvyJar(name));
        if (jars != null && jars.length > 0) {
          for (File jar : jars) {
            entries.add(jar.getAbsolutePath());
          }
          log.debug("Added {} Ivy jar(s) from {} (fallback)", jars.length, groovyDir);
          return;
        }
      }
    }
    // Try class resolution (development mode)
    try {
      Class<?> ivyClass = Class.forName("org.apache.ivy.Ivy");
      URL location = ivyClass.getProtectionDomain().getCodeSource().getLocation();
      if (location != null) {
        entries.add(Paths.get(location.toURI()).toFile().getAbsolutePath());
        log.debug("Added Ivy jar via class resolution (fallback)");
      }
    } catch (ClassNotFoundException e) {
      log.debug("Ivy not available on host classpath, @Grab may not work in subprocess");
    } catch (Exception e) {
      log.debug("Failed to resolve Ivy code source", e);
    }
  }

  /**
   * Builds project dependency entries split into main and test-scope paths for the subprocess.
   * <p>
   * For <b>Gradle/Maven</b> runtimes, this returns:
   * <ul>
   *   <li>Main entries: non-Groovy/Ivy dependencies resolved with {@code testContext=false}</li>
   *   <li>Test entries: test-only delta ({@code testContext=true} minus main entries)</li>
   * </ul>
   * These are used by the subprocess to create a main/test classloader hierarchy.
   * <p>
   * For <b>GADE/Custom</b> runtimes, both lists are empty.
   *
   * @param runtime the runtime configuration
   * @param console the console for any dependency resolution warnings
   * @return main and test dependency path lists
   */
  private ProjectDependencyEntries buildProjectDepEntries(RuntimeConfig runtime, ConsoleTextArea console) throws Exception {
    if (!RuntimeType.GRADLE.equals(runtime.getType()) && !RuntimeType.MAVEN.equals(runtime.getType())) {
      return ProjectDependencyEntries.EMPTY;
    }
    if (classLoader == null) {
      log.warn("No classloader available for building project dependency entries");
      return ProjectDependencyEntries.EMPTY;
    }
    LinkedHashSet<String> mainEntries = collectNonGroovyDependencyEntries(classLoader.getURLs());
    LinkedHashSet<String> testEntries = new LinkedHashSet<>();
    try (GroovyClassLoader testLoader = runtimeClassLoaderFactory.create(runtime, true, console)) {
      testEntries.addAll(collectNonGroovyDependencyEntries(testLoader.getURLs()));
    }
    testEntries.removeAll(mainEntries);
    if (!mainEntries.isEmpty()) {
      log.debug("Main dependency entries ({}):\n{}", mainEntries.size(), String.join("\n", mainEntries));
    }
    if (!testEntries.isEmpty()) {
      log.debug("Test dependency entries ({}):\n{}", testEntries.size(), String.join("\n", testEntries));
    }
    return new ProjectDependencyEntries(new ArrayList<>(mainEntries), new ArrayList<>(testEntries));
  }

  private LinkedHashSet<String> collectNonGroovyDependencyEntries(URL[] urls) {
    LinkedHashSet<String> entries = new LinkedHashSet<>();
    for (URL url : urls) {
      try {
        String path = Paths.get(url.toURI()).toFile().getAbsolutePath();
        if (!isGroovyOrIvyJar(new File(path).getName())) {
          entries.add(path);
        }
      } catch (Exception e) {
        log.debug("Failed to convert dependency url {}", url, e);
      }
    }
    return entries;
  }

  /**
   * Checks if a jar filename belongs to Groovy or Ivy.
   * Used to separate Groovy/Ivy jars (for bootstrap classloader) from project deps.
   */
  private boolean isGroovyOrIvyJar(String fileName) {
    String name = fileName.toLowerCase(Locale.ROOT);
    return name.startsWith("groovy-") || name.startsWith("groovy.")
        || isIvyJar(name);
  }

  /**
   * Checks if a jar filename is an Ivy jar.
   */
  private boolean isIvyJar(String fileName) {
    String name = fileName.toLowerCase(Locale.ROOT);
    return name.startsWith("ivy-") || name.startsWith("ivy.");
  }

  /**
   * Adds the data-utils jar to the classpath entries so that {@code ConnectionInfo}
   * is available in the subprocess.
   * <p>
   * In distribution: scans {@code lib/groovy/} for {@code data-utils-*.jar}.
   * In development: resolves via {@code ConnectionInfo} class code source.
   */
  private void addDataUtilsJar(Set<String> entries) {
    // Try lib/groovy/ directory (distribution mode)
    File libDir = resolveLibDir();
    if (libDir != null) {
      File groovyDir = new File(libDir, "groovy");
      if (groovyDir.isDirectory()) {
        File[] jars = groovyDir.listFiles((dir, name) ->
            name.startsWith("data-utils-") && name.endsWith(".jar"));
        if (jars != null && jars.length > 0) {
          for (File jar : jars) {
            entries.add(jar.getAbsolutePath());
          }
          log.debug("Added {} data-utils jar(s) from {}", jars.length, groovyDir);
          return;
        }
      }
    }
    // Try class resolution (development mode)
    try {
      Class<?> ciClass = Class.forName("se.alipsa.groovy.datautil.ConnectionInfo");
      URL location = ciClass.getProtectionDomain().getCodeSource().getLocation();
      if (location != null) {
        entries.add(Paths.get(location.toURI()).toFile().getAbsolutePath());
        log.debug("Added data-utils jar via class resolution (development mode)");
      }
    } catch (ClassNotFoundException e) {
      log.debug("ConnectionInfo not available on host classpath");
    } catch (Exception e) {
      log.debug("Failed to resolve data-utils code source", e);
    }
  }

  /**
   * Development mode fallback: resolves essential Groovy and Ivy jars from the
   * host classloader by looking up known classes. Used when {@code lib/groovy/}
   * is not available (i.e. running via {@code ./gradlew run}).
   */
  private void addGroovyJarsByClassResolution(Set<String> entries) {
    String[] classNames = {
        "groovy.lang.GroovySystem",                   // groovy (core)
        "groovy.json.JsonSlurper",                    // groovy-json
        "groovy.xml.XmlSlurper",                      // groovy-xml
        "groovy.sql.Sql",                             // groovy-sql
        "groovy.yaml.YamlSlurper",                    // groovy-yaml
        "groovy.console.ui.Console",                  // groovy-console
        "groovy.text.markup.MarkupTemplateEngine",    // groovy-templates
        "groovy.ant.AntBuilder",                      // groovy-ant
        "groovy.swing.SwingBuilder",                  // groovy-swing
        "groovy.transform.ThreadInterrupt",           // groovy-groovydoc
        "org.apache.ivy.Ivy",                         // ivy
    };
    for (String className : classNames) {
      try {
        Class<?> cls = Class.forName(className);
        URL location = cls.getProtectionDomain().getCodeSource().getLocation();
        if (location != null) {
          entries.add(Paths.get(location.toURI()).toFile().getAbsolutePath());
        }
      } catch (ClassNotFoundException e) {
        log.debug("Class {} not found on host classpath, skipping", className);
      } catch (Exception e) {
        log.debug("Failed to resolve code source for {}", className, e);
      }
    }
    if (!entries.isEmpty()) {
      log.debug("Added {} Groovy/Ivy jars via class resolution (development mode)", entries.size());
    }
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
   * Determines whether the given source file should run in test context.
   * This only applies to Maven/Gradle runtimes and never restarts the subprocess.
   */
  boolean resolveTestContextForSource(File sourceFile) {
    if (activeRuntime == null || activeRuntime.getType() == null) {
      return false;
    }
    if (!(RuntimeType.GRADLE.equals(activeRuntime.getType())
        || RuntimeType.MAVEN.equals(activeRuntime.getType()))) {
      return false;
    }
    File projectDir = gui.getProjectDir();
    return isTestSource(projectDir, sourceFile, testSourceDirectories);
  }

  /**
   * Checks if the source file is in a test directory.
   */
  static boolean isTestSource(File projectDir, File sourceFile) {
    return isTestSource(projectDir, sourceFile, Set.of());
  }

  static boolean isTestSource(File projectDir, File sourceFile, Collection<Path> configuredTestSourceDirs) {
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
    if (configuredTestSourceDirs != null) {
      for (Path configured : configuredTestSourceDirs) {
        if (configured == null) {
          continue;
        }
        Path testPath = configured.normalize();
        if (!testPath.isAbsolute()) {
          testPath = projectPath.resolve(testPath).normalize();
        }
        if (sourcePath.startsWith(testPath)) {
          return true;
        }
      }
    }
    return rel.startsWith(Path.of("src", "test")) || rel.startsWith(Path.of("test"));
  }

  private Set<Path> resolveTestSourceDirectories(RuntimeConfig runtime) {
    File projectDir = gui.getProjectDir();
    if (projectDir == null || runtime == null || runtime.getType() == null) {
      return Set.of();
    }
    if (RuntimeType.GRADLE.equals(runtime.getType())) {
      try {
        File gradleInstallationDir = null;
        if (runtime.getBuildToolHome() != null && !runtime.getBuildToolHome().isBlank()) {
          gradleInstallationDir = new File(runtime.getBuildToolHome());
        }
        GradleUtils gradleUtils = new GradleUtils(gradleInstallationDir, projectDir, runtime.getJavaHome());
        Set<Path> testDirs = gradleUtils.getTestSourceDirectories().stream()
            .filter(Objects::nonNull)
            .map(File::toPath)
            .map(path -> path.toAbsolutePath().normalize())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!testDirs.isEmpty()) {
          return testDirs;
        }
      } catch (Exception e) {
        log.debug("Unable to resolve Gradle test source directories, using defaults", e);
      }
      return defaultTestSourceDirectories(projectDir);
    }
    if (RuntimeType.MAVEN.equals(runtime.getType())) {
      return defaultTestSourceDirectories(projectDir);
    }
    return Set.of();
  }

  private Set<Path> defaultTestSourceDirectories(File projectDir) {
    Path projectPath = projectDir.toPath().toAbsolutePath().normalize();
    LinkedHashSet<Path> dirs = new LinkedHashSet<>();
    dirs.add(projectPath.resolve(Path.of("src", "test")).normalize());
    dirs.add(projectPath.resolve(Path.of("test")).normalize());
    return dirs;
  }

  // Accessors for ConsoleComponent

  GroovyClassLoader getClassLoader() {
    return classLoader;
  }

  RuntimeConfig getActiveRuntime() {
    return activeRuntime;
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

  private record ProjectDependencyEntries(List<String> mainEntries, List<String> testEntries) {
    private static final ProjectDependencyEntries EMPTY = new ProjectDependencyEntries(List.of(), List.of());
  }
}
