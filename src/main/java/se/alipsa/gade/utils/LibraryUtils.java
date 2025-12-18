package se.alipsa.gade.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.inout.PackagesTab;
import se.alipsa.gade.model.Library;
import se.alipsa.gade.runtime.RuntimeConfig;
import se.alipsa.gade.runtime.RuntimeType;
import se.alipsa.gade.utils.gradle.GradleUtils;
import se.alipsa.gade.runtime.MavenResolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.util.*;

public class LibraryUtils {

  private static final Logger LOG = LogManager.getLogger(LibraryUtils.class);

  /**
   * Collect libraries that are available to the current runtime.
   * <p>
   * Sources:
   * <ul>
   *   <li>Gradle runtime: dependencies resolved from the Gradle project.</li>
   *   <li>Maven runtime: dependencies listed in pom.xml (non-test scope).</li>
   *   <li>Custom runtime: configured coordinates and additional jars.</li>
   *   <li>All runtimes: jars on the runtime classloader (includes @Grab / grapes downloads).</li>
   * </ul>
   *
   * @param gui the running Gade instance providing runtime and project context
   * @return a set of detected libraries
   */
  public static Set<Library> getAvailableLibraries(Gade gui) {
    RuntimeConfig runtime = gui.getActiveRuntime();
    if (runtime == null) {
      return Collections.emptySet();
    }
    File projectDir = gui.getProjectDir();
    Map<String, Library> libraries = new HashMap<>();

    switch (runtime.getType()) {
      case GRADLE -> addGradleDependencies(gui, libraries);
      case MAVEN -> addMavenDependencies(projectDir, libraries);
      case CUSTOM -> addCustomDependencies(runtime, libraries);
      case GADE -> { /* fall through to classloader scan */ }
    }

    addClassLoaderJars(gui.getConsoleComponent().getClassLoader(), libraries, new HashSet<>());

    return new HashSet<>(libraries.values());
  }

  public static String getPackage(String fullPackageName) {
    if (fullPackageName.contains(":")) {
      return fullPackageName.split(":")[1];
    }
    return fullPackageName;
  }

  public static String getGroup(String fullPackageName) {
    if (fullPackageName.contains(":")) {
      return fullPackageName.split(":")[0];
    }
    return "";
  }

  public static void loadOrUnloadLibrary(ConsoleComponent console, PackagesTab.AvailablePackage pkg, Boolean isLoaded) throws Exception {
    // TODO not sure what to do here, comment out the stuff from Ride
    try {
      //Object result;
      if (isLoaded) {
        console.addOutput("Packages", "loading package " + pkg.getLibrary().getFullName(), true, true);
        //result = console.runScript("library('" + pkg.getLibrary().getFullName() + "')");
      } else {
        console.addOutput("Packages", "unloading package " + pkg.getLibrary().getFullName(), true, true);
        //result = console.runScript("detach('package:" + pkg.getLibrary().getPackageName() + "')");
      }
      //LOG.info(result);
    } catch (Exception e) {
      String action = isLoaded ? "load" : "unload";
      throw new Exception("Failed to " + action + " library", e);
    }
  }

  static void addGradleDependencies(Gade gui, Map<String, Library> libraries) {
    File projectDir = gui.getInoutComponent().projectDir();
    String javaHome = gui.getRuntimeManager().getSelectedRuntime(projectDir).getJavaHome();
    GradleUtils gradleUtils = new GradleUtils(null, projectDir, javaHome);
    gradleUtils.getProjectDependencies().forEach(file -> addLibraryFromFile(file, libraries));
  }

  static void addMavenDependencies(File projectDir, Map<String, Library> libraries) {
    if (projectDir == null) {
      return;
    }
    File pom = new File(projectDir, "pom.xml");
    if (!pom.exists()) {
      return;
    }
    try {
      MavenResolver.dependenciesFromPom(pom).forEach(dep -> addLibraryFromCoordinate(dep, libraries));
    } catch (Exception e) {
      LOG.warn("Failed to parse pom dependencies from {}", pom, e);
    }
  }

  static void addCustomDependencies(RuntimeConfig runtime, Map<String, Library> libraries) {
    runtime.getDependencies().forEach(dep -> addLibraryFromCoordinate(dep, libraries));
    runtime.getAdditionalJars().forEach(path -> addLibraryFromFile(new File(path), libraries));
  }

  static void addClassLoaderJars(ClassLoader classLoader, Map<String, Library> libraries, Set<ClassLoader> visited) {
    if (classLoader == null) {
      return;
    }
    if (!visited.add(classLoader)) {
      return;
    }
    if (classLoader instanceof java.net.URLClassLoader ucl) {
      Arrays.stream(ucl.getURLs()).forEach(url -> {
        try {
          File f = new File(url.toURI());
          addLibraryFromFile(f, libraries);
        } catch (URISyntaxException e) {
          LOG.debug("Failed to convert url {} to file", url, e);
        }
      });
    }
    addClassLoaderJars(classLoader.getParent(), libraries, visited);
  }

  private static void addLibraryFromCoordinate(String coordinate, Map<String, Library> libraries) {
    Library lib = libraryFromCoordinate(coordinate);
    if (lib == null) {
      return;
    }
    String key = lib.getFullName() + ":" + lib.getVersion();
    libraries.putIfAbsent(key, lib);
  }

  private static void addLibraryFromFile(File file, Map<String, Library> libraries) {
    if (file == null || !file.exists()) {
      return;
    }
    Library lib = parseLibraryFromPath(file.getAbsolutePath());
    if (lib != null) {
      String key = lib.getFullName() + ":" + lib.getVersion();
      libraries.putIfAbsent(key, lib);
    }
  }

  /**
   * Parse a Maven/Gradle style coordinate {@code group:artifact[:version]} into a Library.
   *
   * @param coordinate the coordinate string
   * @return a Library or null if the coordinate is malformed
   */
  public static Library libraryFromCoordinate(String coordinate) {
    if (coordinate == null || coordinate.isBlank()) {
      return null;
    }
    String[] parts = coordinate.split(":");
    if (parts.length < 2) {
      LOG.debug("Ignoring malformed coordinate {}", coordinate);
      return null;
    }
    String group = parts[0];
    String artifact = parts[1];
    String version = parts.length > 2 ? parts[2] : "";
    return new Library(artifact, group, artifact, version);
  }

  /**
   * Parse a library from a Gradle/Maven/grapes style file path.
   * Supported formats:
   * <ul>
   *   <li>Gradle cache paths (modules-2/files-2.1/group/artifact/version/...)</li>
   *   <li>Maven repository paths (~/.m2/repository/group/path/artifact/version/...)</li>
   *   <li>Grapes cache paths (~/.groovy/grapes/group/artifact/jars/artifact-version.jar)</li>
   *   <li>Fallback: artifact-version.jar</li>
   * </ul>
   *
   * @param path the file path to parse
   * @return a Library or null if it cannot be parsed
   */
  public static Library parseLibraryFromPath(String path) {
    String normalized = path.replace("\\", "/");
    String group = "";
    String artifact;
    String version = "";
    if (normalized.contains("/caches/modules-2/files-2.1/")) {
      int idx = normalized.indexOf("/caches/modules-2/files-2.1/") + "/caches/modules-2/files-2.1/".length();
      String sub = normalized.substring(idx);
      String[] parts = sub.split("/");
      if (parts.length >= 3) {
        group = parts[0];
        artifact = parts[1];
        version = parts[2];
        return new Library(artifact, group, artifact, version);
      }
    }
    if (normalized.contains("/repository/")) {
      int idx = normalized.indexOf("/repository/") + "/repository/".length();
      String sub = normalized.substring(idx);
      String[] parts = sub.split("/");
      if (parts.length >= 3) {
        group = String.join(".", Arrays.asList(parts).subList(0, parts.length - 3));
        artifact = parts[parts.length - 3];
        version = parts[parts.length - 2];
        return new Library(artifact, group, artifact, version);
      }
    }
    if (normalized.contains("/grapes/")) {
      String sub = normalized.substring(normalized.indexOf("/grapes/") + 8);
      String[] parts = sub.split("/");
      if (parts.length >= 4) {
        group = parts[0];
        artifact = parts[1];
        version = parts[3].replace(".jar", "");
        if (version.startsWith(artifact + "-")) {
          version = version.substring(artifact.length() + 1);
        }
        return new Library(artifact, group, artifact, version);
      }
    }
    // fallback to filename parsing: artifact-version.jar
    String fileName = new File(path).getName();
    if (fileName.endsWith(".jar")) {
      fileName = fileName.substring(0, fileName.length() - 4);
    }
    int versionIdx = fileName.lastIndexOf("-");
    if (versionIdx > 0) {
      artifact = fileName.substring(0, versionIdx);
      version = fileName.substring(versionIdx + 1);
    } else {
      artifact = fileName;
    }
    return new Library(artifact, group, artifact, version);
  }
}
