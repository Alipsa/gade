package se.alipsa.gade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import groovy.lang.GroovyShell;
import javafx.embed.swing.JFXPanel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.runner.GadeRunnerEngine;
import se.alipsa.gi.GuiInteraction;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

class RuntimeProcessRunnerRuntimeMatrixTest {

  private static final String TABLESAW_GRAB_SCRIPT = """
      @GrabResolver(name='central', root='https://repo1.maven.org/maven2/')
      @Grab('tech.tablesaw:tablesaw-core:0.44.1')
      import tech.tablesaw.api.Table
      Table.class.getName()
      """;
  private static final String FORCED_GRAB_FAILURE_SCRIPT = """
      @GrabResolver(name='badlocal', root='http://127.0.0.1:1/')
      @Grab('com.acme:missing-lib:1.0.0')
      import com.acme.MissingType
      MissingType.class.getName()
      """;
  private static final String LOGGING_SCRIPT = """
      import org.apache.logging.log4j.core.config.Configurator
      import org.apache.logging.log4j.Level
      import org.slf4j.LoggerFactory
      Configurator.setRootLevel(Level.INFO)
      def log = LoggerFactory.getLogger(this.class)
      log.info("runner logging probe")
      "logged"
      """;

  @BeforeAll
  static void initJavaFx() {
    try {
      new JFXPanel();
    } catch (Exception ignored) {
      // JavaFX already initialized
    }
  }

  @Test
  void gadeRuntimeSupportsSimpleScriptGrabAndIo(@TempDir Path tempDir) throws Exception {
    assertRuntimeScenario(RuntimeType.GADE, tempDir);
  }

  @Test
  void gradleRuntimeSupportsSimpleScriptGrabAndIo(@TempDir Path tempDir) throws Exception {
    assertRuntimeScenario(RuntimeType.GRADLE, tempDir);
  }

  @Test
  void mavenRuntimeSupportsSimpleScriptGrabAndIo(@TempDir Path tempDir) throws Exception {
    assertRuntimeScenario(RuntimeType.MAVEN, tempDir);
  }

  @Test
  void customRuntimeSupportsSimpleScriptGrabAndIo(@TempDir Path tempDir) throws Exception {
    assertRuntimeScenario(RuntimeType.CUSTOM, tempDir);
  }

  @Test
  void gradleRuntimeLoggingDoesNotEmitDefaultConsoleAppenderErrors(@TempDir Path tempDir) throws Exception {
    assumeTrue(canBindLoopback(), "Loopback must be permitted for runtime matrix tests");

    Path bootJar = findRunnerJar("gade-runner-boot");
    Path engineJar = findRunnerJar("gade-runner-engine");
    List<String> groovyAndSupportEntries = collectGroovyAndSupportEntries();
    assertFalse(groovyAndSupportEntries.isEmpty(), "No Groovy/Ivy/support jars available for runtime bootstrap");

    List<String> loggingEntries = collectLoggingEntries();
    assertFalse(loggingEntries.isEmpty(), "Expected Log4j/SLF4J dependencies to be available for logging regression test");

    LinkedHashSet<String> classPathEntries = new LinkedHashSet<>();
    classPathEntries.add(bootJar.toAbsolutePath().normalize().toString());

    LinkedHashSet<String> groovyEntries = new LinkedHashSet<>(groovyAndSupportEntries);
    groovyEntries.add(engineJar.toAbsolutePath().normalize().toString());

    RuntimeConfig runtime = new RuntimeConfig("GradleRuntime", RuntimeType.GRADLE);
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    try (RuntimeProcessRunner runner = new RuntimeProcessRunner(
        runtime,
        new ArrayList<>(classPathEntries),
        new ArrayList<>(groovyEntries),
        loggingEntries,
        List.of(),
        console,
        Map.of(),
        tempDir.toFile()
    )) {
      runner.start();
      String result = runner.eval(LOGGING_SCRIPT, Map.of()).get(30, TimeUnit.SECONDS);
      assertEquals("logged", result, "Gradle runtime should execute script logging without appender failures");
    }

    verify(console, atLeastOnce()).appendFx(anyString(), any(boolean[].class));
    verify(console, org.mockito.Mockito.never()).appendFx(
        argThat(line -> line != null && line.contains("DefaultConsole")),
        any(boolean[].class));
  }

  @Test
  void gradleRuntimeSupportsProjectDependenciesWithoutGrab(@TempDir Path tempDir) throws Exception {
    assumeTrue(canBindLoopback(), "Loopback must be permitted for runtime matrix tests");

    Path bootJar = findRunnerJar("gade-runner-boot");
    Path engineJar = findRunnerJar("gade-runner-engine");
    List<String> groovyAndSupportEntries = collectGroovyAndSupportEntries();
    assertFalse(groovyAndSupportEntries.isEmpty(), "No Groovy/Ivy/support jars available for runtime bootstrap");

    GrabArtifact artifact = createLocalGrabArtifact(tempDir.resolve("gradle-no-grab-repo"), RuntimeType.GRADLE);
    String script = """
        import %s
        %s.message()
        """.formatted(artifact.fullyQualifiedClassName(), artifact.simpleClassName());

    LinkedHashSet<String> classPathEntries = new LinkedHashSet<>();
    classPathEntries.add(bootJar.toAbsolutePath().normalize().toString());
    LinkedHashSet<String> groovyEntries = new LinkedHashSet<>(groovyAndSupportEntries);
    groovyEntries.add(engineJar.toAbsolutePath().normalize().toString());

    RuntimeConfig runtime = new RuntimeConfig("GradleRuntime", RuntimeType.GRADLE);
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    try (RuntimeProcessRunner runner = new RuntimeProcessRunner(
        runtime,
        new ArrayList<>(classPathEntries),
        new ArrayList<>(groovyEntries),
        List.of(artifact.jarPath()),
        List.of(),
        console,
        Map.of(),
        tempDir.toFile()
    )) {
      runner.start();
      String result = runner.eval(script, Map.of()).get(30, TimeUnit.SECONDS);
      assertEquals(artifact.expectedResult(), result,
          "Gradle runtime should resolve project dependencies from classpath entries without script @Grab");
    }
  }

  @Test
  void gadeRuntimeInheritsNetworkSystemPropertiesForGrab(@TempDir Path tempDir) throws Exception {
    assumeTrue(canBindLoopback(), "Loopback must be permitted for runtime matrix tests");

    Path bootJar = findRunnerJar("gade-runner-boot");
    Path engineJar = findRunnerJar("gade-runner-engine");
    List<String> groovyAndSupportEntries = collectGroovyAndSupportEntries();
    assertFalse(groovyAndSupportEntries.isEmpty(), "No Groovy/Ivy/support jars available for runtime bootstrap");

    LinkedHashSet<String> classPathEntries = new LinkedHashSet<>();
    classPathEntries.add(bootJar.toAbsolutePath().normalize().toString());

    LinkedHashSet<String> groovyEntries = new LinkedHashSet<>(groovyAndSupportEntries);
    groovyEntries.add(engineJar.toAbsolutePath().normalize().toString());

    RuntimeConfig runtime = new RuntimeConfig("GADERuntime", RuntimeType.GADE);
    ConsoleTextArea console = mock(ConsoleTextArea.class);

    String oldHost = System.getProperty("https.proxyHost");
    String oldPort = System.getProperty("https.proxyPort");
    System.setProperty("https.proxyHost", "proxy.test.local");
    System.setProperty("https.proxyPort", "8443");
    try (RuntimeProcessRunner runner = new RuntimeProcessRunner(
        runtime,
        new ArrayList<>(classPathEntries),
        new ArrayList<>(groovyEntries),
        List.of(),
        List.of(),
        console,
        Map.of(),
        tempDir.toFile()
    )) {
      runner.start();
      String result = runner.eval(
          "System.getProperty('https.proxyHost') + ':' + System.getProperty('https.proxyPort')",
          Map.of()
      ).get(20, TimeUnit.SECONDS);
      assertEquals("proxy.test.local:8443", result,
          "GADE subprocess must inherit network proxy system properties for @Grab downloads");
    } finally {
      restoreSystemProperty("https.proxyHost", oldHost);
      restoreSystemProperty("https.proxyPort", oldPort);
    }
  }

  @Test
  void gadeRuntimeCanGrabTablesawFromMavenCentralWhenHostCan(@TempDir Path tempDir) throws Exception {
    assumeTrue(canBindLoopback(), "Loopback must be permitted for runtime matrix tests");
    purgeDefaultGrapeArtifact("tech.tablesaw", "tablesaw-core");

    Path hostGrapeRoot = tempDir.resolve("host-grapes");
    assumeTrue(canHostGrabTablesaw(hostGrapeRoot),
        "Host JVM could not resolve tablesaw via @Grab; skipping external network integration check");

    Path bootJar = findRunnerJar("gade-runner-boot");
    Path engineJar = findRunnerJar("gade-runner-engine");
    List<String> groovyAndSupportEntries = collectGroovyAndSupportEntries();
    assertFalse(groovyAndSupportEntries.isEmpty(), "No Groovy/Ivy/support jars available for runtime bootstrap");

    LinkedHashSet<String> classPathEntries = new LinkedHashSet<>();
    classPathEntries.add(bootJar.toAbsolutePath().normalize().toString());

    LinkedHashSet<String> groovyEntries = new LinkedHashSet<>(groovyAndSupportEntries);
    groovyEntries.add(engineJar.toAbsolutePath().normalize().toString());

    RuntimeConfig runtime = new RuntimeConfig("GADERuntime", RuntimeType.GADE);
    ConsoleTextArea console = mock(ConsoleTextArea.class);

    String oldGrapeRoot = System.getProperty("grape.root");
    System.setProperty("grape.root", tempDir.resolve("runner-grapes").toAbsolutePath().toString());
    try (RuntimeProcessRunner runner = new RuntimeProcessRunner(
        runtime,
        new ArrayList<>(classPathEntries),
        new ArrayList<>(groovyEntries),
        List.of(),
        List.of(),
        console,
        Map.of(),
        tempDir.toFile()
    )) {
      runner.start();
      String result = runner.eval(TABLESAW_GRAB_SCRIPT, Map.of()).get(90, TimeUnit.SECONDS);
      assertEquals("tech.tablesaw.api.Table", result,
          "GADE runtime should resolve tablesaw via @Grab when host JVM can do so");
    } finally {
      restoreSystemProperty("grape.root", oldGrapeRoot);
    }
  }

  @Test
  void gadeRuntimeGrabFailureEmitsDiagnostics(@TempDir Path tempDir) throws Exception {
    assumeTrue(canBindLoopback(), "Loopback must be permitted for runtime matrix tests");

    Path bootJar = findRunnerJar("gade-runner-boot");
    Path engineJar = findRunnerJar("gade-runner-engine");
    List<String> groovyAndSupportEntries = collectGroovyAndSupportEntries();
    assertFalse(groovyAndSupportEntries.isEmpty(), "No Groovy/Ivy/support jars available for runtime bootstrap");

    LinkedHashSet<String> classPathEntries = new LinkedHashSet<>();
    classPathEntries.add(bootJar.toAbsolutePath().normalize().toString());

    LinkedHashSet<String> groovyEntries = new LinkedHashSet<>(groovyAndSupportEntries);
    groovyEntries.add(engineJar.toAbsolutePath().normalize().toString());

    RuntimeConfig runtime = new RuntimeConfig("GADERuntime", RuntimeType.GADE);
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    List<String> warningLines = new ArrayList<>();
    doAnswer(invocation -> {
      String line = invocation.getArgument(0, String.class);
      warningLines.add(line == null ? "" : line);
      return null;
    }).when(console).appendWarningFx(anyString());
    String oldDiagnostics = System.getProperty("gade.runner.diagnostics");
    System.setProperty("gade.runner.diagnostics", "true");
    try {
      try (RuntimeProcessRunner runner = new RuntimeProcessRunner(
          runtime,
          new ArrayList<>(classPathEntries),
          new ArrayList<>(groovyEntries),
          List.of(),
          List.of(),
          console,
          Map.of(),
          tempDir.toFile()
      )) {
        runner.start();
        Exception error = null;
        try {
          runner.eval(FORCED_GRAB_FAILURE_SCRIPT, Map.of()).get(45, TimeUnit.SECONDS);
        } catch (Exception e) {
          error = e;
        }
        assertTrue(error != null, "Forced @Grab failure script should fail");
      }
    } finally {
      restoreSystemProperty("gade.runner.diagnostics", oldDiagnostics);
    }

    assertTrue(warningLines.stream().anyMatch(line -> line.contains("[GRAB_DIAG] Detected @Grab resolution failure")),
        "Expected GRAB diagnostics banner in console warnings");
    assertTrue(warningLines.stream().anyMatch(line -> line.contains("[GRAB_DIAG] cacheHints=")),
        "Expected GRAB diagnostics cache hints in console warnings");
    assertTrue(warningLines.stream().anyMatch(line -> line.contains("Error grabbing Grapes")),
        "Expected runner error details for failing @Grab script");
  }

  private void assertRuntimeScenario(RuntimeType runtimeType, Path tempDir) throws Exception {
    assumeTrue(canBindLoopback(), "Loopback must be permitted for runtime matrix tests");

    Path bootJar = findRunnerJar("gade-runner-boot");
    Path engineJar = findRunnerJar("gade-runner-engine");
    List<String> groovyAndSupportEntries = collectGroovyAndSupportEntries();
    assertFalse(groovyAndSupportEntries.isEmpty(), "No Groovy/Ivy/support jars available for runtime bootstrap");

    GrabArtifact grabArtifact = createLocalGrabArtifact(tempDir.resolve("grab-repo-" + runtimeType.name().toLowerCase(Locale.ROOT)), runtimeType);

    LinkedHashSet<String> classPathEntries = new LinkedHashSet<>();
    LinkedHashSet<String> groovyEntries = new LinkedHashSet<>();

    if (RuntimeType.CUSTOM.equals(runtimeType)) {
      classPathEntries.add(bootJar.toAbsolutePath().normalize().toString());
      classPathEntries.add(engineJar.toAbsolutePath().normalize().toString());
      classPathEntries.addAll(groovyAndSupportEntries);
    } else {
      classPathEntries.add(bootJar.toAbsolutePath().normalize().toString());
      groovyEntries.addAll(groovyAndSupportEntries);
      groovyEntries.add(engineJar.toAbsolutePath().normalize().toString());
    }

    RuntimeConfig runtime = RuntimeType.CUSTOM.equals(runtimeType)
        ? new RuntimeConfig("CustomRuntime", runtimeType, System.getProperty("java.home"), null, List.of(), List.of())
        : new RuntimeConfig(runtimeType.name() + "Runtime", runtimeType);

    ConsoleTextArea console = mock(ConsoleTextArea.class);
    GuiInteraction io = mock(GuiInteraction.class);
    String expectedIoResult = "io-ok-" + runtimeType.name().toLowerCase(Locale.ROOT);
    when(io.prompt(anyString())).thenReturn(expectedIoResult);

    RuntimeProcessRunner runner = new RuntimeProcessRunner(
        runtime,
        new ArrayList<>(classPathEntries),
        new ArrayList<>(groovyEntries),
        List.of(),
        List.of(),
        console,
        Map.of("io", io),
        tempDir.toFile()
    );

    try (runner) {
      runner.start();

      String simpleResult = runner.eval("1 + 1", Map.of()).get(20, TimeUnit.SECONDS);
      assertEquals("2", simpleResult, "Simple script must evaluate correctly for " + runtimeType);

      String grabResult = runner.eval(buildGrabScript(grabArtifact), Map.of()).get(30, TimeUnit.SECONDS);
      assertEquals(grabArtifact.expectedResult(), grabResult, "@Grab must resolve from local repository for " + runtimeType);

      Map<String, Object> ioBindings = Map.of(GadeRunnerEngine.GUI_INTERACTION_KEYS, List.of("io"));
      String ioResult = runner.eval("io.prompt('ping')", ioBindings).get(20, TimeUnit.SECONDS);
      assertEquals(expectedIoResult, ioResult, "io method call must round-trip through GUI protocol for " + runtimeType);
    }

    verify(io, atLeastOnce()).prompt("ping");
  }

  private String buildGrabScript(GrabArtifact artifact) {
    return """
        @GrabResolver(name='localtest', root='%s')
        @Grab('%s')
        import %s
        %s.message()
        """.formatted(
        artifact.resolverUri(),
        artifact.coordinates(),
        artifact.fullyQualifiedClassName(),
        artifact.simpleClassName()
    );
  }

  private GrabArtifact createLocalGrabArtifact(Path repoRoot, RuntimeType runtimeType) throws Exception {
    String suffix = runtimeType.name().toLowerCase(Locale.ROOT);
    String groupId = "local.gade";
    String artifactId = "runtime-grab-" + suffix;
    String version = "1.0.0";
    String packageName = "local.gade." + suffix;
    String simpleClassName = "GrabProbe";
    String fullyQualifiedClassName = packageName + "." + simpleClassName;
    String expectedResult = "grab-ok-" + suffix;

    Path sourceDir = repoRoot.resolve("src");
    Path classesDir = repoRoot.resolve("classes");
    Path javaFile = sourceDir.resolve(packageName.replace('.', File.separatorChar)).resolve(simpleClassName + ".java");

    Files.createDirectories(javaFile.getParent());
    Files.writeString(javaFile, """
        package %s;
        public class %s {
          public static String message() {
            return "%s";
          }
        }
        """.formatted(packageName, simpleClassName, expectedResult));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assumeTrue(compiler != null, "JDK compiler is required for local @Grab artifact test setup");
    Files.createDirectories(classesDir);
    int compileExit = compiler.run(null, null, null, "-d", classesDir.toString(), javaFile.toString());
    assertEquals(0, compileExit, "Failed to compile local @Grab test artifact");

    Path artifactDir = repoRoot.resolve(groupId.replace('.', File.separatorChar))
        .resolve(artifactId)
        .resolve(version);
    Files.createDirectories(artifactDir);

    Path jarPath = artifactDir.resolve(artifactId + "-" + version + ".jar");
    createJarFromCompiledClasses(classesDir, jarPath);

    Path pomPath = artifactDir.resolve(artifactId + "-" + version + ".pom");
    Files.writeString(pomPath, """
        <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <groupId>%s</groupId>
          <artifactId>%s</artifactId>
          <version>%s</version>
          <packaging>jar</packaging>
        </project>
        """.formatted(groupId, artifactId, version));

    String resolverUri = repoRoot.toUri().toString();
    if (!resolverUri.endsWith("/")) {
      resolverUri = resolverUri + "/";
    }
    return new GrabArtifact(
        groupId + ":" + artifactId + ":" + version,
        resolverUri,
        fullyQualifiedClassName,
        simpleClassName,
        expectedResult,
        jarPath.toAbsolutePath().normalize().toString()
    );
  }

  private void createJarFromCompiledClasses(Path classesDir, Path jarPath) throws IOException {
    List<Path> classFiles;
    try (var stream = Files.walk(classesDir)) {
      classFiles = stream.filter(Files::isRegularFile).toList();
    }
    try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jarPath))) {
      for (Path classFile : classFiles) {
        String entryName = classesDir.relativize(classFile).toString().replace(File.separatorChar, '/');
        jarOut.putNextEntry(new JarEntry(entryName));
        Files.copy(classFile, jarOut);
        jarOut.closeEntry();
      }
    }
  }

  private Path findRunnerJar(String prefix) throws IOException {
    Path buildLibs = Path.of("build", "libs");
    assertTrue(Files.isDirectory(buildLibs), "Missing build/libs directory: " + buildLibs.toAbsolutePath());
    try (var stream = Files.list(buildLibs)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> {
            String name = path.getFileName().toString();
            return name.startsWith(prefix) && name.endsWith(".jar");
          })
          .findFirst()
          .map(path -> path.toAbsolutePath().normalize())
          .orElseThrow(() -> new AssertionError("Could not find " + prefix + "*.jar in " + buildLibs.toAbsolutePath()));
    }
  }

  private List<String> collectGroovyAndSupportEntries() {
    LinkedHashSet<String> entries = new LinkedHashSet<>();
    String[] classNames = {
        "groovy.lang.GroovySystem",
        "groovy.json.JsonSlurper",
        "groovy.xml.XmlSlurper",
        "groovy.sql.Sql",
        "groovy.yaml.YamlSlurper",
        "groovy.console.ui.Console",
        "groovy.text.markup.MarkupTemplateEngine",
        "groovy.ant.AntBuilder",
        "groovy.swing.SwingBuilder",
        "groovy.transform.ThreadInterrupt",
        "org.apache.ivy.Ivy",
        "se.alipsa.groovy.datautil.ConnectionInfo"
    };
    for (String className : classNames) {
      addCodeSource(entries, className);
    }
    return new ArrayList<>(entries);
  }

  private List<String> collectLoggingEntries() {
    LinkedHashSet<String> entries = new LinkedHashSet<>();
    String[] classNames = {
        "org.slf4j.LoggerFactory",
        "org.apache.logging.log4j.Level",
        "org.apache.logging.log4j.core.config.Configurator",
        "org.apache.logging.slf4j.Log4jLoggerFactory"
    };
    for (String className : classNames) {
      addCodeSource(entries, className);
    }
    return new ArrayList<>(entries);
  }

  private void addCodeSource(LinkedHashSet<String> entries, String className) {
    try {
      Class<?> cls = Class.forName(className);
      URL location = cls.getProtectionDomain().getCodeSource().getLocation();
      if (location != null) {
        entries.add(Paths.get(location.toURI()).toFile().getAbsolutePath());
      }
    } catch (Exception ignored) {
      // Some optional Groovy modules may be absent in some environments.
    }
  }

  private boolean canBindLoopback() throws Exception {
    try (ServerSocket ss = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      return ss.getLocalPort() > 0;
    } catch (SocketException se) {
      if (se.getMessage() != null && se.getMessage().toLowerCase(Locale.ROOT).contains("operation not permitted")) {
        return false;
      }
      throw se;
    }
  }

  private boolean canHostGrabTablesaw(Path grapeRoot) {
    String oldGrapeRoot = System.getProperty("grape.root");
    try {
      Files.createDirectories(grapeRoot);
      System.setProperty("grape.root", grapeRoot.toAbsolutePath().toString());
      Object result = new GroovyShell().evaluate(TABLESAW_GRAB_SCRIPT);
      return "tech.tablesaw.api.Table".equals(String.valueOf(result));
    } catch (Throwable ignored) {
      return false;
    } finally {
      restoreSystemProperty("grape.root", oldGrapeRoot);
    }
  }

  private void purgeDefaultGrapeArtifact(String group, String artifact) throws IOException {
    String userHome = System.getProperty("user.home");
    if (userHome == null || userHome.isBlank()) {
      return;
    }
    Path artifactDir = Path.of(userHome, ".groovy", "grapes", group, artifact);
    if (!Files.exists(artifactDir)) {
      return;
    }
    try (var stream = Files.walk(artifactDir)) {
      stream.sorted(Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (IOException ignored) {
          // Best-effort cleanup for test isolation
        }
      });
    }
  }

  private void restoreSystemProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }

  private record GrabArtifact(
      String coordinates,
      String resolverUri,
      String fullyQualifiedClassName,
      String simpleClassName,
      String expectedResult,
      String jarPath
  ) {}
}
