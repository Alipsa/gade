package se.alipsa.grade.utils.gradle;

import static se.alipsa.grade.Constants.MavenRepositoryUrl.MAVEN_CENTRAL;
import static se.alipsa.grade.menu.GlobalOptions.GRADLE_HOME;
import static se.alipsa.grade.utils.FileUtils.getUserHome;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.apache.maven.settings.io.DefaultSettingsWriter;
import org.apache.maven.settings.validation.DefaultSettingsValidator;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.Task;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.gradle.util.GradleVersion;
import se.alipsa.grade.Grade;
import se.alipsa.grade.utils.ConsoleRepositoryEventListener;
import se.alipsa.grade.utils.FileUtils;
import se.alipsa.grade.utils.MavenRepoLookup;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GradleUtils {

  private static final Logger log = LogManager.getLogger();

  private final GradleConnector connector;

  public GradleUtils(Grade gui) throws FileNotFoundException {
    this(
        gui.getInoutComponent().projectDir(),
        new File(gui.getPrefs().get(GRADLE_HOME, GradleUtils.locateGradleHome()))
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
        String [] files = dir.list();
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
    buildProject(null, tasks);
  }

  public void buildProject(ProgressListener progressListener, String... tasks) {
    try(ProjectConnection connection = connector.connect()) {
      BuildLauncher build = connection.newBuild();
      if (progressListener != null) {
        build.addProgressListener(progressListener);
      }
      if (tasks.length > 0) {
        build.forTasks(tasks);
      }
      build.run();
    }
  }

  public List<String> getProjectDependencyNames() {
    return getProjectDependencies().stream()
        .map(File::getName)
        .collect(Collectors.toList());

  }

  public List<File> getProjectDependencies() {
    List<File> dependencyFiles = new ArrayList<>();
    try (ProjectConnection connection = connector.connect()) {
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

  public ClassLoader createGradleCLassLoader(ClassLoader parent) throws MalformedURLException {
    List<URL> urls = new ArrayList<>();
    for (File f : getProjectDependencies()) {
      urls.add(f.toURI().toURL());
    }
    try (ProjectConnection connection = connector.connect()) {
      IdeaProject project = connection.getModel(IdeaProject.class);
      for (IdeaModule module : project.getModules()) {
        urls.add(module.getCompilerOutput().getOutputDir().toURI().toURL());
      }
    }
    return new URLClassLoader(urls.toArray(new URL[0]), parent);
  }

  /**
   * TODO: Change to use ivy instead so all those maven dependencies can be removed (we need ivy anyway)
   * @param groupId is the same as the &lt;groupId&gt; tag in the pom.xml
   * @param artifactId is the same as the &lt;artifactId&gt; tag in the pom.xml
   * @param classifier is typically null, javadoc, sources, dist etc
   * @param extension could be pom, jar, zip etc.
   * @param version is the same as the &lt;version&gt; tag in the pom.xml
   * @return a file pointing to the resolved artifact.
   * @throws ArtifactResolutionException if the artifact does not exist (e.g arguments are wrong) or some transport issue
   */
  public static File resolveArtifact(String groupId, String artifactId, String classifier, String extension, String version) throws ArtifactResolutionException, SettingsBuildingException {
    Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version);
    ArtifactRequest artifactRequest = new ArtifactRequest();
    artifactRequest.setArtifact(artifact);
    artifactRequest.setRepositories(List.of(getCentralMavenRepository()));
    RepositorySystem repositorySystem = getRepositorySystem();
    RepositorySystemSession repositorySystemSession = getRepositorySystemSession(repositorySystem);

    try {
      ArtifactResult artifactResult = repositorySystem.resolveArtifact(repositorySystemSession, artifactRequest);
      Artifact fetchedArtifact = artifactResult.getArtifact();
      if (fetchedArtifact != null){
        return fetchedArtifact.getFile();
      }
    } catch (ArtifactResolutionException e) {
      log.warn("Failed to find artifact in remote repos: {}; groupId={}, artifact = {}, classifier = {}, extension = {}, version = {}",
          e, groupId, artifactId, classifier, extension, version);
      throw e;
    }
    return null;
  }

  public static RemoteRepository getCentralMavenRepository() {
    return new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/")
        .build();
  }

  private static RepositorySystem getRepositorySystem() {
    DefaultServiceLocator serviceLocator = MavenRepositorySystemUtils.newServiceLocator();
    serviceLocator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    serviceLocator.addService(TransporterFactory.class, FileTransporterFactory.class);

    serviceLocator.addService(TransporterFactory.class, HttpTransporterFactory.class);

    serviceLocator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
      @Override
      public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
        log.warn("Error creating Maven service", exception);
      }
    });

    return serviceLocator.getService(RepositorySystem.class);
  }

  private static DefaultRepositorySystemSession getRepositorySystemSession(RepositorySystem system) throws SettingsBuildingException {
    DefaultRepositorySystemSession repositorySystemSession = MavenRepositorySystemUtils.newSession();
    LocalRepository localRepository = getLocalRepository();
    repositorySystemSession.setLocalRepositoryManager(
        system.newLocalRepositoryManager(repositorySystemSession, localRepository));

    repositorySystemSession.setRepositoryListener(new ConsoleRepositoryEventListener());

    return repositorySystemSession;
  }

  public static LocalRepository getLocalRepository() throws SettingsBuildingException {
    Settings settings = getSettings();
    String localRepoPath = settings.getLocalRepository();

    if (localRepoPath != null) {
      localRepoPath = localRepoPath.replace("${user.home}", getUserHome().getAbsolutePath());
    } else {
      localRepoPath = new File(getUserHome(), ".m2/repository").getAbsolutePath();
    }
    return new LocalRepository(localRepoPath);
  }

  private static Settings getSettings() throws SettingsBuildingException {
    DefaultSettingsReader settingsReader = new DefaultSettingsReader();
    DefaultSettingsWriter settingsWriter = new DefaultSettingsWriter();
    DefaultSettingsValidator settingsValidator = new DefaultSettingsValidator();
    DefaultSettingsBuilder defaultSettingsBuilder = new DefaultSettingsBuilder(settingsReader, settingsWriter, settingsValidator);
    DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
    File userSettingsFile = new File(getUserHome(), ".m2/settings.xml");
    if (userSettingsFile.exists()) {
      request.setUserSettingsFile(userSettingsFile);
    } else {
      log.warn("Did not find a settings.xml in {}", userSettingsFile.getAbsolutePath() );
    }
    String m2Home = System.getenv("M2_HOME") != null ? System.getenv("M2_HOME") : System.getenv("MAVEN_HOME");
    if (m2Home != null) {
      File globalSettingsFile = new File(m2Home, "conf/settings.xml");
      if (globalSettingsFile.exists()) {
        request.setGlobalSettingsFile(globalSettingsFile);
      }
    }

    defaultSettingsBuilder.setSettingsWriter(new DefaultSettingsWriter());
    defaultSettingsBuilder.setSettingsReader(new DefaultSettingsReader());
    defaultSettingsBuilder.setSettingsValidator(new DefaultSettingsValidator());
    SettingsBuildingResult build = defaultSettingsBuilder.build(request);
    return build.getEffectiveSettings();
  }

  public static File downloadArtifact(String groupId, String artifactId, String version) throws IOException {
    String url = MavenRepoLookup.artifactUrl(groupId, artifactId, version, MAVEN_CENTRAL.baseUrl);
    URL artifactUrl = new URL(url);
    File file = File.createTempFile(artifactId + "-" + version, ".jar");

    ReadableByteChannel readableByteChannel = Channels.newChannel(artifactUrl.openStream());
    FileOutputStream fileOutputStream = new FileOutputStream(file);
    fileOutputStream.getChannel()
        .transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
    return file;
  }
}
