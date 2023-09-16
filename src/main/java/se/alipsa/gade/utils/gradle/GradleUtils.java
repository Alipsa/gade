package se.alipsa.gade.utils.gradle;

import static se.alipsa.gade.Constants.MavenRepositoryUrl.MAVEN_CENTRAL;
import static se.alipsa.gade.menu.GlobalOptions.GRADLE_HOME;

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
import se.alipsa.gade.model.Dependency;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.gade.utils.MavenRepoLookup;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GradleUtils {

  private static final Logger log = LogManager.getLogger();

  private final GradleConnector connector;

  private final File projectDir;

  public GradleUtils(Gade gui) throws FileNotFoundException {
    this(
        new File(gui.getPrefs().get(GRADLE_HOME, GradleUtils.locateGradleHome())),
        gui.getInoutComponent().projectDir()
    );
  }

  public GradleUtils(File gradleInstallationDir, File projectDir) throws FileNotFoundException {
    if (!gradleInstallationDir.exists()) {
      throw new FileNotFoundException("Gradle home " + gradleInstallationDir + " does not exist");
    }
    if (!projectDir.exists()) {
      throw new FileNotFoundException("Project dir " + projectDir + " does not exist");
    }
    connector = GradleConnector.newConnector();
    connector.useInstallation(gradleInstallationDir);
    connector.forProjectDirectory(projectDir);
    this.projectDir = projectDir;
  }

  public static String locateGradleHome() {
    String gradleHome = System.getProperty("GRADLE_HOME", System.getenv("GRADLE_HOME"));
    if (gradleHome == null) {
      gradleHome = locateGradle();
    }
    return gradleHome;
  }

  private static String locateGradle() {
    String path = System.getenv("PATH");
    String[] pathElements = path.split(System.getProperty("path.separator"));
    for (String elem : pathElements) {
      File dir = new File(elem);
      if (dir.exists()) {
        String[] files = dir.list();
        if (files != null) {
          boolean foundMvn = Arrays.asList(files).contains("gradle");
          if (foundMvn) {
            return dir.getParentFile().getAbsolutePath();
          }
        }
      }
    }
    return "";
  }

  public String getGradleVersion() {
    return GradleVersion.current().getVersion();
  }

  public List<String> getGradleTaskNames() {
    List<GradleTask> tasks = getGradleTasks();
    return tasks.stream().map(Task::getName).collect(Collectors.toList());
  }

  public List<GradleTask> getGradleTasks() {
    List<GradleTask> tasks;
    try (ProjectConnection connection = connector.connect()) {
      GradleProject project = connection.getModel(GradleProject.class);
      tasks = new ArrayList<>(project.getTasks());
    }
    return tasks;
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
          BuildLauncher build = connection.newBuild();
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
    List<File> dependencyFiles = new ArrayList<>();
    try (ProjectConnection connection = connector.connect()) {
      //BuildLauncher build = connection.newBuild();
      //build.forTasks("dependencies").setStandardOutput(System.out).run();
      IdeaProject project = connection.getModel(IdeaProject.class);
      for (IdeaModule module : project.getModules()) {
        for (IdeaDependency dependency : module.getDependencies()) {
          IdeaSingleEntryLibraryDependency ideaDependency = (IdeaSingleEntryLibraryDependency) dependency;
          File file = ideaDependency.getFile();
          dependencyFiles.add(file);
        }
      }
    }
    return dependencyFiles;
  }

  public List<URL> getOutputDirs() throws MalformedURLException {
    List<URL> urls = new ArrayList<>();
    try (ProjectConnection connection = connector.connect()) {
      IdeaProject project = connection.getModel(IdeaProject.class);
      for (IdeaModule module : project.getModules()) {
        urls.add(getOutputDir(module).toURI().toURL());
      }
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
    try (ProjectConnection connection = connector.connect()) {
      IdeaProject project = connection.getModel(IdeaProject.class);
      for (IdeaModule module : project.getModules()) {
        var outputDir = getOutputDir(module);
        try {
          urls.add(outputDir.toURI().toURL());
        } catch (MalformedURLException e) {
          log.warn("Error adding gradle output dir {} to classpath", outputDir);
          console.appendWarningFx("Error adding gradle output dir {} " + outputDir + " to classpath");
        }
      }
    }
    return new URLClassLoader(urls.toArray(new URL[0]), parent);
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

  public static File downloadArtifact(Dependency dependency) throws IOException {
    File cachedFile = cachedFile(dependency);
    if (cachedFile.exists()) {
      return cachedFile;
    }
    String url = MavenRepoLookup.artifactUrl(dependency, MAVEN_CENTRAL.baseUrl);
    URL artifactUrl = new URL(url);
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
}
