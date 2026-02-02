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
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.gradle.util.GradleVersion;
import se.alipsa.gade.Gade;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.console.WarningAppenderWriter;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.groovy.resolver.Dependency;
import se.alipsa.groovy.resolver.DependencyResolver;
import se.alipsa.groovy.resolver.MavenRepoLookup;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.util.Map;
import se.alipsa.groovy.resolver.ResolvingException;
import java.util.Objects;
import java.util.HashMap;
import java.util.Properties;
import java.util.stream.Stream;

public class GradleUtils {

  private static final Logger log = LogManager.getLogger(GradleUtils.class);
  private static final String GADE_GRADLE_USER_HOME_ENV = "GADE_GRADLE_USER_HOME";
  private static final String GADE_GRADLE_USER_HOME_PROP = "gade.gradle.userHome";
  private static final String CACHE_SCHEMA = "schema";
  private static final String CACHE_SCHEMA_VERSION = "1";
  private static final String CACHE_FINGERPRINT = "fingerprint";
  private static final String CACHE_DEP_PREFIX = "dep.";
  private static final String CACHE_OUT_PREFIX = "out.";
  private static final String CACHE_CREATED_AT = "createdAtEpochMs";

  private GradleConnector connector;
  private final File projectDir;
  private final File gradleInstallationDir;
  private final File projectLocalGradleUserHome;
  private File gradleUserHomeDir;
  private boolean useCustomGradleUserHome;
  private final String javaHomeOverride;
  private final boolean wrapperAvailable;
  private final List<DistributionMode> distributionOrder = new ArrayList<>();
  private int currentDistributionIndex = 0;
  private volatile ClasspathModel cachedClasspathMain;
  private volatile String cachedClasspathFingerprintMain;
  private volatile ClasspathModel cachedClasspathTest;
  private volatile String cachedClasspathFingerprintTest;

  public GradleUtils(Gade gui) {
    this(null, gui.getInoutComponent().projectDir(), null);
  }

  public GradleUtils(File gradleInstallationDir, File projectDir, String javaHomeOverride) {
    this.gradleInstallationDir = gradleInstallationDir;
    this.projectDir = Objects.requireNonNull(projectDir, "Project dir must be provided");
    this.javaHomeOverride = javaHomeOverride;
    projectLocalGradleUserHome = new File(projectDir, ".gradle-gade-tooling");
    gradleUserHomeDir = resolveGradleUserHomeDir(projectDir, projectLocalGradleUserHome);
    useCustomGradleUserHome = gradleUserHomeDir != null;
    log.debug("Gradle user home set to {} (custom={})", useCustomGradleUserHome ? gradleUserHomeDir.getAbsolutePath() : "~/.gradle", useCustomGradleUserHome);
    connector = GradleConnector.newConnector();
    connector.forProjectDirectory(projectDir);
    if (useCustomGradleUserHome) {
      connector.useGradleUserHomeDir(gradleUserHomeDir);
    }
    wrapperAvailable = new File(projectDir, "gradle/wrapper/gradle-wrapper.properties").exists();
    configureDistribution();
  }

  private static File resolveGradleUserHomeDir(File projectDir, File projectLocalGradleUserHome) {
    String configured = System.getProperty(GADE_GRADLE_USER_HOME_PROP);
    if (configured == null || configured.isBlank()) {
      configured = System.getenv(GADE_GRADLE_USER_HOME_ENV);
    }
    if (configured == null || configured.isBlank() || "default".equalsIgnoreCase(configured)) {
      return null;
    }
    final File userHomeDir;
    if ("project".equalsIgnoreCase(configured) || "project-local".equalsIgnoreCase(configured)) {
      userHomeDir = projectLocalGradleUserHome;
    } else {
      File configuredDir = new File(configured);
      userHomeDir = configuredDir.isAbsolute() ? configuredDir : new File(projectDir, configured);
    }
    if (userHomeDir.exists()) {
      return userHomeDir;
    }
    if (userHomeDir.mkdirs()) {
      return userHomeDir;
    }
    log.warn("Failed to create Gradle user home {} ({}={}, {}={}), falling back to default ~/.gradle",
        userHomeDir,
        GADE_GRADLE_USER_HOME_PROP, System.getProperty(GADE_GRADLE_USER_HOME_PROP),
        GADE_GRADLE_USER_HOME_ENV, System.getenv(GADE_GRADLE_USER_HOME_ENV));
    return null;
  }

  private record ClasspathModel(List<File> dependencies, List<File> outputDirs, boolean fromCache) {}

  private void configureDistribution() {
    distributionOrder.clear();
    // Prefer provided installation if explicitly given, then wrapper, then embedded/tooling API.
    if (gradleInstallationDir != null && gradleInstallationDir.exists()) {
      distributionOrder.add(DistributionMode.INSTALLATION);
    }
    if (wrapperAvailable) {
      distributionOrder.add(DistributionMode.WRAPPER);
    }
    distributionOrder.add(DistributionMode.EMBEDDED);
    currentDistributionIndex = 0;
    applyCurrentDistribution();
  }

  private ProjectConnection connect() {
    return connector.connect();
  }

  private <T> T withConnection(Function<ProjectConnection, T> action) {
    GradleConnectionException last = null;
    boolean cachePurged = false;
    boolean triedDefaultUserHome = false;
    boolean daemonCacheCleared = false;
    while (true) {
      try (ProjectConnection connection = connect()) {
        return action.apply(connection);
      } catch (GradleConnectionException e) {
        last = e;
        if (!triedDefaultUserHome && trySwitchToDefaultUserHome(e)) {
          triedDefaultUserHome = true;
          continue;
        }
        if (!daemonCacheCleared && isDaemonOrCacheCorruption(e)) {
          String errorType = extractErrorType(e);
          log.warn("Detected Gradle daemon or cache corruption ({}), attempting cleanup and retry", errorType, e);
          clearGradleDaemonCache();
          daemonCacheCleared = true;
          applyCurrentDistribution();
          continue;
        }
        if (!cachePurged && shouldPurgeDistributionCache(e)) {
          purgeGradleUserHomeCache();
          cachePurged = true;
          applyCurrentDistribution();
          continue;
        }
        if (currentDistributionIndex < distributionOrder.size() - 1) {
          currentDistributionIndex++;
          log.warn("Gradle connection failed, retrying with fallback distribution {}", distributionOrder.get(currentDistributionIndex), e);
          applyCurrentDistribution();
          continue;
        }
        throw last;
      }
    }
  }

  private boolean trySwitchToDefaultUserHome(Throwable throwable) {
    if (!useCustomGradleUserHome || gradleUserHomeDir == null) {
      return false;
    }
    // Never fall back away from an explicit user-provided Gradle user home path.
    if (!isProjectLocalGradleUserHome()) {
      return false;
    }
    // When the project-local Gradle user home lacks a complete distribution, retrying with the default
    // ~/.gradle is often enough (it may already contain the needed distributions, even in offline setups).
    if (!(shouldPurgeDistributionCache(throwable) || isDistributionInstallFailure(throwable))) {
      return false;
    }
    log.warn("Gradle connection failed using project-local Gradle user home {}, retrying with default ~/.gradle", gradleUserHomeDir, throwable);
    useCustomGradleUserHome = false;
    gradleUserHomeDir = null;
    applyCurrentDistribution();
    return true;
  }

  private boolean isProjectLocalGradleUserHome() {
    return projectLocalGradleUserHome.getAbsolutePath().equals(gradleUserHomeDir.getAbsolutePath());
  }

  private boolean isDistributionInstallFailure(Throwable throwable) {
    Throwable t = throwable;
    while (t != null) {
      String msg = t.getMessage();
      if (msg != null && msg.contains("Could not install Gradle distribution")) {
        return true;
      }
      t = t.getCause();
    }
    return false;
  }

  private void applyCurrentDistribution() {
    if (useCustomGradleUserHome && gradleUserHomeDir != null && !gradleUserHomeDir.exists() && !gradleUserHomeDir.mkdirs()) {
      log.warn("Failed to recreate Gradle user home {}", gradleUserHomeDir);
    }
    connector = GradleConnector.newConnector();
    connector.forProjectDirectory(projectDir);
    if (useCustomGradleUserHome && gradleUserHomeDir != null) {
      connector.useGradleUserHomeDir(gradleUserHomeDir);
    }
    DistributionMode mode = distributionOrder.get(currentDistributionIndex);
    if (useCustomGradleUserHome && gradleUserHomeDir != null) {
      log.info("Using Gradle user home {}", gradleUserHomeDir.getAbsolutePath());
    } else {
      log.info("Using Gradle user home ~/.gradle");
    }
    switch (mode) {
      case INSTALLATION -> {
        connector.useInstallation(gradleInstallationDir);
        log.info("Using Gradle installation at {}", gradleInstallationDir);
      }
      case WRAPPER -> {
        connector.useBuildDistribution();
        log.info("Using Gradle wrapper distribution for {}", projectDir);
      }
      case EMBEDDED -> {
        String version = GradleVersion.current().getVersion();
        connector.useGradleVersion(version);
        log.info("Using embedded/tooling API Gradle version {} for {}", version, projectDir);
      }
    }
  }

  enum DistributionMode {
    INSTALLATION, WRAPPER, EMBEDDED
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
          .setJvmArguments(gradleJvmArgs())
          .setEnvironmentVariables(gradleEnv())
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
    /*
    // Standard out gives better info, consider using the listener for debug mode
    final List<String> dontShow = List.of("Run tasks", "Run build", "Build");
    final ProgressListener listener = progressEvent -> {
      String description = progressEvent.getDescription();
      if (!dontShow.contains(description)) {
        console.appendFx(progressEvent.getDescription(), true);
      }
    };
     */
    var task = new javafx.concurrent.Task<Void>() {
      @Override
      protected Void call() {
        withConnection(connection -> {
          try (OutputStream outputStream = consoleComponent.getOutputStream();
               WarningAppenderWriter err = new WarningAppenderWriter(console);
               PrintStream errStream = new PrintStream(WriterOutputStream.builder().setWriter(err).get())) {
            BuildLauncher build = connection.newBuild()
                .setJvmArguments(gradleJvmArgs())
                .setEnvironmentVariables(gradleEnv());
            //build.addProgressListener(listener);
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
      //console.appendFx("Build finished!", true);
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
      // ExceptionAlert.showAlert("Build failed: " + clazz + ": " + message, exc);
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
    String javaHome = pickJavaHome();
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
    File outPutDir = module.getCompilerOutput().getOutputDir();
    if (outPutDir == null || !outPutDir.exists()) {
      File moduleDir = module.getGradleProject().getProjectDirectory();
      moduleDir = moduleDir != null && moduleDir.exists() ? moduleDir : projectDir;
      outPutDir = new File(moduleDir, "build/classes/groovy/main/");
    }
    return outPutDir;
  }

  public void addGradleDependencies(GroovyClassLoader classLoader, ConsoleTextArea console) {
    addGradleDependencies(classLoader, console, false);
  }

  public void addGradleDependencies(GroovyClassLoader classLoader, ConsoleTextArea console, boolean testContext) {
    long start = System.currentTimeMillis();
    ClasspathModel model = resolveClasspathModel(testContext);
    if (model.fromCache()) {
      console.appendFx("  Using cached Gradle classpath", true);
    }
    for (File f : model.dependencies()) {
      try {
        if (f.exists()) {
          classLoader.addURL(f.toURI().toURL());
        } else {
          log.warn("Dependency file {} does not exist", f);
          console.appendWarningFx("Dependency file " + f + " does not exist");
        }
      } catch (MalformedURLException e) {
        log.warn("Error adding gradle dependency {} to classpath", f);
        console.appendWarningFx("Error adding gradle dependency " + f + " to classpath");
      }
    }
    for (File outputDir : model.outputDirs()) {
      try {
        classLoader.addURL(outputDir.toURI().toURL());
      } catch (MalformedURLException e) {
        log.warn("Error adding gradle output dir {} to classpath", outputDir);
        console.appendWarningFx("Error adding gradle output dir {} " + outputDir + " to classpath");
      }
    }
    long ms = System.currentTimeMillis() - start;
    if (!model.fromCache() && ms > 2_000) {
      console.appendFx("  Gradle classpath resolved in " + (ms / 1000) + "s", true);
    }
  }

  public ClassLoader createGradleCLassLoader(ClassLoader parent, ConsoleTextArea console) {
    List<URL> urls = new ArrayList<>();
    ClasspathModel model = resolveClasspathModel(false);
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

  private ClasspathModel resolveClasspathModel(boolean testContext) {
    String fingerprint = fingerprintProject(testContext);
    if (testContext) {
      ClasspathModel cached = cachedClasspathTest;
      if (cached != null && Objects.equals(fingerprint, cachedClasspathFingerprintTest)) {
        return cached;
      }
      ClasspathModel fromDisk = loadClasspathCache(fingerprint, true);
      if (fromDisk != null) {
        cachedClasspathTest = fromDisk;
        cachedClasspathFingerprintTest = fingerprint;
        return fromDisk;
      }
      ClasspathModel resolved = resolveClasspathViaToolingApi(true);
      cachedClasspathTest = resolved;
      cachedClasspathFingerprintTest = fingerprint;
      storeClasspathCache(fingerprint, resolved.dependencies(), resolved.outputDirs(), true);
      return resolved;
    }
    ClasspathModel cached = cachedClasspathMain;
    if (cached != null && Objects.equals(fingerprint, cachedClasspathFingerprintMain)) {
      return cached;
    }
    ClasspathModel fromDisk = loadClasspathCache(fingerprint, false);
    if (fromDisk != null) {
      cachedClasspathMain = fromDisk;
      cachedClasspathFingerprintMain = fingerprint;
      return fromDisk;
    }
    ClasspathModel resolved = resolveClasspathViaToolingApi(false);
    cachedClasspathMain = resolved;
    cachedClasspathFingerprintMain = fingerprint;
    storeClasspathCache(fingerprint, resolved.dependencies(), resolved.outputDirs(), false);
    return resolved;
  }

  private ClasspathModel resolveClasspathViaToolingApi(boolean testContext) {
    return withConnection(connection -> {
      LinkedHashSet<File> dependencyFiles = new LinkedHashSet<>();
      LinkedHashSet<File> outputDirs = new LinkedHashSet<>();
      IdeaProject project = connection.model(IdeaProject.class)
          .setJvmArguments(gradleJvmArgs())
          .setEnvironmentVariables(gradleEnv())
          .get();
      for (IdeaModule module : project.getModules()) {
        for (IdeaDependency dependency : module.getDependencies()) {
          if (dependency instanceof IdeaSingleEntryLibraryDependency ideaDependency) {
            if (shouldIncludeDependency(dependency, testContext)) {
              dependencyFiles.add(ideaDependency.getFile());
            }
          }
        }
        outputDirs.add(getOutputDir(module));
        if (testContext) {
          File testOut = getTestOutputDir(module);
          if (testOut != null) {
            outputDirs.add(testOut);
          }
        }
      }
      return new ClasspathModel(List.copyOf(dependencyFiles), List.copyOf(outputDirs), false);
    });
  }

  private static boolean shouldIncludeDependency(IdeaDependency dependency, boolean testContext) {
    if (dependency == null || dependency.getScope() == null) {
      return true;
    }
    String scope = dependency.getScope().getScope();
    if (scope == null || scope.isBlank()) {
      return true;
    }
    scope = scope.toUpperCase();
    if ("COMPILE".equals(scope) || "RUNTIME".equals(scope) || "PROVIDED".equals(scope)) {
      return true;
    }
    return testContext && "TEST".equals(scope);
  }

  private File getTestOutputDir(IdeaModule module) {
    if (module == null || module.getCompilerOutput() == null) {
      return null;
    }
    File out = module.getCompilerOutput().getTestOutputDir();
    if (out != null && out.exists()) {
      return out;
    }
    File moduleDir = module.getGradleProject().getProjectDirectory();
    moduleDir = moduleDir != null && moduleDir.exists() ? moduleDir : projectDir;
    File fallback = new File(moduleDir, "build/classes/groovy/test/");
    return fallback.exists() ? fallback : null;
  }

  private String fingerprintProject(boolean testContext) {
    String fingerprint;
    List<Path> tracked = new ArrayList<>();
    tracked.add(projectDir.toPath().resolve("build.gradle"));
    tracked.add(projectDir.toPath().resolve("build.gradle.kts"));
    tracked.add(projectDir.toPath().resolve("settings.gradle"));
    tracked.add(projectDir.toPath().resolve("settings.gradle.kts"));
    tracked.add(projectDir.toPath().resolve("gradle.properties"));
    tracked.add(projectDir.toPath().resolve("gradle").resolve("libs.versions.toml"));
    tracked.add(projectDir.toPath().resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties"));

    long buildSrcLatest = latestModified(projectDir.toPath().resolve("buildSrc"));
    StringBuilder sb = new StringBuilder();
    sb.append("project=").append(projectDir.getAbsolutePath()).append('\n');
    sb.append("testContext=").append(testContext).append('\n');
    for (Path p : tracked) {
      sb.append(p).append('|');
      try {
        if (Files.exists(p)) {
          sb.append(Files.size(p)).append('|').append(Files.getLastModifiedTime(p).toMillis());
        } else {
          sb.append("missing");
        }
      } catch (IOException e) {
        sb.append("error:").append(e.getClass().getSimpleName());
      }
      sb.append('\n');
    }
    sb.append("buildSrcLatest=").append(buildSrcLatest).append('\n');
    fingerprint = sha256Hex(sb.toString());
    return fingerprint;
  }

  private static long latestModified(Path dir) {
    if (dir == null || !Files.exists(dir)) {
      return 0L;
    }
    try (Stream<Path> stream = Files.walk(dir)) {
      return stream
          .filter(Files::isRegularFile)
          .limit(2000)
          .mapToLong(path -> {
            try {
              return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
              return 0L;
            }
          })
          .max()
          .orElse(0L);
    } catch (IOException e) {
      return 0L;
    }
  }

  private static String sha256Hex(String str) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(str.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static File gradleClasspathCacheDir() {
    File dir = new File(getCacheDir(), "gradle-classpath");
    if (!dir.exists() && !dir.mkdirs()) {
      log.warn("Failed to create Gradle classpath cache dir {}", dir);
    }
    return dir;
  }

  File getClasspathCacheFile(boolean testContext) {
    String key = sha256Hex(projectDir.getAbsolutePath());
    String suffix = testContext ? "-test" : "-main";
    return new File(gradleClasspathCacheDir(), key + suffix + ".properties");
  }

  private ClasspathModel loadClasspathCache(String expectedFingerprint, boolean testContext) {
    File cacheFile = getClasspathCacheFile(testContext);
    if (!cacheFile.exists()) {
      return null;
    }
    Properties props = new Properties();
    try (InputStream in = new FileInputStream(cacheFile)) {
      props.load(in);
    } catch (IOException e) {
      log.debug("Failed to read Gradle classpath cache {}", cacheFile, e);
      return null;
    }
    if (!CACHE_SCHEMA_VERSION.equals(props.getProperty(CACHE_SCHEMA))) {
      return null;
    }
    if (!Objects.equals(expectedFingerprint, props.getProperty(CACHE_FINGERPRINT))) {
      return null;
    }
    LinkedHashSet<File> deps = new LinkedHashSet<>();
    LinkedHashSet<File> outs = new LinkedHashSet<>();
    props.stringPropertyNames().stream()
        .filter(key -> key.startsWith(CACHE_DEP_PREFIX))
        .sorted(Comparator.comparingInt(GradleUtils::cacheIndex))
        .forEach(key -> deps.add(new File(props.getProperty(key))));
    props.stringPropertyNames().stream()
        .filter(key -> key.startsWith(CACHE_OUT_PREFIX))
        .sorted(Comparator.comparingInt(GradleUtils::cacheIndex))
        .forEach(key -> outs.add(new File(props.getProperty(key))));
    if (deps.isEmpty() && outs.isEmpty()) {
      return null;
    }
    if (!allExist(deps)) {
      return null;
    }
    return new ClasspathModel(List.copyOf(deps), List.copyOf(outs), true);
  }

  private static int cacheIndex(String key) {
    int dot = key.lastIndexOf('.');
    if (dot < 0 || dot >= key.length() - 1) {
      return Integer.MAX_VALUE;
    }
    try {
      return Integer.parseInt(key.substring(dot + 1));
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }

  private static boolean allExist(LinkedHashSet<File> files) {
    for (File f : files) {
      if (f == null || !f.exists()) {
        return false;
      }
    }
    return true;
  }

  private void storeClasspathCache(String fingerprint, List<File> dependencies, List<File> outputDirs, boolean testContext) {
    File cacheFile = getClasspathCacheFile(testContext);
    Properties props = new Properties();
    props.setProperty(CACHE_SCHEMA, CACHE_SCHEMA_VERSION);
    props.setProperty(CACHE_FINGERPRINT, fingerprint);
    props.setProperty(CACHE_CREATED_AT, String.valueOf(System.currentTimeMillis()));
    int index = 0;
    for (File dep : dependencies) {
      if (dep != null) {
        props.setProperty(CACHE_DEP_PREFIX + index++, dep.getAbsolutePath());
      }
    }
    index = 0;
    for (File out : outputDirs) {
      if (out != null) {
        props.setProperty(CACHE_OUT_PREFIX + index++, out.getAbsolutePath());
      }
    }
    File parentDir = cacheFile.getParentFile();
    if (parentDir == null) {
      log.warn("Cannot write Gradle classpath cache - parent directory is null for {}", cacheFile);
      return;
    }
    File tmp = new File(parentDir, cacheFile.getName() + ".tmp");
    try (OutputStream out = new FileOutputStream(tmp)) {
      props.store(out, "Gade Gradle classpath cache");
      Files.move(tmp.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (Exception e) {
      log.debug("Failed to write Gradle classpath cache {}", cacheFile, e);
      if (tmp.exists() && !tmp.delete()) {
        tmp.deleteOnExit();
      }
    }
  }

  private String[] gradleJvmArgs() {
    List<String> args = new ArrayList<>();
    String javaHome = pickJavaHome();
    args.add("-Dorg.gradle.java.home=" + javaHome);
    args.add("-Dorg.gradle.ignoreInitScripts=true");
    if (useCustomGradleUserHome && gradleUserHomeDir != null) {
      args.add("-Dgradle.user.home=" + gradleUserHomeDir.getAbsolutePath());
    }
    return args.toArray(new String[0]);
  }

  private Map<String, String> gradleEnv() {
    Map<String, String> env = new HashMap<>(System.getenv());
    String javaHome = pickJavaHome();
    env.put("JAVA_HOME", javaHome);
    if (useCustomGradleUserHome && gradleUserHomeDir != null) {
      env.put("GRADLE_USER_HOME", gradleUserHomeDir.getAbsolutePath());
    }
    return env;
  }

  private String pickJavaHome() {
    String javaHome = javaHomeOverride;
    if (javaHome == null || javaHome.isBlank()) {
      javaHome = System.getenv("JAVA_HOME");
    }
    if (javaHome == null || javaHome.isBlank()) {
      javaHome = System.getProperty("java.home");
    }
    if (javaHome != null && !javaHome.isBlank() && !new File(javaHome).exists()) {
      log.warn("Configured JAVA_HOME {} does not exist, falling back to system default", javaHome);
      javaHome = System.getProperty("java.home", "");
    }
    if (javaHome == null || javaHome.isBlank()) {
      javaHome = "";
    }
    return javaHome;
  }

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
    File dir = new File(FileUtils.getUserHome(), ".gade/cache");
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        throw new RuntimeException("Failed to create cache dir " + dir);
      }
    }
    return dir;
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

  private boolean isDaemonOrCacheCorruption(Throwable throwable) {
    Throwable t = throwable;
    while (t != null) {
      String msg = t.getMessage();
      if (msg != null) {
        // Common daemon/cache corruption indicators
        if (msg.contains("Cannot locate manifest for module")) {
          return true;
        }
        if (msg.contains("Could not create service of type ClassLoaderRegistry")) {
          return true;
        }
        if (msg.contains("Could not create an instance of Tooling API implementation")) {
          return true;
        }
      }
      String className = t.getClass().getName();
      if (className.contains("UnknownModuleException")) {
        return true;
      }
      t = t.getCause();
    }
    return false;
  }

  private String extractErrorType(Throwable throwable) {
    Throwable t = throwable;
    while (t != null) {
      String msg = t.getMessage();
      if (msg != null) {
        if (msg.contains("Cannot locate manifest for module")) {
          return "module manifest not found";
        }
        if (msg.contains("Could not create service of type ClassLoaderRegistry")) {
          return "ClassLoaderRegistry creation failed";
        }
        if (msg.contains("Could not create an instance of Tooling API implementation")) {
          return "Tooling API initialization failed";
        }
      }
      String className = t.getClass().getName();
      if (className.contains("UnknownModuleException")) {
        return "unknown module: " + msg;
      }
      t = t.getCause();
    }
    return "unknown corruption";
  }

  private boolean shouldPurgeDistributionCache(Throwable throwable) {
    Throwable t = throwable;
    while (t != null) {
      String msg = t.getMessage();
      if (msg != null && msg.contains("gradle-runtime-api-info")) {
        return true;
      }
      if (t.getClass().getName().contains("UnknownModuleException")) {
        return true;
      }
      t = t.getCause();
    }
    return false;
  }

  private void clearGradleDaemonCache() {
    File gradleHome = useCustomGradleUserHome && gradleUserHomeDir != null
        ? gradleUserHomeDir
        : new File(FileUtils.getUserHome(), ".gradle");

    File daemonDir = new File(gradleHome, "daemon");
    File cachesDir = new File(gradleHome, "caches");

    log.info("Clearing Gradle daemon cache at {}", gradleHome);

    // Stop all Gradle daemons
    try {
      ProcessBuilder pb = new ProcessBuilder();
      String gradleCommand = findGradleCommand();
      if (gradleCommand != null) {
        pb.command(gradleCommand, "--stop");
        pb.directory(projectDir);
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode == 0) {
          log.info("Successfully stopped Gradle daemons");
        } else {
          log.warn("Gradle --stop command exited with code {}", exitCode);
        }
      } else {
        log.debug("No gradle command found, skipping daemon stop");
      }
    } catch (Exception e) {
      log.warn("Failed to stop Gradle daemons, proceeding with cache cleanup", e);
    }

    // Clear daemon directory
    if (daemonDir.exists()) {
      try (Stream<Path> walker = Files.walk(daemonDir.toPath())) {
        walker.sorted((a, b) -> b.compareTo(a))
            .filter(path -> !path.equals(daemonDir.toPath()))
            .forEach(path -> {
              try {
                Files.deleteIfExists(path);
              } catch (IOException ex) {
                log.debug("Failed to delete {}", path, ex);
              }
            });
        log.info("Cleared Gradle daemon cache at {}", daemonDir);
      } catch (IOException e) {
        log.warn("Failed to clear daemon cache at {}", daemonDir, e);
      }
    }

    // Clear version-specific caches that might be corrupted
    if (cachesDir.exists()) {
      try {
        File[] versionDirs = cachesDir.listFiles(f -> f.isDirectory() && f.getName().matches("\\d+\\.\\d+.*"));
        if (versionDirs != null) {
          for (File versionDir : versionDirs) {
            File[] subdirs = versionDir.listFiles(f -> f.isDirectory() &&
                (f.getName().equals("scripts") || f.getName().equals("scripts-remapped")));
            if (subdirs != null) {
              for (File subdir : subdirs) {
                try (Stream<Path> walker = Files.walk(subdir.toPath())) {
                  walker.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try {
                      Files.deleteIfExists(path);
                    } catch (IOException ex) {
                      log.debug("Failed to delete {}", path, ex);
                    }
                  });
                  log.debug("Cleared cache directory {}", subdir);
                } catch (IOException e) {
                  log.debug("Failed to clear cache directory {}", subdir, e);
                }
              }
            }
          }
        }
      } catch (Exception e) {
        log.warn("Failed to clear version-specific caches", e);
      }
    }
  }

  private String findGradleCommand() {
    // Check for gradle wrapper first
    File gradlew = new File(projectDir, "gradlew");
    if (gradlew.exists() && gradlew.canExecute()) {
      return gradlew.getAbsolutePath();
    }

    // Check for gradle in PATH
    String gradleHome = System.getenv("GRADLE_HOME");
    if (gradleHome != null) {
      File gradle = new File(gradleHome, "bin/gradle");
      if (gradle.exists()) {
        return gradle.getAbsolutePath();
      }
    }

    // Try to find gradle on PATH
    String path = System.getenv("PATH");
    if (path != null) {
      for (String dir : path.split(File.pathSeparator)) {
        File gradle = new File(dir, "gradle");
        if (gradle.exists() && gradle.canExecute()) {
          return gradle.getAbsolutePath();
        }
      }
    }

    return null;
  }

  private void purgeGradleUserHomeCache() {
    if (!useCustomGradleUserHome || gradleUserHomeDir == null) {
      log.warn("Skipping purge of Gradle user home because a shared/default location is in use");
      return;
    }
    if (!isProjectLocalGradleUserHome()) {
      log.warn("Skipping purge of Gradle user home {} because it is not project-local", gradleUserHomeDir);
      return;
    }
    if (!projectLocalGradleUserHome.exists()) {
      return;
    }
    // Switch to default user home before purging so we have a better chance of succeeding without downloads.
    useCustomGradleUserHome = false;
    gradleUserHomeDir = null;
    applyCurrentDistribution();

    Path backup = projectLocalGradleUserHome.toPath().resolveSibling(projectLocalGradleUserHome.getName() + "-backup");
    try {
      if (Files.exists(backup)) {
        try (Stream<Path> backupWalker = Files.walk(backup)) {
          backupWalker.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
            try {
              Files.deleteIfExists(path);
            } catch (IOException ex) {
              log.warn("Failed to delete {}", path, ex);
            }
          });
        }
      }
      Files.move(projectLocalGradleUserHome.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
      log.warn("Purged corrupted Gradle user home {}, backup kept at {}", projectLocalGradleUserHome, backup);
      Files.createDirectories(projectLocalGradleUserHome.toPath());
    } catch (IOException ex) {
      log.warn("Failed to purge Gradle user home {}, proceeding with deletion attempt", projectLocalGradleUserHome, ex);
      try (Stream<Path> walker = Files.walk(projectLocalGradleUserHome.toPath())) {
        walker.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
          try {
            Files.deleteIfExists(path);
          } catch (IOException e) {
            log.warn("Failed to delete {}", path, e);
          }
        });
        Files.createDirectories(projectLocalGradleUserHome.toPath());
      } catch (IOException walkEx) {
        log.warn("Failed to delete Gradle user home {}", projectLocalGradleUserHome, walkEx);
      }
    }
  }

  // package-private for tests
  File getGradleUserHomeDir() {
    return gradleUserHomeDir;
  }

  // package-private for tests
  List<DistributionMode> getDistributionOrder() {
    return List.copyOf(distributionOrder);
  }

  // package-private for tests
  String getProjectFingerprint() {
    return fingerprintProject(false);
  }
}
