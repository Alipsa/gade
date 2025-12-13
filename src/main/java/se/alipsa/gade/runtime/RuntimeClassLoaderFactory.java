package se.alipsa.gade.runtime;

import static se.alipsa.gade.menu.GlobalOptions.ADD_BUILDDIR_TO_CLASSPATH;
import static se.alipsa.gade.menu.GlobalOptions.GRADLE_HOME;
import static se.alipsa.gade.menu.GlobalOptions.USE_GRADLE_CLASSLOADER;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovySystem;
import groovy.transform.ThreadInterrupt;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.utils.ClassUtils;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.gade.utils.gradle.GradleUtils;
import se.alipsa.gade.runtime.MavenResolver;
import se.alipsa.groovy.resolver.DependencyResolver;
import se.alipsa.groovy.resolver.ResolvingException;

/**
 * Creates Groovy classloaders for the selected runtime.
 */
public class RuntimeClassLoaderFactory {

  private static final Logger log = LogManager.getLogger(RuntimeClassLoaderFactory.class);

  private final Gade gui;

  public RuntimeClassLoaderFactory(Gade gui) {
    this.gui = gui;
  }

  public GroovyClassLoader create(RuntimeConfig runtime, ConsoleTextArea console) throws Exception {
    if (runtime == null) {
      throw new IllegalArgumentException("Runtime must be provided");
    }
    CompilerConfiguration config = new CompilerConfiguration(CompilerConfiguration.DEFAULT);
    config.addCompilationCustomizers(new ASTTransformationCustomizer(ThreadInterrupt.class));

    return switch (runtime.getType()) {
      case GADE -> createGadeClassLoader(config, console);
      case GRADLE -> createGradleClassLoader(config, console);
      case MAVEN -> createMavenClassLoader(config, console);
      case CUSTOM -> createCustomClassLoader(runtime, config, console);
    };
  }

  private GroovyClassLoader createGadeClassLoader(CompilerConfiguration config, ConsoleTextArea console) throws Exception {
    boolean useGradleClassLoader = gui.getPrefs().getBoolean(USE_GRADLE_CLASSLOADER, false);
    GroovyClassLoader loader;
    if (useGradleClassLoader) {
      loader = new GroovyClassLoader(ClassUtils.getBootstrapClassLoader(), config);
    } else {
      loader = new GroovyClassLoader(gui.dynamicClassLoader, config);
    }

    File wd = gui.getInoutComponent() == null ? null : gui.getInoutComponent().projectDir();
    if (gui.getPrefs().getBoolean(ADD_BUILDDIR_TO_CLASSPATH, true) && wd != null && wd.exists()) {
      addBuildDirs(loader, wd);
    }
    if (useGradleClassLoader && wd != null) {
      File gradleHome = new File(gui.getPrefs().get(GRADLE_HOME, GradleUtils.locateGradleHome()));
      File gradleFile = new File(wd, "build.gradle");
      if (gradleFile.exists() && gradleHome.exists()) {
        log.debug("Parsing build.gradle to use gradle classloader");
        console.appendFx("* Parsing build.gradle to create Gradle classloader...", true);
        var gradleUtils = new GradleUtils(gui);
        gradleUtils.addGradleDependencies(loader, console);
      } else {
        log.info("Use gradle class loader is set but gradle build file {} does not exist", gradleFile);
      }
    }
    return loader;
  }

  private GroovyClassLoader createGradleClassLoader(CompilerConfiguration config, ConsoleTextArea console)
      throws FileNotFoundException {
    File projectDir = gui.getProjectDir();
    GroovyClassLoader loader = new GroovyClassLoader(ClassUtils.getBootstrapClassLoader(), config);
    addDefaultGroovyRuntime(loader);
    if (projectDir == null) {
      return loader;
    }
    var gradleUtils = new GradleUtils(
        new File(gui.getPrefs().get(GRADLE_HOME, GradleUtils.locateGradleHome())),
        projectDir
    );
    gradleUtils.addGradleDependencies(loader, console);
    return loader;
  }

  private GroovyClassLoader createMavenClassLoader(CompilerConfiguration config, ConsoleTextArea console) {
    GroovyClassLoader loader = new GroovyClassLoader(ClassUtils.getBootstrapClassLoader(), config);
    addDefaultGroovyRuntime(loader);
    File projectDir = gui.getProjectDir();
    if (projectDir == null) {
      return loader;
    }
    File pom = new File(projectDir, "pom.xml");
    if (!pom.exists()) {
      return loader;
    }
    try {
      MavenResolver.addPomDependenciesTo(loader, pom);
      addMavenOutputs(loader, projectDir);
    } catch (Exception e) {
      log.warn("Failed to resolve Maven dependencies for {}", pom, e);
      console.appendWarningFx("Failed to resolve Maven dependencies: " + e.getMessage());
    }
    return loader;
  }

  private GroovyClassLoader createCustomClassLoader(RuntimeConfig runtime, CompilerConfiguration config,
                                                    ConsoleTextArea console) {
    GroovyClassLoader loader = new GroovyClassLoader(ClassUtils.getBootstrapClassLoader(), config);
    if (runtime.getJavaHome() != null && !runtime.getJavaHome().isBlank()) {
      File javaHome = new File(runtime.getJavaHome());
      if (!javaHome.exists()) {
        console.appendWarningFx("Java home does not exist: " + runtime.getJavaHome());
      }
    }
    if (runtime.getGroovyHome() != null && !runtime.getGroovyHome().isBlank()) {
      addGroovyHomeJars(loader, runtime.getGroovyHome(), console);
    } else {
      addDefaultGroovyRuntime(loader);
    }
    runtime.getAdditionalJars().forEach(path -> addJarOrDir(loader, new File(path), console));
    DependencyResolver resolver = new DependencyResolver(loader);
    for (String dep : runtime.getDependencies()) {
      try {
        resolver.addDependency(dep);
      } catch (ResolvingException e) {
        log.warn("Failed adding dependency {} to runtime {}", dep, runtime.getName(), e);
        console.appendWarningFx("Failed to add dependency " + dep + ": " + e.getMessage());
      }
    }
    return loader;
  }

  private void addBuildDirs(GroovyClassLoader loader, File wd) {
    List<URL> urlList = new ArrayList<>();
    try {
      File classesDir = new File(wd, "build/classes/groovy/main/");
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
    urlList.forEach(loader::addURL);
  }

  private void addGroovyHomeJars(GroovyClassLoader loader, String groovyHome, ConsoleTextArea console) {
    File libDir = new File(groovyHome, "lib");
    if (!libDir.exists()) {
      console.appendWarningFx("Groovy home does not contain a lib dir: " + groovyHome);
      return;
    }
    File[] libs = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
    if (libs == null) {
      return;
    }
    for (File jar : libs) {
      addJarOrDir(loader, jar, console);
    }
  }

  private void addDefaultGroovyRuntime(GroovyClassLoader loader) {
    URL groovyLocation = GroovySystem.class.getProtectionDomain().getCodeSource().getLocation();
    loader.addURL(groovyLocation);
  }

  private void addJarOrDir(GroovyClassLoader loader, File file, ConsoleTextArea console) {
    if (!file.exists()) {
      console.appendWarningFx("Path does not exist: " + file);
      return;
    }
    try {
      loader.addURL(file.toURI().toURL());
    } catch (MalformedURLException e) {
      log.warn("Failed to add {} to classpath", file, e);
    }
  }

  private void addMavenOutputs(GroovyClassLoader loader, File projectDir) {
    List<File> outputs = List.of(
        new File(projectDir, "target/classes"),
        new File(projectDir, "target/test-classes")
    );
    outputs.stream().filter(File::exists).forEach(f -> {
      try {
        loader.addURL(f.toURI().toURL());
      } catch (MalformedURLException e) {
        log.warn("Failed adding output dir {}", f, e);
      }
    });
  }
}
