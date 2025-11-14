package se.alipsa.gade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.script.ScriptEngine;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScriptClassLoaderManagerTest {

  private Path tempHome;

  @AfterEach
  void cleanup() throws IOException {
    if (tempHome != null) {
      Files.walk(tempHome)
          .sorted(Comparator.reverseOrder())
          .forEach(path -> {
            try {
              Files.deleteIfExists(path);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
      tempHome = null;
    }
  }

  @Test
  void scriptsCannotAccessGadeImplementationClasses() throws Exception {
    Path home = prepareGadeHome();
    ScriptClassLoaderManager manager = new ScriptClassLoaderManager(home.toFile());
    CompilerConfiguration configuration = new CompilerConfiguration();
    GroovyShell shell =
        new GroovyShell(manager.createScriptClassLoader(configuration), new Binding(), configuration);
    CompilationFailedException ex =
        assertThrows(
            CompilationFailedException.class,
            () -> shell.evaluate("import se.alipsa.gade.Gade\nGade"));
    assertTrue(ex.getMessage().contains("se.alipsa.gade.Gade"));
  }

  @Test
  void scriptsExecuteWithProvidedBindings() throws Exception {
    Path home = prepareGadeHome();
    ScriptClassLoaderManager manager = new ScriptClassLoaderManager(home.toFile());
    CompilerConfiguration configuration = new CompilerConfiguration();
    Binding binding = new Binding();
    binding.setVariable("value", "report");
    GroovyShell shell =
        new GroovyShell(manager.createScriptClassLoader(configuration), binding, configuration);
    Object result = shell.evaluate("value.toUpperCase()");
    assertTrue(result instanceof String);
    assertTrue(((String) result).contains("REPORT"));
  }

  @Test
  void scriptClassLoaderPrefersChildDependencies() throws Exception {
    Path home = prepareGadeHome();
    ScriptClassLoaderManager manager = new ScriptClassLoaderManager(home.toFile());

    Path parentJar = createVersionedJar("parent", "PARENT_VERSION");
    Path childJar = createVersionedJar("child", "CHILD_VERSION");

    try (URLClassLoader parent =
        new URLClassLoader(new java.net.URL[] {parentJar.toUri().toURL()},
            manager.getSharedDynamicLoader())) {
      IsolatedGroovyClassLoader child =
          new IsolatedGroovyClassLoader(parent, java.util.List.of());
      child.addURL(childJar.toUri().toURL());

      Class<?> clazz = child.loadClass("example.override.Versioned");
      java.lang.reflect.Field field = clazz.getDeclaredField("VERSION");
      assertEquals("CHILD_VERSION", field.get(null));
    }
  }

  private Path prepareGadeHome() throws IOException {
    tempHome = Files.createTempDirectory("gade-home");
    return tempHome;
  }

  @Test
  void scriptsDoNotSeeBundledLibsByDefault() throws Exception {
    Path home = prepareGadeHome();
    Files.createDirectories(home.resolve("lib"));
    Path bundledJar = createVersionedJar("bundled", "BUNDLED_VERSION");
    Files.copy(bundledJar, home.resolve("lib").resolve(bundledJar.getFileName()), StandardCopyOption.REPLACE_EXISTING);

    ScriptClassLoaderManager manager = new ScriptClassLoaderManager(home.toFile());
    CompilerConfiguration configuration = new CompilerConfiguration();
    GroovyClassLoader loader = manager.createScriptClassLoader(configuration);

    assertThrows(ClassNotFoundException.class, () -> loader.loadClass("example.override.Versioned"));

    manager.addDependencyFile(bundledJar.toFile());
    GroovyClassLoader refreshedLoader = manager.createScriptClassLoader(configuration);
    Class<?> clazz = refreshedLoader.loadClass("example.override.Versioned");
    assertEquals("BUNDLED_VERSION", clazz.getDeclaredField("VERSION").get(null));
  }

  @Test
  void groovyScriptEngineIsAvailableToScripts() throws Exception {
    Path home = prepareGadeHome();
    ScriptClassLoaderManager manager = new ScriptClassLoaderManager(home.toFile());
    CompilerConfiguration configuration = new CompilerConfiguration();
    GroovyClassLoader loader = manager.createScriptClassLoader(configuration);

    Class<?> engineClass = loader.loadClass("org.codehaus.groovy.jsr223.GroovyScriptEngineImpl");
    Object engineInstance = engineClass.getDeclaredConstructor().newInstance();
    assertTrue(ScriptEngine.class.isInstance(engineInstance));

    ScriptEngine scriptEngine = (ScriptEngine) engineInstance;
    Object result = scriptEngine.eval("1 + 2");
    assertTrue(result instanceof Number);
    assertEquals(3, ((Number) result).intValue());
  }

  @Test
  void ivyRuntimeIsAvailableWhenBundled() throws Exception {
    Path home = prepareGadeHome();
    Path libDir = Files.createDirectories(home.resolve("lib"));

    Path ivyJar =
        createJarWithSource(
            "ivy-mock",
            "org.apache.ivy.core.module.descriptor.ModuleDescriptor",
            "package org.apache.ivy.core.module.descriptor;\n"
                + "public class ModuleDescriptor {}\n",
            tempHome.resolve("ivy"));
    Files.copy(ivyJar, libDir.resolve(ivyJar.getFileName()), StandardCopyOption.REPLACE_EXISTING);

    ScriptClassLoaderManager manager = new ScriptClassLoaderManager(home.toFile());
    GroovyClassLoader loader = manager.createScriptClassLoader(new CompilerConfiguration());

    Class<?> moduleDescriptor = loader.loadClass("org.apache.ivy.core.module.descriptor.ModuleDescriptor");
    assertEquals("org.apache.ivy.core.module.descriptor.ModuleDescriptor", moduleDescriptor.getName());
  }

  private Path createVersionedJar(String classifier, String versionLiteral) throws Exception {
    String className = "example.override.Versioned";
    String source =
        "package example.override;\n"
            + "public class Versioned {\n"
            + "  public static final String VERSION = \""
            + versionLiteral
            + "\";\n"
            + "}\n";
    return createJarWithSource(
        "versioned-" + classifier, className, source, tempHome.resolve("jar-" + classifier));
  }

  private Path createJarWithSource(String jarName, String className, String source, Path workDir)
      throws Exception {
    Path buildDir = Files.createDirectories(workDir);
    Path sourceDir = Files.createDirectories(buildDir.resolve("src"));
    Path javaFile = sourceDir.resolve(className.replace('.', '/') + ".java");
    Files.createDirectories(javaFile.getParent());
    Files.writeString(javaFile, source);

    Path classesDir = Files.createDirectories(buildDir.resolve("classes"));
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No system compiler available for tests");
    }
    int compilationResult =
        compiler.run(null, null, null, "-d", classesDir.toString(), javaFile.toString());
    if (compilationResult != 0) {
      throw new IllegalStateException("Failed to compile test jar source");
    }

    Path jarFile = buildDir.resolve(jarName + ".jar");
    try (OutputStream outputStream = Files.newOutputStream(jarFile);
        JarOutputStream jar = new JarOutputStream(outputStream)) {
      String classEntry = className.replace('.', '/') + ".class";
      Path classFile = classesDir.resolve(classEntry);
      JarEntry entry = new JarEntry(classEntry);
      jar.putNextEntry(entry);
      jar.write(Files.readAllBytes(classFile));
      jar.closeEntry();
    }
    return jarFile;
  }
}
