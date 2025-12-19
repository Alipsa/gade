package se.alipsa.gade.runtime;

import static se.alipsa.gade.menu.GlobalOptions.ADD_BUILDDIR_TO_CLASSPATH;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovySystem;
import groovy.transform.ThreadInterrupt;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.utils.ClassUtils;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.gade.utils.gradle.GradleUtils;
import se.alipsa.gade.utils.maven.MavenClasspathUtils;
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
    return create(runtime, false, console);
  }

  public GroovyClassLoader create(RuntimeConfig runtime, boolean testContext, ConsoleTextArea console) throws Exception {
    if (runtime == null) {
      throw new IllegalArgumentException("Runtime must be provided");
    }
    CompilerConfiguration config = new CompilerConfiguration(CompilerConfiguration.DEFAULT);
    config.addCompilationCustomizers(new ASTTransformationCustomizer(ThreadInterrupt.class));

    GroovyClassLoader loader = switch (runtime.getType()) {
      case GADE -> createGadeClassLoader(config, console);
      case GRADLE -> createGradleClassLoader(config, testContext, console);
      case MAVEN -> createMavenClassLoader(config, testContext, console);
      case CUSTOM -> createCustomClassLoader(runtime, config, console);
    };
    ensureGroovyScriptEngine(loader, runtime, console);
    return loader;
  }

  private GroovyClassLoader createGadeClassLoader(CompilerConfiguration config, ConsoleTextArea console) throws Exception {
    GroovyClassLoader loader = new GroovyClassLoader(gui.dynamicClassLoader, config);

    File wd = gui.getInoutComponent() == null ? null : gui.getInoutComponent().projectDir();
    if (gui.getPrefs().getBoolean(ADD_BUILDDIR_TO_CLASSPATH, true) && wd != null && wd.exists()) {
      addBuildDirs(loader, wd);
    }
    return loader;
  }

  private GroovyClassLoader createGradleClassLoader(CompilerConfiguration config, boolean testContext, ConsoleTextArea console) {
    File projectDir = gui.getProjectDir();
    GroovyClassLoader loader = new GroovyClassLoader(ClassUtils.getBootstrapClassLoader(), config);
    addDefaultGroovyRuntime(loader);
    if (projectDir == null) {
      return loader;
    }
    var gradleUtils = new GradleUtils(
        null,
        projectDir,
        gui.getRuntimeManager().getSelectedRuntime(projectDir).getJavaHome()
    );
    gradleUtils.addGradleDependencies(loader, console, testContext);
    return loader;
  }

  private GroovyClassLoader createMavenClassLoader(CompilerConfiguration config, boolean testContext, ConsoleTextArea console) {
    GroovyClassLoader loader = new GroovyClassLoader(ClassUtils.getBootstrapClassLoader(), config);
    addDefaultGroovyRuntime(loader);
    File projectDir = gui.getProjectDir();
    if (projectDir == null) {
      return loader;
    }
    MavenClasspathUtils.addPomDependenciesTo(loader, projectDir, testContext, console);
    if (gui.getPrefs().getBoolean(ADD_BUILDDIR_TO_CLASSPATH, true)) {
      addMavenOutputs(loader, projectDir, testContext);
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
    if (!runtime.getDependencies().isEmpty()) {
      DependencyResolver resolver = new DependencyResolver(loader);
      for (String dep : runtime.getDependencies()) {
        try {
          resolver.addDependency(dep);
        } catch (ResolvingException e) {
          log.warn("Failed adding dependency {} to runtime {}", dep, runtime.getName(), e);
          console.appendWarningFx("Failed to add dependency " + dep + ": " + e.getMessage());
        }
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

  private void addMavenOutputs(GroovyClassLoader loader, File projectDir, boolean testContext) {
    List<File> outputs = List.of(
        new File(projectDir, "target/classes"),
        new File(projectDir, "target/test-classes")
    );
    outputs.stream()
        .filter(f -> !f.getName().contains("test-classes") || testContext)
        .filter(File::exists)
        .forEach(f -> {
      try {
        loader.addURL(f.toURI().toURL());
      } catch (MalformedURLException e) {
        log.warn("Failed adding output dir {}", f, e);
      }
    });
  }

  private void ensureGroovyScriptEngine(GroovyClassLoader loader, RuntimeConfig runtime, ConsoleTextArea console) {
    try {
      loader.loadClass("org.codehaus.groovy.jsr223.GroovyScriptEngineImpl");
    } catch (ClassNotFoundException e) {
      String runtimeName = runtime == null ? "runtime" : runtime.getName();
      console.appendWarningFx("Runtime '" + runtimeName
          + "' is missing groovy-jsr223; using bundled engine. Add groovy-jsr223 to Groovy home or Dependencies to avoid fallback.");
      log.warn("groovy-jsr223 not found for runtime {}", runtimeName);
      try {
        URL engineLocation = GroovyScriptEngineImpl.class.getProtectionDomain().getCodeSource().getLocation();
        if (engineLocation == null) {
          throw new IllegalStateException("No location found for bundled groovy-jsr223");
        }
        loader.addURL(engineLocation);
        loader.loadClass("org.codehaus.groovy.jsr223.GroovyScriptEngineImpl");
      } catch (Exception ex) {
        String message = "groovy-jsr223 is missing for runtime " + runtimeName
            + "; add org.apache.groovy:groovy-jsr223 to the runtime configuration";
        log.error(message, ex);
        throw new RuntimeException(message, ex);
      }
    }
  }
}
