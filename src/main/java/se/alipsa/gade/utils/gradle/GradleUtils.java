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
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.util.Map;
import se.alipsa.groovy.resolver.ResolvingException;
import java.util.Objects;
import java.util.HashMap;
import java.util.stream.Stream;

public class GradleUtils {

  private static final Logger log = LogManager.getLogger(GradleUtils.class);

  private GradleConnector connector;
  private final File projectDir;
  private final File gradleInstallationDir;
  private final File gradleUserHome;
  private final boolean useCustomGradleUserHome;
  private final String javaHomeOverride;
  private final boolean wrapperAvailable;
  private final List<DistributionMode> distributionOrder = new ArrayList<>();
  private int currentDistributionIndex = 0;

  public GradleUtils(Gade gui) {
    this(null, gui.getInoutComponent().projectDir(), null);
  }

  public GradleUtils(File gradleInstallationDir, File projectDir, String javaHomeOverride) {
    this.gradleInstallationDir = gradleInstallationDir;
    this.projectDir = Objects.requireNonNull(projectDir, "Project dir must be provided");
    this.javaHomeOverride = javaHomeOverride;
    gradleUserHome = new File(projectDir, ".gradle-gade-tooling");
    useCustomGradleUserHome = gradleUserHome.exists() || gradleUserHome.mkdirs();
    if (!useCustomGradleUserHome) {
      log.warn("Failed to create Gradle user home {}, falling back to default ~/.gradle", gradleUserHome);
    }
    log.debug("Gradle user home set to {} (custom={})", gradleUserHome.getAbsolutePath(), useCustomGradleUserHome);
    connector = GradleConnector.newConnector();
    connector.forProjectDirectory(projectDir);
    connector.useGradleUserHomeDir(useCustomGradleUserHome ? gradleUserHome : null);
    wrapperAvailable = new File(projectDir, "gradle/wrapper/gradle-wrapper.properties").exists();
    configureDistribution();
  }

  private void configureDistribution() {
    distributionOrder.clear();
    // Prefer provided installation if explicitly given, then embedded/tooling API, then wrapper.
    if (gradleInstallationDir != null && gradleInstallationDir.exists()) {
      distributionOrder.add(DistributionMode.INSTALLATION);
    }
    distributionOrder.add(DistributionMode.EMBEDDED);
    if (wrapperAvailable) {
      distributionOrder.add(DistributionMode.WRAPPER);
    }
    currentDistributionIndex = 0;
    applyCurrentDistribution();
  }

  private ProjectConnection connect() {
    return connector.connect();
  }

  private <T> T withConnection(Function<ProjectConnection, T> action) {
    boolean retried = false;
    GradleConnectionException last = null;
    boolean cachePurged = false;
    while (true) {
      try (ProjectConnection connection = connect()) {
        return action.apply(connection);
      } catch (GradleConnectionException e) {
        last = e;
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
          retried = true;
          continue;
        }
        throw last;
      }
    }
  }

  private void applyCurrentDistribution() {
    if (useCustomGradleUserHome && !gradleUserHome.exists() && !gradleUserHome.mkdirs()) {
      log.warn("Failed to recreate Gradle user home {}", gradleUserHome);
    }
    connector = GradleConnector.newConnector();
    connector.forProjectDirectory(projectDir);
    connector.useGradleUserHomeDir(useCustomGradleUserHome ? gradleUserHome : null);
    DistributionMode mode = distributionOrder.get(currentDistributionIndex);
    log.info("Using Gradle user home {}", useCustomGradleUserHome ? gradleUserHome.getAbsolutePath() : "~/.gradle");
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

  private enum DistributionMode {
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
        try (ProjectConnection connection = connector.connect();
             OutputStream outputStream = consoleComponent.getOutputStream();
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
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
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

      Throwable t = exc.getCause();
      String previousMsg = null;
      String msg = "";
      while ( t != null) {
        msg = t.getMessage();
        if (previousMsg != null && previousMsg.equals(msg)) {
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
      console.appendWarningFx("Build failed!");
      Platform.runLater(consoleComponent::promptAndScrollToEnd);
      // ExceptionAlert.showAlert("Build failed: " + clazz + ": " + message, exc);
    });

    Thread scriptThread = new Thread(task);
    scriptThread.setDaemon(false);
    scriptThread.start();
  }

  public List<String> getProjectDependencyNames() {
    return getProjectDependencies().stream()
        .map(File::getName)
        .collect(Collectors.toList());

  }

  public List<File> getProjectDependencies() {
    return withConnection(connection -> {
      List<File> dependencyFiles = new ArrayList<>();
      IdeaProject project = connection.model(IdeaProject.class)
          .setJvmArguments(gradleJvmArgs())
          .setEnvironmentVariables(gradleEnv())
          .get();
      for (IdeaModule module : project.getModules()) {
        for (IdeaDependency dependency : module.getDependencies()) {
          IdeaSingleEntryLibraryDependency ideaDependency = (IdeaSingleEntryLibraryDependency) dependency;
          File file = ideaDependency.getFile();
          dependencyFiles.add(file);
        }
      }
      return dependencyFiles;
    });
  }

  public List<URL> getOutputDirs() throws MalformedURLException {
    return withConnection(connection -> {
      List<URL> urls = new ArrayList<>();
      IdeaProject project = connection.model(IdeaProject.class)
          .setJvmArguments(gradleJvmArgs())
          .setEnvironmentVariables(gradleEnv())
          .get();
      for (IdeaModule module : project.getModules()) {
        try {
          urls.add(getOutputDir(module).toURI().toURL());
        } catch (MalformedURLException e) {
          throw new RuntimeException(e);
        }
      }
      return urls;
    });
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
    for (File f : getProjectDependencies()) {
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
    withConnection(connection -> {
      IdeaProject project = connection.model(IdeaProject.class)
          .setJvmArguments(gradleJvmArgs())
          .setEnvironmentVariables(gradleEnv())
          .get();
      for (IdeaModule module : project.getModules()) {
        var outputDir = getOutputDir(module);
        try {
          classLoader.addURL(outputDir.toURI().toURL());
        } catch (MalformedURLException e) {
          log.warn("Error adding gradle output dir {} to classpath", outputDir);
          console.appendWarningFx("Error adding gradle output dir {} " + outputDir + " to classpath");
        }
      }
      return null;
    });
  }

  public ClassLoader createGradleCLassLoader(ClassLoader parent, ConsoleTextArea console) {
    List<URL> urls = new ArrayList<>();
    for (File f : getProjectDependencies()) {
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
    withConnection(connection -> {
      IdeaProject project = connection.model(IdeaProject.class)
          .setJvmArguments(gradleJvmArgs())
          .get();
      for (IdeaModule module : project.getModules()) {
        var outputDir = getOutputDir(module);
        try {
          urls.add(outputDir.toURI().toURL());
        } catch (MalformedURLException e) {
          log.warn("Error adding gradle output dir {} to classpath", outputDir);
          console.appendWarningFx("Error adding gradle output dir {} " + outputDir + " to classpath");
        }
      }
      return null;
    });
    return new URLClassLoader(urls.toArray(new URL[0]), parent);
  }

  private String[] gradleJvmArgs() {
    List<String> args = new ArrayList<>();
    String javaHome = pickJavaHome();
    args.add("-Dorg.gradle.java.home=" + javaHome);
    args.add("-Dorg.gradle.ignoreInitScripts=true");
    if (useCustomGradleUserHome) {
      args.add("-Dgradle.user.home=" + gradleUserHome.getAbsolutePath());
    }
    return args.toArray(new String[0]);
  }

  private Map<String, String> gradleEnv() {
    Map<String, String> env = new HashMap<>(System.getenv());
    String javaHome = pickJavaHome();
    env.put("JAVA_HOME", javaHome);
    if (useCustomGradleUserHome) {
      env.put("GRADLE_USER_HOME", gradleUserHome.getAbsolutePath());
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
    if (!cachedFile.getParentFile().exists()) {
      if (!cachedFile.getParentFile().mkdirs()) {
        throw new IOException("Failed to create directory " + cachedFile.getParentFile());
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

  private void purgeGradleUserHomeCache() {
    if (!useCustomGradleUserHome) {
      log.warn("Skipping purge of Gradle user home because a shared/default location is in use");
      return;
    }
    if (!gradleUserHome.exists()) {
      return;
    }
    Path backup = gradleUserHome.toPath().resolveSibling(gradleUserHome.getName() + "-backup");
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
      Files.move(gradleUserHome.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
      log.warn("Purged corrupted Gradle user home {}, backup kept at {}", gradleUserHome, backup);
      Files.createDirectories(gradleUserHome.toPath());
    } catch (IOException ex) {
      log.warn("Failed to purge Gradle user home {}, proceeding with deletion attempt", gradleUserHome, ex);
      try (Stream<Path> walker = Files.walk(gradleUserHome.toPath())) {
        walker.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
          try {
            Files.deleteIfExists(path);
          } catch (IOException e) {
            log.warn("Failed to delete {}", path, e);
          }
        });
        Files.createDirectories(gradleUserHome.toPath());
      } catch (IOException walkEx) {
        log.warn("Failed to delete Gradle user home {}", gradleUserHome, walkEx);
      }
    }
  }
}
