package se.alipsa.gade.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import groovy.lang.GroovyClassLoader;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.alipsa.gade.Gade;
import se.alipsa.gade.runtime.RuntimeConfig;
import se.alipsa.gade.runtime.RuntimeType;

class GroovyRuntimeManagerTest {

  @TempDir
  Path tempDir;

  @Test
  void isTestSourceDetectsDefaultTestPaths() throws Exception {
    Path script = tempDir.resolve("src/test/groovy/SampleTest.groovy");
    Files.createDirectories(script.getParent());
    Files.createFile(script);

    boolean result = GroovyRuntimeManager.isTestSource(tempDir.toFile(), script.toFile());

    assertTrue(result);
  }

  @Test
  void isTestSourceUsesConfiguredTestDirectories() throws Exception {
    Path script = tempDir.resolve("src/integration/groovy/SmokeTest.groovy");
    Files.createDirectories(script.getParent());
    Files.createFile(script);
    Set<Path> configured = Set.of(tempDir.resolve("src/integration/groovy"));

    boolean result = GroovyRuntimeManager.isTestSource(
        tempDir.toFile(),
        script.toFile(),
        configured
    );

    assertTrue(result);
  }

  @Test
  void isTestSourceRejectsFilesOutsideProject() throws Exception {
    Path external = Files.createTempFile("outside", ".groovy");
    boolean result = GroovyRuntimeManager.isTestSource(tempDir.toFile(), external.toFile(), Set.of());
    Files.deleteIfExists(external);
    assertFalse(result);
  }

  @Test
  void customRuntimeClasspathOnlyUsesGroovyFromConfiguredGroovyHome() throws Exception {
    Path groovyHome = tempDir.resolve("groovy-home");
    Path groovyLib = groovyHome.resolve("lib");
    Files.createDirectories(groovyLib);
    Path groovyJar = Files.createFile(groovyLib.resolve("groovy-5.0.3.jar"));
    Path groovySwingJar = Files.createFile(groovyLib.resolve("groovy-swing-5.0.3.jar"));
    Path ivyJar = Files.createFile(groovyLib.resolve("ivy-2.5.3.jar"));
    Path extraJar = Files.createFile(tempDir.resolve("custom-extra.jar"));

    GroovyClassLoader customLoader = new GroovyClassLoader();
    customLoader.addURL(groovyJar.toUri().toURL());
    customLoader.addURL(groovySwingJar.toUri().toURL());
    customLoader.addURL(ivyJar.toUri().toURL());
    customLoader.addURL(extraJar.toUri().toURL());

    GroovyRuntimeManager manager = new GroovyRuntimeManager(mock(Gade.class));
    Field classLoaderField = GroovyRuntimeManager.class.getDeclaredField("classLoader");
    classLoaderField.setAccessible(true);
    classLoaderField.set(manager, customLoader);

    RuntimeConfig runtime = new RuntimeConfig(
        "Custom",
        RuntimeType.CUSTOM,
        null,
        groovyHome.toString(),
        List.of(),
        List.of()
    );

    List<String> entries = manager.buildClassPathEntries(runtime);
    assertTrue(entries.contains(groovyJar.toAbsolutePath().toString()));
    assertTrue(entries.contains(groovySwingJar.toAbsolutePath().toString()));
    assertTrue(entries.contains(ivyJar.toAbsolutePath().toString()));
    assertTrue(entries.contains(extraJar.toAbsolutePath().toString()));

    Path expectedGroovyDir = groovyLib.toAbsolutePath().normalize();
    List<String> foreignGroovyEntries = entries.stream()
        .filter(path -> isGroovyOrIvyJarName(new File(path).getName()))
        .filter(path -> !Path.of(path).toAbsolutePath().normalize().startsWith(expectedGroovyDir))
        .toList();

    assertEquals(List.of(), foreignGroovyEntries,
        "Custom runtime classpath must not include host Groovy/Ivy jars when GROOVY_HOME is configured");
  }

  private static boolean isGroovyOrIvyJarName(String fileName) {
    String name = fileName.toLowerCase(Locale.ROOT);
    return name.startsWith("groovy-")
        || name.startsWith("groovy.")
        || name.startsWith("ivy-")
        || name.startsWith("ivy.");
  }
}
