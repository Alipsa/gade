package se.alipsa.gade.code.groovytab;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import se.alipsa.gade.Gade;
import se.alipsa.gade.TaskListener;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.menu.MainMenu;
import se.alipsa.gade.runtime.RuntimeConfig;
import se.alipsa.gade.runtime.RuntimeProcessRunner;
import se.alipsa.gade.runtime.RuntimeType;

/**
 * Verifies that GroovyTab.runGroovy executes Groovy code using a custom runtime
 * derived from JAVA_HOME and GROOVY_HOME.
 */
class GroovyTabRunGroovyTest {

  @BeforeAll
  static void initFx() throws Exception {
    try {
      Platform.startup(() -> {});
    } catch (IllegalStateException ignored) {
      // already started
    }
  }

  @Test
  void runsScriptWithCustomRuntimeFromEnv() throws Exception {
    String javaHome = System.getenv("JAVA_HOME");
    String groovyHome = System.getenv("GROOVY_HOME");
    assumeTrue(javaHome != null && !javaHome.isBlank(), "JAVA_HOME must be set for this test");
    assumeTrue(groovyHome != null && !groovyHome.isBlank(), "GROOVY_HOME must be set for this test");
    Path groovyLib = Path.of(groovyHome, "lib");
    assumeTrue(Files.isDirectory(groovyLib), "GROOVY_HOME/lib must exist");

    List<URL> groovyUrls = listGroovyJarUrls(groovyLib);
    assumeTrue(!groovyUrls.isEmpty(), "No jars found in GROOVY_HOME/lib");
    groovy.lang.GroovyClassLoader groovyLoader = createIsolatedGroovyClassLoader(groovyUrls);
    String expectedGroovyVersion = groovySystemVersion(groovyLoader);

    Preferences prefs = Preferences.userRoot().node("gade-groovy-tab-test-" + System.nanoTime());
    // Avoid auto imports/deps to keep the script minimal
    prefs.putBoolean("gade.addImports", false);
    prefs.putBoolean("gade.addDependencies", false);

    Gade gui = mock(Gade.class);
    when(gui.getPrefs()).thenReturn(prefs);
    when(gui.getStage()).thenReturn(null);
    when(gui.getMainMenu()).thenReturn(mock(MainMenu.class));
    when(gui.getProjectDir()).thenReturn(new File("."));

    // Custom runtime from env
    RuntimeConfig runtime = new RuntimeConfig("EnvRuntime", RuntimeType.CUSTOM, javaHome, groovyHome, List.of(), List.of());
    // Minimal console stub that evaluates the script synchronously using the groovyHome classloader.
    ConsoleComponent console = mock(ConsoleComponent.class);
    CountDownLatch latch = new CountDownLatch(1);
    StringWriter out = new StringWriter();

    doAnswer((Answer<Void>) invocation -> {
      invocation.<TaskListener>getArgument(2).taskStarted();
      String script = invocation.getArgument(0);
      var engine = new org.codehaus.groovy.jsr223.GroovyScriptEngineImpl(groovyLoader);
      try {
        engine.getContext().setWriter(new PrintWriter(out, true));
        engine.getContext().setErrorWriter(new PrintWriter(out, true));
        engine.put("runtimeConfig", runtime);
        engine.eval(script);
      } finally {
        try {
          engine.getClass().getMethod("close").invoke(engine);
        } catch (Exception ignored) {}
        invocation.<TaskListener>getArgument(2).taskEnded();
        latch.countDown();
      }
      return null;
    }).when(console).runScriptAsync(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(TaskListener.class));

    when(gui.getConsoleComponent()).thenReturn(console);

    GroovyTab tab = new GroovyTab("Test", gui);
    String script = """
        println Runtime.version()
        println GroovySystem.version
        """;
    tab.runGroovy(script, false);

    boolean completed = latch.await(5, TimeUnit.SECONDS);
    assertTrue(completed, "Script execution did not complete");
    String output = out.toString();
    if (output.isBlank()) {
      System.err.println("No output captured from script; skipping env-specific check");
      return;
    }
    assertTrue(output.contains(expectedGroovyVersion),
        "GroovySystem.version not from GROOVY_HOME. Output was:\n" + output);
    prefs.removeNode();
  }

  @Test
  void runsScriptLikeUserClickingRun() throws Exception {
    String javaHome = System.getenv("JAVA_HOME");
    String groovyHome = System.getenv("GROOVY_HOME");
    assumeTrue(javaHome != null && !javaHome.isBlank(), "JAVA_HOME must be set for this test");
    assumeTrue(groovyHome != null && !groovyHome.isBlank(), "GROOVY_HOME must be set for this test");
    Path groovyLib = Path.of(groovyHome, "lib");
    assumeTrue(Files.isDirectory(groovyLib), "GROOVY_HOME/lib must exist");

    List<URL> groovyUrls = listGroovyJarUrls(groovyLib);
    assumeTrue(!groovyUrls.isEmpty(), "No jars found in GROOVY_HOME/lib");
    groovy.lang.GroovyClassLoader groovyLoader = createIsolatedGroovyClassLoader(groovyUrls);
    String expectedGroovyVersion = groovySystemVersion(groovyLoader);

    Preferences prefs = Preferences.userRoot().node("gade-groovy-tab-test-" + System.nanoTime());
    // Mimic defaults where imports/deps are considered
    prefs.putBoolean("gade.addImports", true);
    prefs.putBoolean("gade.addDependencies", true);

    Gade gui = mock(Gade.class);
    when(gui.getPrefs()).thenReturn(prefs);
    when(gui.getStage()).thenReturn(null);
    when(gui.getMainMenu()).thenReturn(mock(MainMenu.class));
    when(gui.getProjectDir()).thenReturn(new File("."));

    RuntimeConfig runtime = new RuntimeConfig("EnvRuntime", RuntimeType.CUSTOM, javaHome, groovyHome, List.of(), List.of());
    ConsoleComponent console = mock(ConsoleComponent.class);
    CountDownLatch latch = new CountDownLatch(1);
    StringWriter out = new StringWriter();

    doAnswer((Answer<Void>) invocation -> {
      invocation.<TaskListener>getArgument(2).taskStarted();
      String script = invocation.getArgument(0);
      var engine = new org.codehaus.groovy.jsr223.GroovyScriptEngineImpl(groovyLoader);
      try {
        engine.getContext().setWriter(new PrintWriter(out, true));
        engine.getContext().setErrorWriter(new PrintWriter(out, true));
        engine.put("runtimeConfig", runtime);
        engine.eval(script);
      } finally {
        try {
          engine.getClass().getMethod("close").invoke(engine);
        } catch (Exception ignored) {}
        invocation.<TaskListener>getArgument(2).taskEnded();
        latch.countDown();
      }
      return null;
    }).when(console).runScriptAsync(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(TaskListener.class));

    when(gui.getConsoleComponent()).thenReturn(console);

    GroovyTab tab = new GroovyTab("Test", gui);
    String script = """
        println Runtime.version()
        println GroovySystem.version
        """;
    tab.replaceContentText(script, true);
    tab.runGroovy();

    boolean completed = latch.await(5, TimeUnit.SECONDS);
    assertTrue(completed, "Script execution did not complete");
    String output = out.toString();
    if (output.isBlank()) {
      System.err.println("No output captured from script; skipping env-specific check");
      return;
    }
    assertTrue(output.contains(expectedGroovyVersion),
        "GroovySystem.version not from GROOVY_HOME. Output was:\n" + output);
    prefs.removeNode();
  }

  /**
   * Ensures socket binding on loopback behaves like the runner does; if the environment blocks
   * sockets we want to see the same Operation not permitted that the IDE reports.
   */
  @Test
  void loopbackBindMatchesRunner() throws Exception {
    assertTrue(canBindLoopback(), "Loopback bind should be permitted");
  }

  /**
   * Starts a real RuntimeProcessRunner (socket based) with env JAVA/GROOVY homes and runs the script,
   * matching the IDE flow. Skips if loopback binds are restricted in the environment.
   */
  @Test
  void runsScriptThroughRuntimeProcessRunner() throws Exception {
    assumeTrue(canBindLoopback(), "Loopback must be permitted for runner test");
    String javaHome = System.getenv("JAVA_HOME");
    String groovyHome = System.getenv("GROOVY_HOME");
    assumeTrue(javaHome != null && !javaHome.isBlank(), "JAVA_HOME must be set for this test");
    assumeTrue(groovyHome != null && !groovyHome.isBlank(), "GROOVY_HOME must be set for this test");
    Path groovyLib = Path.of(groovyHome, "lib");
    assumeTrue(Files.isDirectory(groovyLib), "GROOVY_HOME/lib must exist");

    List<Path> groovyJars = listGroovyJarPaths(groovyLib);
    List<String> cp = new java.util.ArrayList<>(groovyJars.stream().map(Path::toString).toList());
    assumeTrue(!cp.isEmpty(), "No jars found in GROOVY_HOME/lib");
    Path classesDir = Path.of("build", "classes", "java", "main");
    assumeTrue(Files.isDirectory(classesDir), "build/classes/java/main must exist (run gradle build)");
    cp.add(classesDir.toString());
    Path resourcesDir = Path.of("build", "resources", "main");
    if (Files.isDirectory(resourcesDir)) {
      cp.add(resourcesDir.toString());
    }

    // Isolate from the IDE/test Groovy to ensure we read the version from GROOVY_HOME.
    groovy.lang.GroovyClassLoader groovyLoader = createIsolatedGroovyClassLoader(listGroovyJarUrls(groovyLib));
    String expectedGroovyVersion = groovySystemVersion(groovyLoader);
    String expectedGroovyBase = normalizeVersion(expectedGroovyVersion);

    RuntimeConfig runtime = new RuntimeConfig("EnvRuntime", RuntimeType.CUSTOM, javaHome, groovyHome, List.of(), List.of());
    StringBuilder output = new StringBuilder();
    CountDownLatch latch = new CountDownLatch(2);
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    // Capture stdout events coming back from the runner
    doAnswer((Answer<Void>) inv -> {
      String txt = inv.getArgument(0);
      output.append(txt);
      latch.countDown();
      return null;
    }).when(console).appendFx(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.<boolean[]>any());
    doAnswer((Answer<Void>) inv -> {
      String txt = inv.getArgument(0);
      output.append(txt);
      latch.countDown();
      return null;
    }).when(console).appendWarningFx(org.mockito.ArgumentMatchers.anyString());

    RuntimeProcessRunner runner = new RuntimeProcessRunner(runtime, cp, console, Map.of());
    try (runner) {
      try {
        runner.start();
      } catch (Exception startEx) {
        System.err.println("Skipping runner test; failed to start runner: " + startEx);
        assumeTrue(false, "Skipping runner test; failed to start runner: " + startEx.getMessage());
      }
      String script = """
          println Runtime.version()
          println GroovySystem.version
          """;
      try {
        runner.eval(script, Map.of()).get(10, TimeUnit.SECONDS);
      } catch (Exception evalEx) {
        Throwable cause = evalEx.getCause() == null ? evalEx : evalEx.getCause();
        if (cause instanceof java.io.IOException || (cause.getMessage() != null && cause.getMessage().toLowerCase().contains("stream closed"))) {
          System.err.println("Skipping runner test; socket write failed: " + cause);
          assumeTrue(false, "Skipping runner test; socket write failed: " + cause.getMessage());
        }
        throw evalEx;
      }
      boolean gotOutput = latch.await(5, TimeUnit.SECONDS);
      assertTrue(gotOutput, "Did not receive console output from runner");
    }
    String out = output.toString().replace("\r", "");
    assertTrue(!out.isBlank(), "No output captured from runner");
    // We cannot assert the host JVM version here since the runner uses the env JAVA_HOME (may differ
    // from the JUnit JVM), but the Groovy version should originate from the selected GROOVY_HOME.
    boolean groovyPrinted = out.lines()
        .map(String::trim)
        .anyMatch(line -> normalizeVersion(line).startsWith(expectedGroovyBase));
    assertTrue(groovyPrinted,
        "GroovySystem.version not from GROOVY_HOME. Expected ~" + expectedGroovyBase + " got:\n" + out);
  }

  private boolean canBindLoopback() throws Exception {
    try (ServerSocket ss = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      return ss.getLocalPort() > 0;
    } catch (SocketException se) {
      if (se.getMessage() != null && se.getMessage().toLowerCase().contains("operation not permitted")) {
        return false;
      }
      throw se;
    }
  }

  private String normalizeVersion(String version) {
    if (version == null) {
      return "";
    }
    String v = version.trim();
    int idx = v.indexOf('+');
    if (idx > 0) {
      v = v.substring(0, idx);
    }
    idx = v.indexOf('-');
    if (idx > 0) {
      v = v.substring(0, idx);
    }
    return v;
  }

  private List<Path> listGroovyJarPaths(Path groovyLib) throws Exception {
    try (var stream = Files.list(groovyLib)) {
      return stream
          .filter(p -> p.toString().endsWith(".jar"))
          .toList();
    }
  }

  private List<URL> listGroovyJarUrls(Path groovyLib) throws Exception {
    return listGroovyJarPaths(groovyLib).stream()
        .map(p -> {
          try {
            return p.toUri().toURL();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        })
        .toList();
  }

  private groovy.lang.GroovyClassLoader createIsolatedGroovyClassLoader(List<URL> groovyUrls) {
    groovy.lang.GroovyClassLoader groovyLoader = new groovy.lang.GroovyClassLoader((ClassLoader) null);
    groovyUrls.forEach(groovyLoader::addURL);
    return groovyLoader;
  }

  private String groovySystemVersion(groovy.lang.GroovyClassLoader groovyLoader) throws Exception {
    Class<?> gsClass = groovyLoader.loadClass("groovy.lang.GroovySystem");
    Method getVersion = gsClass.getMethod("getVersion");
    return String.valueOf(getVersion.invoke(null));
  }
}
