package se.alipsa.gade.runtime;

import static se.alipsa.gade.menu.GlobalOptions.ADD_BUILDDIR_TO_CLASSPATH;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovySystem;
import groovy.transform.ThreadInterrupt;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.console.ConsoleOutputStream;
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
      case GRADLE -> createGradleClassLoader(runtime, config, testContext, console);
      case MAVEN -> createMavenClassLoader(runtime, config, testContext, console);
      case CUSTOM -> createCustomClassLoader(runtime, config, console);
    };
    return loader;
  }

  private GroovyClassLoader createGadeClassLoader(CompilerConfiguration config, ConsoleTextArea console) throws Exception {
    GroovyClassLoader loader = new GroovyClassLoader(ClassUtils.getBootstrapClassLoader(), config);
    addGroovyAndIvyJars(loader);

    File wd = gui.getInoutComponent() == null ? null : gui.getInoutComponent().projectDir();
    if (gui.getPrefs().getBoolean(ADD_BUILDDIR_TO_CLASSPATH, true) && wd != null && wd.exists()) {
      addBuildDirs(loader, wd);
    }
    return loader;
  }

  /**
   * Adds Groovy runtime jars and Ivy (for @Grab support) to the classloader.
   * <p>
   * In a distribution, Groovy/Ivy jars are in lib/groovy/. In development (./gradlew run),
   * jars are in separate Gradle cache dirs, so we fall back to resolving key classes.
   */
  private void addGroovyAndIvyJars(GroovyClassLoader loader) {
    URL groovyLocation = GroovySystem.class.getProtectionDomain().getCodeSource().getLocation();
    if (groovyLocation == null) {
      log.warn("Cannot determine Groovy jar location");
      return;
    }
    try {
      File groovyJar = new File(groovyLocation.toURI());
      File libDir = groovyJar.getParentFile();
      // In distribution: look for lib/groovy/ directory first
      if (libDir != null && libDir.isDirectory()) {
        File groovyDir = new File(libDir, "groovy");
        if (!groovyDir.isDirectory()) {
          // GroovySystem jar is already in lib/groovy/, so libDir IS the groovy dir
          if (libDir.getName().equals("groovy")) {
            groovyDir = libDir;
          }
        }
        if (groovyDir.isDirectory()) {
          File[] jars = groovyDir.listFiles((dir, name) -> name.endsWith(".jar"));
          if (jars != null && jars.length > 0) {
            for (File jar : jars) {
              loader.addURL(jar.toURI().toURL());
            }
            log.debug("Added {} Groovy/Ivy jars from {}", jars.length, groovyDir);
            return;
          }
        }
        // Fallback: scan lib/ with name filters (backward compatibility)
        File[] groovyJars = libDir.listFiles((dir, name) ->
            (name.startsWith("groovy-") || name.equals("groovy.jar")) && name.endsWith(".jar"));
        File[] ivyJars = libDir.listFiles((dir, name) ->
            name.startsWith("ivy-") && name.endsWith(".jar"));
        boolean foundSiblings = groovyJars != null && groovyJars.length > 0;
        if (foundSiblings) {
          for (File jar : groovyJars) {
            loader.addURL(jar.toURI().toURL());
          }
          if (ivyJars != null) {
            for (File jar : ivyJars) {
              loader.addURL(jar.toURI().toURL());
            }
          }
          log.debug("Added {} Groovy jars and {} Ivy jars from {} (fallback)",
              groovyJars.length, ivyJars == null ? 0 : ivyJars.length, libDir);
          return;
        }
      }
    } catch (Exception e) {
      log.debug("Failed to scan lib dir for Groovy/Ivy jars, trying class-based resolution", e);
    }

    // Fallback for development mode: resolve key classes and extract their code source locations
    loader.addURL(groovyLocation);
    addCodeSourceByClassName(loader, "groovy.json.JsonSlurper");          // groovy-json
    addCodeSourceByClassName(loader, "groovy.xml.XmlSlurper");            // groovy-xml
    addCodeSourceByClassName(loader, "groovy.sql.Sql");                   // groovy-sql
    addCodeSourceByClassName(loader, "groovy.console.ui.Console");        // groovy-console
    addCodeSourceByClassName(loader, "groovy.text.markup.MarkupTemplateEngine"); // groovy-templates
    addCodeSourceByClassName(loader, "groovy.yaml.YamlSlurper");          // groovy-yaml
    addCodeSourceByClassName(loader, "groovy.ant.AntBuilder");            // groovy-ant
    addCodeSourceByClassName(loader, "groovy.swing.SwingBuilder");        // groovy-swing
    addCodeSourceByClassName(loader, "groovy.transform.ThreadInterrupt"); // groovy-groovydoc or groovy-all
    addCodeSourceByClassName(loader, "org.apache.ivy.Ivy");               // ivy
    log.debug("Added Groovy/Ivy jars via class-based resolution (development mode)");
  }

  private void addCodeSourceByClassName(GroovyClassLoader loader, String className) {
    try {
      Class<?> cls = Class.forName(className);
      URL location = cls.getProtectionDomain().getCodeSource().getLocation();
      if (location != null) {
        loader.addURL(location);
      }
    } catch (ClassNotFoundException | NullPointerException e) {
      log.debug("Class {} not found, skipping", className);
    }
  }

  private GroovyClassLoader createGradleClassLoader(RuntimeConfig runtime, CompilerConfiguration config,
                                                    boolean testContext, ConsoleTextArea console) {
    File projectDir = gui.getProjectDir();
    GroovyClassLoader loader = new GroovyClassLoader(ClassUtils.getBootstrapClassLoader(), config);
    if (projectDir == null) {
      addDefaultGroovyRuntime(loader);
      return loader;
    }
    File gradleInstallationDir = null;
    if (runtime.getBuildToolHome() != null && !runtime.getBuildToolHome().isBlank()) {
      gradleInstallationDir = new File(runtime.getBuildToolHome());
    }
    var gradleUtils = new GradleUtils(
        gradleInstallationDir,
        projectDir,
        runtime.getJavaHome()
    );
    gradleUtils.addGradleDependencies(loader, console, testContext);
    // Add default Groovy runtime AFTER project dependencies so project version takes precedence
    addDefaultGroovyRuntimeIfMissing(loader);
    return loader;
  }

  private GroovyClassLoader createMavenClassLoader(RuntimeConfig runtime, CompilerConfiguration config,
                                                   boolean testContext, ConsoleTextArea console) {
    GroovyClassLoader loader = new GroovyClassLoader(ClassUtils.getBootstrapClassLoader(), config);
    File projectDir = gui.getProjectDir();
    if (projectDir == null) {
      addDefaultGroovyRuntime(loader);
      return loader;
    }
    MavenClasspathUtils.addPomDependenciesTo(loader, projectDir, testContext, console, runtime.getBuildToolHome());
    if (gui.getPrefs().getBoolean(ADD_BUILDDIR_TO_CLASSPATH, true)) {
      addMavenOutputs(loader, projectDir, testContext);
    }
    // Add default Groovy runtime AFTER project dependencies so project version takes precedence
    addDefaultGroovyRuntimeIfMissing(loader);
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

  /**
   * Add default Groovy runtime only if GroovySystem is not already available in the classloader.
   * This ensures project-specific Groovy versions take precedence over Gade's bundled version.
   */
  private void addDefaultGroovyRuntimeIfMissing(GroovyClassLoader loader) {
    try {
      // Try to load GroovySystem from the current classloader
      loader.loadClass("groovy.lang.GroovySystem");
      // If successful, Groovy is already available, don't add Gade's version
      log.debug("Groovy runtime already available in classloader, skipping default Groovy");
    } catch (ClassNotFoundException e) {
      // Groovy not found, add Gade's bundled version as fallback
      log.debug("Groovy runtime not found in classloader, adding default Groovy as fallback");
      addDefaultGroovyRuntime(loader);
    }
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
}
