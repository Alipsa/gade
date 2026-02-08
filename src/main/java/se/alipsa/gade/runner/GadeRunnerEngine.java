package se.alipsa.gade.runner;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import se.alipsa.gade.runtime.ProtocolXml;

import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Groovy-dependent eval engine for the runner subprocess.
 * <p>
 * For Gradle/Maven runtimes, this class is loaded from a {@link GroovyClassLoader}
 * that has project dependencies (via {@code addURL()}) and Groovy jars in its
 * parent bootstrap classloader. For GADE/Custom runtimes, it is loaded from the
 * system classloader which already has Groovy on {@code -cp}.
 * <p>
 * Entry point: {@link #run(BufferedReader, BufferedWriter, String, String[], String[])} — creates a
 * main/test {@link GroovyClassLoader} hierarchy, {@link GroovyShell} instances,
 * and enters the main read loop
 * handling eval, bindings, interrupt, setWorkingDir, shutdown, gui_response, gui_error.
 */
public class GadeRunnerEngine {

  private static final OutputStream ROOT_ERR = new FileOutputStream(FileDescriptor.err);
  private static final boolean VERBOSE = Boolean.getBoolean("gade.runner.verbose");
  private static final String RUNNER_DIAG_PREFIX = "[RUNNER_DIAG] ";
  public static final String GUI_INTERACTION_KEYS = "__gadeGuiInteractionKeys";
  private static final AtomicReference<Thread> currentEvalThread = new AtomicReference<>();
  private static final Pattern GRAB_COORDINATE_PATTERN = Pattern.compile("([\\w.-]+)#([\\w.-]+);([\\w.-]+)");

  private GadeRunnerEngine() {}

  /**
   * Main entry point, invoked reflectively from {@link GadeRunnerMain}.
   * <p>
   * Project dependency paths (for Gradle/Maven runtimes) are added to the shell's
   * {@link GroovyClassLoader} via {@code addURL()}. This puts deps on the same
   * classloader as scripts — matching how {@code @Grab} works in GADE mode and
   * avoiding double Log4j initialization (DefaultConsole-2 bug).
   *
   * @param reader          socket input
   * @param writer          socket output
   * @param runtimeType     runtime type name (GADE, GRADLE, MAVEN, CUSTOM)
   * @param mainDepPaths    main-scope dependency paths
   * @param testDepPaths    test-scope dependency paths (test-only delta)
   */
  public static void run(BufferedReader reader, BufferedWriter writer, String runtimeType,
                         String[] mainDepPaths, String[] testDepPaths) {
    Binding binding;
    GroovyShell mainShell;
    GroovyShell testShell;
    try {
      ClassLoader rootLoader = Thread.currentThread().getContextClassLoader();
      GroovyClassLoader mainLoader = createScriptLoader(runtimeType, rootLoader);
      addDependencyPaths(mainLoader, mainDepPaths);
      GroovyClassLoader testLoader = null;
      if (supportsTestLoader(runtimeType)) {
        testLoader = createScriptLoader(runtimeType, mainLoader);
        addDependencyPaths(testLoader, testDepPaths);
      }
      binding = new Binding();
      mainShell = new GroovyShell(mainLoader, binding);
      testShell = testLoader == null ? mainShell : new GroovyShell(testLoader, binding);
      emitRuntimeDiagnostics(runtimeType, mainLoader, testLoader, mainDepPaths, testDepPaths);
    } catch (Throwable t) {
      emitRaw("shell init failed: " + t);
      emitRaw(getStackTrace(t));
      emitError("init", "Shell init failed: " + t.getMessage(), getStackTrace(t), writer);
      return;
    }

    ConcurrentHashMap<String, CompletableFuture<Object>> guiPending = new ConcurrentHashMap<>();

    try {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        emitRaw("engine received: " + line);
        try {
          @SuppressWarnings("unchecked")
          Map<String, Object> cmd = ProtocolXml.fromXml(line);
          String action = (String) cmd.get("cmd");
          String type = (String) cmd.get("type");
          String id = (String) cmd.getOrDefault("id", UUID.randomUUID().toString());

          // Handle responses from Gade (gui_response, gui_error)
          if (type != null) {
            switch (type) {
              case "gui_response" -> handleGuiResponse(cmd, guiPending);
              case "gui_error" -> handleGuiError(cmd, guiPending);
              default -> emitRaw("Unhandled message type: " + type);
            }
            continue;
          }

          // Handle commands from Gade
          try {
            @SuppressWarnings("unchecked")
            Map<String, Object> bindings = (Map<String, Object>) cmd.get("bindings");
            switch (action) {
              case "eval" -> {
                boolean testContext = toBoolean(cmd.get("testContext"));
                GroovyShell shell = testContext ? testShell : mainShell;
                emitScriptDiagnosticsIfLoggingRelated((String) cmd.get("script"), shell.getClassLoader(), testContext);
                handleEval(binding, shell, id, (String) cmd.get("script"), bindings, writer, guiPending);
              }
              case "bindings" -> handleBindings(binding, id, writer);
              case "interrupt" -> handleInterrupt(id, writer);
              case "setWorkingDir" -> handleSetWorkingDir(id, (String) cmd.get("dir"), writer);
              case "shutdown" -> {
                emit(Map.of("type", "shutdown", "id", id), writer);
                return;
              }
              default -> emitError(id, "Unknown command: " + action, null, writer);
            }
          } catch (Exception e) {
            emitRaw("command failed: " + e);
            emitRaw(getStackTrace(e));
            emitError(id, e.getMessage(), getStackTrace(e), writer);
          }
        } catch (Exception parse) {
          emitRaw("protocol parse failed: " + parse);
          emitRaw(getStackTrace(parse));
          emitError("protocol", "Failed to parse command: " + parse.getMessage(), getStackTrace(parse), writer);
        }
      }
      emitRaw("engine received EOF, shutting down");
    } catch (IOException loopEx) {
      emitRaw("engine read loop exception: " + loopEx);
      emitRaw(getStackTrace(loopEx));
    }
  }

  private static GroovyClassLoader createScriptLoader(String runtimeType, ClassLoader parent) {
    if (supportsChildFirst(runtimeType)) {
      return new ChildFirstGroovyClassLoader(parent);
    }
    return new GroovyClassLoader(parent);
  }

  private static void addDependencyPaths(GroovyClassLoader loader, String[] depPaths) {
    if (depPaths == null) {
      return;
    }
    for (String path : depPaths) {
      try {
        loader.addURL(new File(path).toURI().toURL());
      } catch (Exception e) {
        emitRaw("skipping bad dependency path: " + path + " (" + e + ")");
      }
    }
  }

  private static boolean supportsChildFirst(String runtimeType) {
    return "GRADLE".equals(runtimeType) || "MAVEN".equals(runtimeType);
  }

  private static boolean supportsTestLoader(String runtimeType) {
    return "GRADLE".equals(runtimeType) || "MAVEN".equals(runtimeType);
  }

  private static boolean toBoolean(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean bool) {
      return bool;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }

  private static void handleEval(Binding binding, GroovyShell shell, String id, String script,
                                  Map<String, Object> bindings, BufferedWriter writer,
                                  ConcurrentHashMap<String, CompletableFuture<Object>> guiPending) {
    if (script == null) {
      emitError(id, "No script provided", null, writer);
      return;
    }
    if (currentEvalThread.get() != null) {
      emitError(id, "Runner is busy executing another script", null, writer);
      return;
    }
    Thread t = new Thread(() -> {
      currentEvalThread.set(Thread.currentThread());
      Thread.currentThread().setContextClassLoader(shell.getClassLoader());
      try {
        if (bindings != null) {
          ensureRemoteGuiInteractions(binding, bindings.get(GUI_INTERACTION_KEYS), writer, guiPending, shell);
          bindings.forEach((k, v) -> {
            if (!GUI_INTERACTION_KEYS.equals(k)) {
              binding.setVariable(k, v);
            }
          });
        }
        Object result = shell.evaluate(script);
        emit(Map.of("type", "result", "id", id, "result", result == null ? "null" : String.valueOf(result)), writer);
      } catch (Exception e) {
        emitGrabDiagnosticsIfRelevant(e);
        emitError(id, e.getMessage(), getStackTrace(e), writer);
      } finally {
        currentEvalThread.set(null);
      }
    }, "gade-runner-eval");
    t.start();
  }

  private static void ensureRemoteGuiInteractions(
      Binding binding, Object keys, BufferedWriter writer,
      ConcurrentHashMap<String, CompletableFuture<Object>> guiPending,
      GroovyShell shell) {
    if (keys == null) {
      return;
    }
    if (!(keys instanceof Iterable<?> iterable)) {
      return;
    }
    Map<String, Object> variables = binding.getVariables();
    for (Object key : iterable) {
      String name = key == null ? "" : String.valueOf(key).trim();
      if (name.isEmpty()) {
        continue;
      }
      if (variables.containsKey(name)) {
        continue;
      }
      RemoteInOut remoteInOut = new RemoteInOut(writer, guiPending);
      remoteInOut.setScriptClassLoader((GroovyClassLoader) shell.getClassLoader());
      binding.setVariable(name, remoteInOut);
    }
  }

  private static void handleBindings(Binding binding, String id, BufferedWriter writer) {
    Map<String, Object> variables = binding.getVariables();
    Map<String, String> serialized = new HashMap<>();
    variables.forEach((k, v) -> serialized.put(String.valueOf(k), v == null ? "null" : v.toString()));
    emit(Map.of("type", "bindings", "id", id, "bindings", serialized), writer);
  }

  private static void handleSetWorkingDir(String id, String dir, BufferedWriter writer) {
    if (dir != null && !dir.isBlank()) {
      System.setProperty("user.dir", dir);
      emitRaw("Working directory set to: " + dir);
    }
    emit(Map.of("type", "result", "id", id, "result", dir == null ? "" : dir), writer);
  }

  private static void handleInterrupt(String id, BufferedWriter writer) {
    Thread running = currentEvalThread.get();
    if (running != null) {
      running.interrupt();
      emit(Map.of("type", "interrupted", "id", id), writer);
    } else {
      emit(Map.of("type", "interrupted", "id", id, "message", "No script running"), writer);
    }
  }

  private static void handleGuiResponse(Map<String, Object> cmd,
                                         ConcurrentHashMap<String, CompletableFuture<Object>> guiPending) {
    String id = (String) cmd.get("id");
    if (id == null) {
      emitRaw("GUI response missing id, ignoring");
      return;
    }
    CompletableFuture<Object> future = guiPending.remove(id);
    if (future != null) {
      Object result = cmd.get("result");
      future.complete(result);
      emitRaw("GUI response completed for id: " + id);
    } else {
      emitRaw("GUI response for unknown id: " + id);
    }
  }

  private static void handleGuiError(Map<String, Object> cmd,
                                      ConcurrentHashMap<String, CompletableFuture<Object>> guiPending) {
    String id = (String) cmd.get("id");
    if (id == null) {
      emitRaw("GUI error missing id, ignoring");
      return;
    }
    CompletableFuture<Object> future = guiPending.remove(id);
    if (future != null) {
      String error = (String) cmd.getOrDefault("error", "GUI operation failed");
      future.completeExceptionally(new RuntimeException(error));
      emitRaw("GUI error completed for id: " + id + " error: " + error);
    } else {
      emitRaw("GUI error for unknown id: " + id);
    }
  }

  private static void emitError(String id, String msg, String stackTrace, BufferedWriter writer) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("type", "error");
    payload.put("id", id);
    payload.put("error", msg);
    if (stackTrace != null) {
      payload.put("stacktrace", stackTrace);
    }
    emit(payload, writer);
  }

  static void emit(Map<String, ?> payload, BufferedWriter writer) {
    String xml = ProtocolXml.toXml(payload);
    synchronized (writer) {
      try {
        writer.write(xml);
        writer.write("\n");
        writer.flush();
      } catch (IOException e) {
        emitRaw("emit failed: " + e);
      }
    }
  }

  private static void emitRaw(String msg) {
    if (!VERBOSE) {
      return;
    }
    try {
      ROOT_ERR.write((msg + "\n").getBytes(StandardCharsets.UTF_8));
      ROOT_ERR.flush();
    } catch (IOException e) {
      // Last resort
    }
  }

  private static String getStackTrace(Throwable t) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }

  private static void emitGrabDiagnosticsIfRelevant(Throwable error) {
    if (!isGrabFailure(error)) {
      return;
    }
    emitGrabDiag("Detected @Grab resolution failure");
    emitGrabDiag("error=" + error.getClass().getName() + ": " + safeMessage(error));
    emitGrabDiag("java.version=" + System.getProperty("java.version"));
    emitGrabDiag("java.home=" + System.getProperty("java.home"));
    emitGrabDiag("user.dir=" + System.getProperty("user.dir"));
    emitGrabDiag("grape.root=" + effectiveGrapeRoot());
    emitGrabDiag("ivy.default.ivy.user.dir=" + System.getProperty("ivy.default.ivy.user.dir"));
    emitGrabDiag("https.proxyHost=" + System.getProperty("https.proxyHost"));
    emitGrabDiag("https.proxyPort=" + System.getProperty("https.proxyPort"));
    emitGrabDiag("http.proxyHost=" + System.getProperty("http.proxyHost"));
    emitGrabDiag("http.proxyPort=" + System.getProperty("http.proxyPort"));
    emitGrabDiag("java.net.useSystemProxies=" + System.getProperty("java.net.useSystemProxies"));
    emitGrabDiag("dns(repo1.maven.org)=" + probeDns("repo1.maven.org"));
    emitGrabDiag("https(repo1.maven.org)=" + probeHttps("https://repo1.maven.org/maven2/"));
    emitGrabDiag("dns(repo.maven.apache.org)=" + probeDns("repo.maven.apache.org"));
    emitGrabDiag("https(repo.maven.apache.org)=" + probeHttps("https://repo.maven.apache.org/maven2/"));
    emitGrabDiag(dependencyCacheHints(error));
  }

  private static boolean isGrabFailure(Throwable error) {
    Throwable cursor = error;
    while (cursor != null) {
      String message = safeMessage(cursor);
      if (message.contains("Error grabbing Grapes")
          || message.contains("download failed")
          || message.contains("unresolved dependency")) {
        return true;
      }
      cursor = cursor.getCause();
    }
    return false;
  }

  private static String safeMessage(Throwable error) {
    return error.getMessage() == null ? "<no-message>" : error.getMessage();
  }

  private static String effectiveGrapeRoot() {
    String configured = System.getProperty("grape.root");
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    String userHome = System.getProperty("user.home");
    if (userHome == null || userHome.isBlank()) {
      return "<unknown>";
    }
    return userHome + File.separator + ".groovy" + File.separator + "grapes";
  }

  private static String probeDns(String host) {
    try {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      return Arrays.stream(addresses)
          .map(InetAddress::getHostAddress)
          .distinct()
          .sorted()
          .reduce((a, b) -> a + "," + b)
          .orElse("<none>");
    } catch (Exception e) {
      return e.getClass().getSimpleName() + ": " + safeMessage(e);
    }
  }

  private static String probeHttps(String url) {
    try {
      URLConnection conn = URI.create(url).toURL().openConnection();
      conn.setConnectTimeout(2500);
      conn.setReadTimeout(2500);
      conn.getInputStream().close();
      return "ok";
    } catch (Exception e) {
      return e.getClass().getSimpleName() + ": " + safeMessage(e);
    }
  }

  private static String dependencyCacheHints(Throwable error) {
    String message = collectMessages(error);
    Matcher matcher = GRAB_COORDINATE_PATTERN.matcher(message);
    if (!matcher.find()) {
      return "cacheHints=coordinate-not-detected";
    }
    String group = matcher.group(1);
    String artifact = matcher.group(2);
    String version = matcher.group(3);

    String userHome = System.getProperty("user.home", "");
    String m2RelPath = group.replace('.', File.separatorChar) + File.separator + artifact + File.separator + version;
    Path m2Dir = Path.of(userHome, ".m2", "repository", m2RelPath);
    Path m2Jar = m2Dir.resolve(artifact + "-" + version + ".jar");
    Path m2Pom = m2Dir.resolve(artifact + "-" + version + ".pom");

    Path grapeRoot = Path.of(effectiveGrapeRoot());
    Path ivyData = grapeRoot.resolve(Path.of(group, artifact, "ivydata-" + version + ".properties"));

    String ivyDataSummary = summarizeIvyData(ivyData);
    return "cacheHints="
        + group + ":" + artifact + ":" + version
        + " m2JarExists=" + Files.isRegularFile(m2Jar)
        + " m2PomExists=" + Files.isRegularFile(m2Pom)
        + " ivyDataExists=" + Files.isRegularFile(ivyData)
        + " ivyDataSummary={" + ivyDataSummary + "}";
  }

  private static String summarizeIvyData(Path ivyData) {
    if (!Files.isRegularFile(ivyData)) {
      return "missing";
    }
    try {
      List<String> lines = Files.readAllLines(ivyData);
      StringBuilder sb = new StringBuilder();
      for (String line : lines) {
        if (line.startsWith("resolver=") || line.startsWith("artifact.resolver=")
            || line.contains(".location=") || line.contains(".exists=")) {
          if (sb.length() > 0) {
            sb.append(" | ");
          }
          sb.append(line);
          if (sb.length() > 900) {
            sb.append(" ...");
            break;
          }
        }
      }
      return sb.isEmpty() ? "no-relevant-entries" : sb.toString();
    } catch (Exception e) {
      return "read-failed:" + e.getClass().getSimpleName() + ":" + safeMessage(e);
    }
  }

  private static String collectMessages(Throwable error) {
    StringBuilder sb = new StringBuilder();
    Throwable cursor = error;
    while (cursor != null) {
      String msg = safeMessage(cursor);
      if (!msg.isBlank()) {
        if (sb.length() > 0) {
          sb.append('\n');
        }
        sb.append(msg);
      }
      cursor = cursor.getCause();
    }
    return sb.toString();
  }

  private static void emitGrabDiag(String message) {
    System.err.println("[GRAB_DIAG] " + message);
  }

  private static void emitRuntimeDiagnostics(String runtimeType, GroovyClassLoader mainLoader, GroovyClassLoader testLoader,
                                             String[] mainDepPaths, String[] testDepPaths) {
    if (!"GRADLE".equals(runtimeType) && !"MAVEN".equals(runtimeType)) {
      return;
    }
    emitRunnerDiag("runtimeType=" + runtimeType
        + " mainDepCount=" + (mainDepPaths == null ? 0 : mainDepPaths.length)
        + " testDepCount=" + (testDepPaths == null ? 0 : testDepPaths.length));
    emitRunnerDiag("mainDep log jars: " + findLoggingJars(mainDepPaths));
    emitRunnerDiag("testDep log jars: " + findLoggingJars(testDepPaths));
    emitRunnerDiag("mainLoader chain: " + classLoaderChain(mainLoader));
    if (testLoader != null && testLoader != mainLoader) {
      emitRunnerDiag("testLoader chain: " + classLoaderChain(testLoader));
    }
    emitRunnerDiag("mainLoader log4j.Level origin: " + classOrigin("org.apache.logging.log4j.Level", mainLoader));
    emitRunnerDiag("mainLoader log4j.core.StatusData origin: " + classOrigin("org.apache.logging.log4j.status.StatusData", mainLoader));
    emitRunnerDiag("mainLoader slf4j.LoggerFactory origin: " + classOrigin("org.slf4j.LoggerFactory", mainLoader));
    emitRunnerDiag("mainLoader log4j-slf4j binding origin: " + classOrigin("org.apache.logging.slf4j.Log4jLoggerFactory", mainLoader));
    emitRunnerDiag("sysprop log4j2.configurationFile=" + System.getProperty("log4j2.configurationFile"));
    emitRunnerDiag("sysprop log4j.configurationFile=" + System.getProperty("log4j.configurationFile"));
    emitRunnerDiag("mainLoader resources log4j2.xml: " + resourceOrigins("log4j2.xml", mainLoader, 8));
    emitRunnerDiag("mainLoader resources log4j2-test.xml: " + resourceOrigins("log4j2-test.xml", mainLoader, 8));
    emitRunnerDiag("mainLoader resources slf4j-provider: " + resourceOrigins("META-INF/services/org.slf4j.spi.SLF4JServiceProvider", mainLoader, 8));
  }

  private static void emitScriptDiagnosticsIfLoggingRelated(String script, ClassLoader scriptLoader, boolean testContext) {
    if (script == null) {
      return;
    }
    if (!(script.contains("LoggerFactory")
        || script.contains("Configurator")
        || script.contains("org.apache.logging")
        || script.contains("org.slf4j"))) {
      return;
    }
    emitRunnerDiag("eval logging-script detected; testContext=" + testContext);
    emitRunnerDiag("scriptLoader chain: " + classLoaderChain(scriptLoader));
    emitRunnerDiag("scriptLoader log4j.Level origin: " + classOrigin("org.apache.logging.log4j.Level", scriptLoader));
    emitRunnerDiag("scriptLoader log4j.core.StatusData origin: " + classOrigin("org.apache.logging.log4j.status.StatusData", scriptLoader));
    emitRunnerDiag("scriptLoader slf4j.LoggerFactory origin: " + classOrigin("org.slf4j.LoggerFactory", scriptLoader));
    emitRunnerDiag("scriptLoader log4j-slf4j binding origin: " + classOrigin("org.apache.logging.slf4j.Log4jLoggerFactory", scriptLoader));
    emitRunnerDiag("thread context loader: " + classLoaderChain(Thread.currentThread().getContextClassLoader()));
    emitRunnerDiag("scriptLoader resources log4j2.xml: " + resourceOrigins("log4j2.xml", scriptLoader, 8));
    emitRunnerDiag("scriptLoader resources slf4j-provider: " + resourceOrigins("META-INF/services/org.slf4j.spi.SLF4JServiceProvider", scriptLoader, 8));
    emitLog4jContextSnapshot(scriptLoader);
  }

  private static String findLoggingJars(String[] entries) {
    if (entries == null || entries.length == 0) {
      return "<none>";
    }
    List<String> matches = Arrays.stream(entries)
        .filter(path -> path != null
            && (path.contains("log4j") || path.contains("slf4j")))
        .map(path -> {
          File file = new File(path);
          return file.getName();
        })
        .distinct()
        .sorted()
        .toList();
    return matches.isEmpty() ? "<none>" : String.join(", ", matches);
  }

  private static String classLoaderChain(ClassLoader loader) {
    if (loader == null) {
      return "<bootstrap>";
    }
    StringBuilder sb = new StringBuilder();
    ClassLoader current = loader;
    while (current != null) {
      if (!sb.isEmpty()) {
        sb.append(" -> ");
      }
      sb.append(current.getClass().getSimpleName());
      current = current.getParent();
    }
    sb.append(" -> <bootstrap>");
    return sb.toString();
  }

  private static String classOrigin(String fqcn, ClassLoader loader) {
    try {
      Class<?> cls = Class.forName(fqcn, false, loader);
      String location = "<unknown>";
      if (cls.getProtectionDomain() != null
          && cls.getProtectionDomain().getCodeSource() != null
          && cls.getProtectionDomain().getCodeSource().getLocation() != null) {
        String raw = cls.getProtectionDomain().getCodeSource().getLocation().toString();
        if (raw.endsWith("/")) {
          location = raw;
        } else {
          location = new File(raw).getName();
        }
      }
      ClassLoader clsLoader = cls.getClassLoader();
      String loaderName = clsLoader == null ? "<bootstrap>" : clsLoader.getClass().getSimpleName();
      return location + " via " + loaderName;
    } catch (Throwable t) {
      return "NOT_FOUND(" + t.getClass().getSimpleName() + ": " + safeMessage(t) + ")";
    }
  }

  private static String resourceOrigins(String resourceName, ClassLoader loader, int limit) {
    try {
      Enumeration<URL> resources = loader == null ? ClassLoader.getSystemResources(resourceName) : loader.getResources(resourceName);
      List<String> origins = new ArrayList<>();
      while (resources.hasMoreElements() && origins.size() < limit) {
        origins.add(String.valueOf(resources.nextElement()));
      }
      if (origins.isEmpty()) {
        return "<none>";
      }
      boolean truncated = resources.hasMoreElements();
      String joined = String.join(" | ", origins);
      return truncated ? joined + " | ... (truncated)" : joined;
    } catch (Throwable t) {
      return "ERROR(" + t.getClass().getSimpleName() + ": " + safeMessage(t) + ")";
    }
  }

  private static void emitLog4jContextSnapshot(ClassLoader loader) {
    try {
      Class<?> logManagerClass = Class.forName("org.apache.logging.log4j.LogManager", false, loader);
      Object context = logManagerClass.getMethod("getContext", boolean.class).invoke(null, false);
      if (context == null) {
        emitRunnerDiag("log4j context: <null>");
        return;
      }
      emitRunnerDiag("log4j context class: " + context.getClass().getName() + " via " + classLoaderName(context.getClass().getClassLoader()));

      try {
        Object configuration = context.getClass().getMethod("getConfiguration").invoke(context);
        if (configuration == null) {
          emitRunnerDiag("log4j configuration: <null>");
          return;
        }
        emitRunnerDiag("log4j configuration class: " + configuration.getClass().getName()
            + " via " + classLoaderName(configuration.getClass().getClassLoader()));
        try {
          Object configName = configuration.getClass().getMethod("getName").invoke(configuration);
          emitRunnerDiag("log4j configuration name: " + String.valueOf(configName));
        } catch (NoSuchMethodException ignored) {
          // Some contexts don't expose configuration name.
        }
        try {
          Object source = configuration.getClass().getMethod("getConfigurationSource").invoke(configuration);
          emitRunnerDiag("log4j configuration source: " + String.valueOf(source));
        } catch (NoSuchMethodException ignored) {
          // Some contexts don't expose configuration source.
        }
        try {
          Object appenders = configuration.getClass().getMethod("getAppenders").invoke(configuration);
          emitRunnerDiag("log4j appenders: " + summarizeAppenders(appenders));
        } catch (NoSuchMethodException ignored) {
          // Some contexts don't expose appender maps.
        }
      } catch (NoSuchMethodException ignored) {
        emitRunnerDiag("log4j context does not expose configuration details");
      }
    } catch (Throwable t) {
      emitRunnerDiag("log4j context snapshot unavailable: " + t.getClass().getSimpleName() + ": " + safeMessage(t));
    }
  }

  private static String summarizeAppenders(Object appenders) {
    if (!(appenders instanceof Map<?, ?> map)) {
      return appenders == null ? "<null>" : appenders.getClass().getName();
    }
    List<String> names = map.keySet().stream()
        .map(String::valueOf)
        .sorted()
        .toList();
    if (names.size() <= 8) {
      return String.join(", ", names);
    }
    return String.join(", ", names.subList(0, 8)) + " ... (total " + names.size() + ")";
  }

  private static String classLoaderName(ClassLoader loader) {
    return loader == null ? "<bootstrap>" : loader.getClass().getSimpleName();
  }

  private static void emitRunnerDiag(String message) {
    System.out.println(RUNNER_DIAG_PREFIX + message);
  }
}
