package se.alipsa.gade.utils.gradle;

import static se.alipsa.gade.Constants.MavenRepositoryUrl.MAVEN_CENTRAL;
import groovy.lang.GroovyClassLoader;
import javafx.application.Platform;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gradle.tooling.*;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.Task;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.util.GradleVersion;
import se.alipsa.gade.Gade;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.console.WarningAppenderWriter;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.ClasspathCacheManager;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.groovy.resolver.Dependency;
import se.alipsa.groovy.resolver.DependencyResolver;
import se.alipsa.groovy.resolver.MavenRepoLookup;

import java.io.*;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.Optional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.util.Map;
import se.alipsa.groovy.resolver.ResolvingException;
import java.util.Objects;

/**
 * Utility class for Gradle project integration and dependency resolution.
 * <p>
 * Provides Gradle Tooling API access, classpath resolution with caching, daemon management,
 * and artifact downloading from Maven Central.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe for concurrent classpath resolution.
 * Cached classpath models use {@code volatile} fields to ensure safe publication and visibility
 * across threads without synchronization. The cache fingerprints are also volatile to prevent
 * stale reads. Gradle Tooling API connections are not thread-safe and should not be shared
 * across concurrent operations.
 *
 * @see org.gradle.tooling.GradleConnector
 * <p><b>Thread-Safety:</b> This class is thread-safe.</p>
 */
public class GradleUtils {

  private static final Logger log = LogManager.getLogger(GradleUtils.class);

  private final File projectDir;
  private final File projectLocalGradleUserHome;
  private volatile GradleDependencyResolver.ClasspathModel cachedClasspathMain;
  private volatile String cachedClasspathFingerprintMain;
  private volatile GradleDependencyResolver.ClasspathModel cachedClasspathTest;
  private volatile String cachedClasspathFingerprintTest;

  // Extracted helper classes
  private final GradleConfigurationManager configManager;
  private final GradleDistributionManager distributionManager;
  private final GradleDaemonRecovery daemonRecovery;
  private final GradleDependencyResolver dependencyResolver;

  public GradleUtils(Gade gui) {
    this(null, gui.getInoutComponent().projectDir(), null);
  }

  public GradleUtils(File gradleInstallationDir, File projectDir, String javaHomeOverride) {
    this.projectDir = Objects.requireNonNull(projectDir, "Project dir must be provided");
    projectLocalGradleUserHome = new File(projectDir, ".gradle-gade-tooling");

    // Initialize extracted helpers
    configManager = new GradleConfigurationManager(projectDir, projectLocalGradleUserHome, javaHomeOverride);

    boolean wrapperAvailable = new File(projectDir, "gradle/wrapper/gradle-wrapper.properties").exists();
    distributionManager = new GradleDistributionManager(projectDir, gradleInstallationDir,
        wrapperAvailable, configManager);

    daemonRecovery = new GradleDaemonRecovery(projectDir, projectLocalGradleUserHome, configManager);
    dependencyResolver = new GradleDependencyResolver(projectDir);
  }

  private ProjectConnection connect() {
    return distributionManager.getConnector().connect();
  }

  private <T> T withConnection(Function<ProjectConnection, T> action) {
    GradleConnectionException last = null;
    boolean cachePurged = false;
    boolean triedDefaultUserHome = false;
    boolean daemonCacheCleared = false;
    boolean lockFilesChecked = false;
    while (true) {
      try (ProjectConnection connection = connect()) {
        return action.apply(connection);
      } catch (GradleConnectionException e) {
        last = e;
        // Log detailed error information to help diagnose Tooling API issues
        log.debug("Gradle connection failed: {}", e.getMessage());
        if (e.getCause() != null) {
          log.debug("Root cause: {}", e.getCause().getMessage(), e.getCause());
        }
        if (!triedDefaultUserHome && trySwitchToDefaultUserHome(e)) {
          triedDefaultUserHome = true;
          continue;
        }
        if (!daemonCacheCleared && GradleDaemonRecovery.isDaemonOrCacheCorruption(e)) {
          String errorType = GradleDaemonRecovery.extractErrorType(e);
          log.warn("Detected Gradle daemon or cache corruption ({}), attempting cleanup and retry", errorType, e);
          daemonRecovery.clearDaemonCache();
          daemonCacheCleared = true;
          distributionManager.applyCurrentDistribution();
          continue;
        }
        // Check for stale lock files before checking for full corruption
        if (!lockFilesChecked) {
          lockFilesChecked = true;
          Optional<GradleDaemonRecovery.StaleLockFiles> staleLocks =
              GradleDaemonRecovery.findStaleLockFiles(e);
          if (staleLocks.isPresent()) {
            String lockFileList = staleLocks.get().lockFiles().stream()
                .map(File::getName)
                .collect(Collectors.joining(", "));
            log.warn("Found stale lock files for {}: {}", staleLocks.get().distributionName(), lockFileList);

            boolean userConfirmed = Alerts.confirmFx(
                "Stale Lock Files Detected",
                "Found stale lock files blocking Gradle distribution '" + staleLocks.get().distributionName() + "':\n\n" +
                lockFileList + "\n\n" +
                "These lock files may be left over from an interrupted download.\n" +
                "Would you like to delete them and retry?\n\n" +
                "(The distribution itself appears to be complete and intact)"
            );

            if (userConfirmed) {
              boolean deleted = GradleDaemonRecovery.deleteStaleLockFiles(staleLocks.get());
              if (deleted) {
                log.info("Deleted all stale lock files, retrying connection");
                distributionManager.applyCurrentDistribution();
                continue;
              } else {
                log.warn("Failed to delete some lock files");
              }
            }
          }
        }
        if (!cachePurged && GradleDaemonRecovery.shouldPurgeDistributionCache(e)) {
          // Try to find and delete the specific corrupted distribution
          Optional<GradleDaemonRecovery.CorruptedDistribution> corruptedDist =
              GradleDaemonRecovery.findCorruptedDistribution(e);
          if (corruptedDist.isPresent()) {
            log.warn("Found corrupted distribution: {} - {}", corruptedDist.get().name(), corruptedDist.get().reason());
            boolean deleted = GradleDaemonRecovery.deleteCorruptedDistribution(corruptedDist.get());
            if (deleted) {
              log.info("Deleted corrupted distribution, will retry with fresh download");
              cachePurged = true;
              distributionManager.applyCurrentDistribution();
              continue;
            } else {
              log.warn("Failed to delete corrupted distribution, attempting full purge");
            }
          }
          // Fallback to purging project-local Gradle user home if applicable
          daemonRecovery.purgeGradleUserHomeCache(distributionManager);
          cachePurged = true;
          distributionManager.applyCurrentDistribution();
          continue;
        }
        if (distributionManager.tryNextDistribution()) {
          continue;
        }
        // Log the full exception chain before throwing
        log.error("All Gradle distribution attempts failed. Last error:", last);
        Throwable cause = last.getCause();
        int depth = 1;
        while (cause != null && depth < 10) {
          log.error("  Caused by [{}]: {}", depth, cause.getMessage());
          cause = cause.getCause();
          depth++;
        }
        throw last;
      }
    }
  }

  private boolean trySwitchToDefaultUserHome(Throwable throwable) {
    if (!configManager.isUsingCustomGradleUserHome() || configManager.getGradleUserHomeDir() == null) {
      return false;
    }
    // Never fall back away from an explicit user-provided Gradle user home path.
    if (!configManager.isProjectLocalGradleUserHome(projectLocalGradleUserHome)) {
      return false;
    }
    // When the project-local Gradle user home lacks a complete distribution, retrying with the default
    // ~/.gradle is often enough (it may already contain the needed distributions, even in offline setups).
    if (!(GradleDaemonRecovery.shouldPurgeDistributionCache(throwable)
        || GradleDistributionManager.isDistributionInstallFailure(throwable))) {
      return false;
    }
    log.warn("Gradle connection failed using project-local Gradle user home {}, retrying with default ~/.gradle",
        configManager.getGradleUserHomeDir(), throwable);
    configManager.switchToDefaultGradleUserHome();
    distributionManager.applyCurrentDistribution();
    return true;
  }

  public String getGradleVersion() {
    return GradleVersion.current().getVersion();
  }

  public List<String> getGradleTaskNames() {
    List<GradleTask> tasks = getGradleTasks();
    return tasks.stream().map(Task::getName).collect(Collectors.toList());
  }

  public List<GradleTask> getGradleTasks() {
    return withConnection(connection -> {
      GradleProject project = connection.model(GradleProject.class)
          .setJvmArguments(configManager.gradleJvmArgs())
          .setEnvironmentVariables(configManager.gradleEnv())
          .get();
      return new ArrayList<>(project.getTasks());
    });
  }

  public void buildProject(String... tasks) {
    ConsoleComponent consoleComponent = Gade.instance().getConsoleComponent();

    buildProject(consoleComponent, tasks);
  }

  /**
   * @param consoleComponent the ConsoleComponent to use
   * @param tasks   the tasks to run e.g. clean build
   */
  public void buildProject(final ConsoleComponent consoleComponent, String... tasks) {
    final ConsoleTextArea console = consoleComponent.getConsole();
    var task = new javafx.concurrent.Task<Void>() {
      @Override
      protected Void call() {
        withConnection(connection -> {
          try (OutputStream outputStream = consoleComponent.getOutputStream();
               WarningAppenderWriter err = new WarningAppenderWriter(console);
               PrintStream errStream = new PrintStream(WriterOutputStream.builder().setWriter(err).get())) {
            BuildLauncher build = connection.newBuild()
                .setJvmArguments(configManager.gradleJvmArgs())
                .setEnvironmentVariables(configManager.gradleEnv());
            if (tasks.length > 0) {
              build.forTasks(tasks);
            }
            build.setStandardOutput(outputStream);
            build.setStandardError(errStream);
            build.run();
            return null;
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
        return null;
      }
    };

    task.setOnSucceeded(e -> {
      Platform.runLater(consoleComponent::promptAndScrollToEnd);
    });

    task.setOnFailed(e -> {

      Throwable exc = task.getException();
      String clazz = exc.getClass().getName();
      String message = exc.getMessage() == null ? "" : "\n" + exc.getMessage();
      log.warn("Build failed: {}, {}", clazz, message, exc);

      Throwable t = exc;
      String previousMsg = null;
      while (t != null) {
        String msg = t.getMessage();
        if (previousMsg != null && previousMsg.equals(msg)) {
          t = t.getCause();
          continue;
        }
        if (msg == null || msg.isBlank()) {
          t = t.getCause();
          continue;
        }
        if (msg.contains("There were failing tests. See the report at:")) {
          int start = msg.indexOf("file:/");
          int end = msg.indexOf(".html");
          if (start > -1 && end > -1) {
            String url = msg.substring(start);
            Gade.instance().getInoutComponent().viewHtml(url);
          }
        }
        console.appendWarningFx(msg);
        t = t.getCause();
        previousMsg = msg;
      }
      String hint = gradleFailureHint(exc);
      if (hint != null) {
        console.appendWarningFx(hint);
      }
      console.appendWarningFx("Build failed!");
      Platform.runLater(consoleComponent::promptAndScrollToEnd);
    });

    Thread scriptThread = new Thread(task);
    scriptThread.setDaemon(false);
    scriptThread.start();
  }

  private String gradleFailureHint(Throwable throwable) {
    boolean javaMismatch = false;
    boolean toolingApiFailed = false;
    Throwable t = throwable;
    while (t != null) {
      if (t instanceof UnsupportedClassVersionError) {
        javaMismatch = true;
      }
      String msg = t.getMessage();
      if (msg != null) {
        if (msg.contains("requires Java") || msg.contains("Unsupported class file major version")) {
          javaMismatch = true;
        }
        if (msg.contains("Could not create an instance of Tooling API implementation")) {
          toolingApiFailed = true;
        }
      }
      t = t.getCause();
    }
    if (!javaMismatch && !toolingApiFailed) {
      return null;
    }
    String javaHome = configManager.pickJavaHome();
    if (javaMismatch) {
      return "Hint: The selected Java runtime (" + javaHome + ") is likely too old for this Gradle version. "
          + "Select a JDK 17+ under Runtimes (or set JAVA_HOME) and retry.";
    }
    return "Hint: This can be caused by a corrupted Gradle distribution cache. "
        + "Try deleting the matching folder under ~/.gradle/wrapper/dists and retry.";
  }

  public List<String> getProjectDependencyNames() {
    return getProjectDependencies().stream()
        .map(File::getName)
        .collect(Collectors.toList());

  }

  public List<File> getProjectDependencies() {
    return resolveClasspathModel(false).dependencies();
  }

  public List<URL> getOutputDirs() throws MalformedURLException {
    List<URL> urls = new ArrayList<>();
    for (File out : resolveClasspathModel(false).outputDirs()) {
      urls.add(out.toURI().toURL());
    }
    return urls;
  }

  public File getOutputDir(IdeaModule module) {
    return dependencyResolver.getOutputDir(module);
  }

  public void addGradleDependencies(GroovyClassLoader classLoader, PrintWriter console, boolean testContext) {
    long start = System.currentTimeMillis();
    GradleDependencyResolver.ClasspathModel model = resolveClasspathModel(testContext);
    if (model.fromCache()) {
      console.println("  Using cached Gradle classpath");
    }
    for (File f : model.dependencies()) {
      try {
        if (f.exists()) {
          classLoader.addURL(f.toURI().toURL());
        } else {
          log.warn("Dependency file {} does not exist", f);
          console.println("Dependency file " + f + " does not exist");
        }
      } catch (MalformedURLException e) {
        log.warn("Error adding gradle dependency {} to classpath", f);
        console.println("Error adding gradle dependency " + f + " to classpath");
      }
    }
    for (File outputDir : model.outputDirs()) {
      try {
        classLoader.addURL(outputDir.toURI().toURL());
      } catch (MalformedURLException e) {
        log.warn("Error adding gradle output dir {} to classpath", outputDir);
        console.println("Error adding gradle output dir {} " + outputDir + " to classpath");
      }
    }
    long ms = System.currentTimeMillis() - start;
    if (!model.fromCache() && ms > 2_000) {
      console.println("  Gradle classpath resolved in " + (ms / 1000) + "s");
    }
  }

  public ClassLoader createGradleCLassLoader(ClassLoader parent, ConsoleTextArea console) {
    List<URL> urls = new ArrayList<>();
    GradleDependencyResolver.ClasspathModel model = resolveClasspathModel(false);
    if (model.fromCache()) {
      console.appendFx("  Using cached Gradle classpath", true);
    }
    for (File f : model.dependencies()) {
      try {
        if (f.exists()) {
          urls.add(f.toURI().toURL());
        } else {
          log.warn("Dependency file {} does not exist", f);
          console.appendWarningFx("Dependency file " + f + " does not exist");
        }
      } catch (MalformedURLException e) {
        log.warn("Error adding gradle dependency {} to classpath", f);
        console.appendWarningFx("Error adding gradle dependency " + f + " to classpath");
      }
    }
    for (File out : model.outputDirs()) {
      try {
        urls.add(out.toURI().toURL());
      } catch (MalformedURLException e) {
        log.warn("Error adding gradle output dir {} to classpath", out);
        console.appendWarningFx("Error adding gradle output dir {} " + out + " to classpath");
      }
    }
    return new URLClassLoader(urls.toArray(new URL[0]), parent);
  }

  private GradleDependencyResolver.ClasspathModel resolveClasspathModel(boolean testContext) {
    String fingerprint = fingerprintProject(testContext);
    if (testContext) {
      GradleDependencyResolver.ClasspathModel cached = cachedClasspathTest;
      if (cached != null && Objects.equals(fingerprint, cachedClasspathFingerprintTest)) {
        return cached;
      }
      GradleDependencyResolver.ClasspathModel fromDisk = loadClasspathCache(fingerprint, true);
      if (fromDisk != null) {
        cachedClasspathTest = fromDisk;
        cachedClasspathFingerprintTest = fingerprint;
        return fromDisk;
      }
      GradleDependencyResolver.ClasspathModel resolved = resolveClasspathViaToolingApi(true);
      cachedClasspathTest = resolved;
      cachedClasspathFingerprintTest = fingerprint;
      storeClasspathCache(fingerprint, resolved.dependencies(), resolved.outputDirs(), true);
      return resolved;
    }
    GradleDependencyResolver.ClasspathModel cached = cachedClasspathMain;
    if (cached != null && Objects.equals(fingerprint, cachedClasspathFingerprintMain)) {
      return cached;
    }
    GradleDependencyResolver.ClasspathModel fromDisk = loadClasspathCache(fingerprint, false);
    if (fromDisk != null) {
      cachedClasspathMain = fromDisk;
      cachedClasspathFingerprintMain = fingerprint;
      return fromDisk;
    }
    GradleDependencyResolver.ClasspathModel resolved = resolveClasspathViaToolingApi(false);
    cachedClasspathMain = resolved;
    cachedClasspathFingerprintMain = fingerprint;
    storeClasspathCache(fingerprint, resolved.dependencies(), resolved.outputDirs(), false);
    return resolved;
  }

  private GradleDependencyResolver.ClasspathModel resolveClasspathViaToolingApi(boolean testContext) {
    return withConnection(connection -> {
      IdeaProject project = connection.model(IdeaProject.class)
          .setJvmArguments(configManager.gradleJvmArgs())
          .setEnvironmentVariables(configManager.gradleEnv())
          .get();
      return dependencyResolver.resolveFromIdeaProject(project, testContext);
    });
  }

  private String fingerprintProject(boolean testContext) {
    List<Path> tracked = new ArrayList<>();
    tracked.add(projectDir.toPath().resolve("build.gradle"));
    tracked.add(projectDir.toPath().resolve("build.gradle.kts"));
    tracked.add(projectDir.toPath().resolve("settings.gradle"));
    tracked.add(projectDir.toPath().resolve("settings.gradle.kts"));
    tracked.add(projectDir.toPath().resolve("gradle.properties"));
    tracked.add(projectDir.toPath().resolve("gradle").resolve("libs.versions.toml"));
    tracked.add(projectDir.toPath().resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties"));

    long buildSrcLatest = ClasspathCacheManager.latestModified(projectDir.toPath().resolve("buildSrc"));
    Map<String, String> extra = new LinkedHashMap<>();
    extra.put("buildSrcLatest", String.valueOf(buildSrcLatest));
    return ClasspathCacheManager.computeFingerprint(projectDir, testContext, tracked, extra);
  }

  private static File gradleClasspathCacheDir() {
    File dir = new File(getCacheDir(), "gradle-classpath");
    if (!dir.exists() && !dir.mkdirs()) {
      log.warn("Failed to create Gradle classpath cache dir {}", dir);
    }
    return dir;
  }

  File getClasspathCacheFile(boolean testContext) {
    String key = ClasspathCacheManager.sha256Hex(projectDir.getAbsolutePath());
    String suffix = testContext ? "-test" : "-main";
    return new File(gradleClasspathCacheDir(), key + suffix + ".properties");
  }

  private GradleDependencyResolver.ClasspathModel loadClasspathCache(String expectedFingerprint, boolean testContext) {
    File cacheFile = getClasspathCacheFile(testContext);
    ClasspathCacheManager.CachedClasspath cached = ClasspathCacheManager.load(cacheFile, expectedFingerprint, true);
    if (cached == null) {
      return null;
    }
    return new GradleDependencyResolver.ClasspathModel(cached.dependencies(), cached.outputDirs(), true);
  }


  private void storeClasspathCache(String fingerprint, List<File> dependencies, List<File> outputDirs, boolean testContext) {
    File cacheFile = getClasspathCacheFile(testContext);
    ClasspathCacheManager.store(cacheFile, fingerprint, dependencies, outputDirs, "Gade Gradle classpath cache");
  }

  // Static utility methods for artifact management

  public static void purgeCache(Dependency dependency) {
    File cachedFile = cachedFile(dependency);
    if (cachedFile.exists()) {
      if (cachedFile.delete()) {
        return;
      }
      log.info("Failed to delete {}, it will be purged on application exit", dependency);
      cachedFile.deleteOnExit();
    }
  }

  public static File getCacheDir() {
    return ClasspathCacheManager.getCacheDir();
  }

  public static File cachedFile(Dependency dependency) {
    String subDir = MavenRepoLookup.subDir(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
    String fileName = MavenRepoLookup.jarFile(dependency.getArtifactId(), dependency.getVersion());
    return new File(getCacheDir(), subDir + fileName);
  }

  public static File downloadArtifact(Dependency dependency) throws IOException, URISyntaxException {
    File cachedFile = cachedFile(dependency);
    if (cachedFile.exists()) {
      return cachedFile;
    }
    String url = MavenRepoLookup.artifactUrl(dependency, MAVEN_CENTRAL.baseUrl);
    URL artifactUrl = new URI(url).toURL();
    File parentDir = cachedFile.getParentFile();
    if (parentDir == null) {
      throw new IOException("Cannot cache artifact - parent directory is null for " + cachedFile);
    }
    if (!parentDir.exists()) {
      if (!parentDir.mkdirs()) {
        throw new IOException("Failed to create directory " + parentDir);
      }
    }
    if (!cachedFile.createNewFile()) {
      throw new IOException("Failed to create file " + cachedFile);
    }
    ReadableByteChannel readableByteChannel = Channels.newChannel(artifactUrl.openStream());
    try (FileOutputStream fileOutputStream = new FileOutputStream(cachedFile)) {
      fileOutputStream.getChannel()
          .transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
    }
    return cachedFile;
  }

  public static void addDependencies(Dependency dependency) {
    DependencyResolver resolver = new DependencyResolver(Gade.instance().dynamicClassLoader);
    try {
      resolver.addDependency(dependency);
    } catch (ResolvingException e) {
      ExceptionAlert.showAlert("Failed to add dependency " + dependency + " to classpath", e);
    }
  }

  // Package-private accessors for tests

  File getGradleUserHomeDir() {
    return configManager.getGradleUserHomeDir();
  }

  List<DistributionMode> getDistributionOrder() {
    // Convert from internal DistributionMode to public one for backward compatibility
    return distributionManager.getDistributionOrder().stream()
        .map(mode -> switch (mode) {
          case INSTALLATION -> DistributionMode.INSTALLATION;
          case WRAPPER -> DistributionMode.WRAPPER;
          case EMBEDDED -> DistributionMode.EMBEDDED;
        })
        .toList();
  }

  String getProjectFingerprint() {
    return fingerprintProject(false);
  }

  /**
   * Exposes the DistributionMode enum at the GradleUtils level for backward compatibility.
   */
  public enum DistributionMode {
    INSTALLATION, WRAPPER, EMBEDDED
  }
}
