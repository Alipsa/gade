package se.alipsa.gade.runtime;

import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.runner.ArgumentSerializer;
import se.alipsa.gade.runner.GadeRunnerMain;
import se.alipsa.gade.utils.gradle.GradleUtils;
import se.alipsa.gi.GuiInteraction;
import se.alipsa.groovy.resolver.Dependency;

import java.io.*;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Manages a long-lived external Groovy runner process for non-GADE runtimes.
 * <p>
 * Handles subprocess lifecycle (start, communication, shutdown) and provides asynchronous
 * script evaluation via XML protocol over TCP sockets.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe for concurrent script evaluation requests.
 * The {@link #start()} and {@link #close()} methods use synchronized blocks with {@code procLock}
 * to protect process lifecycle operations. Pending request tracking uses {@link ConcurrentHashMap}
 * for lock-free concurrent access. The stderr buffer uses {@link LinkedBlockingDeque} for
 * thread-safe producer-consumer pattern. Multiple threads can safely submit eval requests
 * concurrently; responses are delivered via {@link CompletableFuture}.
 *
 * @see CompletableFuture
 * @see ConcurrentHashMap
 * <p><b>Thread-Safety:</b> This class is thread-safe.</p>
 */
public class RuntimeProcessRunner implements Closeable {

  private static final Logger log = LogManager.getLogger(RuntimeProcessRunner.class);
  private static final int STDERR_BUFFER_SIZE = 50;
  private static final int MAX_CONNECT_RETRIES = 50;
  private static final long CONNECT_RETRY_SLEEP_MS = 50;
  private static final int CONNECT_HANDSHAKE_TIMEOUT_MS = 2000;
  private static final long STARTUP_EXIT_CHECK_TIMEOUT_MS = 200;
  private static final List<String> INHERITED_NETWORK_SYSTEM_PROPERTIES = List.of(
      "http.proxyHost",
      "http.proxyPort",
      "https.proxyHost",
      "https.proxyPort",
      "http.nonProxyHosts",
      "socksProxyHost",
      "socksProxyPort",
      "java.net.useSystemProxies"
  );

  private final RuntimeConfig runtime;
  private final List<String> classPathEntries;
  private final List<String> groovyEntries;
  private final List<String> mainDepEntries;
  private final List<String> testDepEntries;
  private final ConsoleTextArea console;
  private final Map<String, GuiInteraction> guiInteractions;
  private volatile File workingDir;

  private Process process;
  private Socket socket;
  private BufferedWriter socketWriter;
  private BufferedReader socketReader;
  private int runnerPort;
  private ExecutorService readerService;
  private final Map<String, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();
  private final LinkedBlockingDeque<String> stderrBuffer = new LinkedBlockingDeque<>(STDERR_BUFFER_SIZE);
  private final Object procLock = new Object();

  public RuntimeProcessRunner(RuntimeConfig runtime, List<String> classPathEntries, ConsoleTextArea console, Map<String, GuiInteraction> guiInteractions) {
    this(runtime, classPathEntries, List.of(), List.of(), List.of(), console, guiInteractions, null);
  }

  public RuntimeProcessRunner(RuntimeConfig runtime, List<String> classPathEntries, ConsoleTextArea console, Map<String, GuiInteraction> guiInteractions, File workingDir) {
    this(runtime, classPathEntries, List.of(), List.of(), List.of(), console, guiInteractions, workingDir);
  }

  public RuntimeProcessRunner(RuntimeConfig runtime, List<String> classPathEntries,
                               List<String> groovyEntries, List<String> projectDepEntries,
                               ConsoleTextArea console, Map<String, GuiInteraction> guiInteractions, File workingDir) {
    this(runtime, classPathEntries, groovyEntries, projectDepEntries, List.of(), console, guiInteractions, workingDir);
  }

  public RuntimeProcessRunner(RuntimeConfig runtime, List<String> classPathEntries,
                               List<String> groovyEntries, List<String> mainDepEntries, List<String> testDepEntries,
                               ConsoleTextArea console, Map<String, GuiInteraction> guiInteractions, File workingDir) {
    this.runtime = runtime;
    this.classPathEntries = classPathEntries;
    this.groovyEntries = groovyEntries;
    this.mainDepEntries = mainDepEntries;
    this.testDepEntries = testDepEntries;
    this.console = console;
    this.guiInteractions = guiInteractions;
    this.workingDir = workingDir;
    if (log.isDebugEnabled()) {
      log.debug("Runner classpath entries ({}): {}", classPathEntries.size(), classPathEntries);
      if (!groovyEntries.isEmpty()) {
        log.debug("Runner groovy bootstrap entries ({}): {}", groovyEntries.size(), groovyEntries);
      }
      if (!mainDepEntries.isEmpty()) {
        log.debug("Runner main dep entries ({}): {}", mainDepEntries.size(), mainDepEntries);
      }
      if (!testDepEntries.isEmpty()) {
        log.debug("Runner test dep entries ({}): {}", testDepEntries.size(), testDepEntries);
      }
    }
  }

  public synchronized void start() throws IOException {
    synchronized (procLock) {
      if (process != null && process.isAlive() && socket != null && socket.isConnected() && !socket.isClosed()) {
        return;
      }
      log.info("Starting runner for runtime {}", runtime.getName());
      if (classPathEntries.isEmpty()) {
        console.appendWarningFx("Cannot start runtime process: no classpath entries were found");
        throw new IOException("Classpath for runner is empty");
      }
      log.info("Runner command javaHome={}, classpath size={}", runtime.getJavaHome(), classPathEntries.size());
      if (log.isDebugEnabled()) {
        log.debug("Runner classpath entries:\n{}", String.join("\n", classPathEntries));
      }
      List<String> cpOrdered = new ArrayList<>(classPathEntries);
      // Prefer runner classes/resources first to avoid shadowing by Groovy jars
      cpOrdered.sort((a, b) -> {
        boolean aRunner = isRunnerPath(a);
        boolean bRunner = isRunnerPath(b);
        if (aRunner == bRunner) return 0;
        return aRunner ? -1 : 1;
      });
      runnerPort = pickPort();
      List<String> cmd = new ArrayList<>();
      cmd.add(resolveJavaExecutable());
      addInheritedNetworkSystemProperties(cmd);
      if (hasProcessRootLoaderOnClasspath(cpOrdered)) {
        cmd.add("-Djava.system.class.loader=se.alipsa.gade.runner.ProcessRootLoader");
      }
      cmd.add("-cp");
      cmd.add(String.join(File.pathSeparator, cpOrdered));
      cmd.add(GadeRunnerMain.class.getName());
      // Use a pre-selected loopback port for the runner socket
      cmd.add(String.valueOf(runnerPort));
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(false);
      if (workingDir != null && workingDir.isDirectory()) {
        pb.directory(workingDir);
      }
      if (log.isDebugEnabled()) {
        log.debug("Runner command line: {}", String.join(" ", cmd));
      } else {
        log.info("Starting runner {} on port {}", runtime.getName(), runnerPort);
      }
      try {
        process = pb.start();
        process.onExit().thenAccept(p -> log.warn("Runner for {} exited (async) with code {}", runtime.getName(), p.exitValue()));
        readerService = Executors.newFixedThreadPool(2, r -> {
          Thread t = new Thread(r, "gade-runner-reader");
          t.setDaemon(true);
          return t;
        });
        readerService.submit(this::stderrLoop);
        connectWithRetries();

        // If the process dies immediately, capture stderr/stdout for diagnostics.
        if (process.waitFor(STARTUP_EXIT_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
          logProcessExit("immediate start check");
          throw new IOException("Runtime runner exited immediately");
        }
      } catch (IOException e) {
        console.appendWarningFx("Failed to start runtime process: " + e.getMessage());
        throw e;
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while starting runtime process", ie);
      }
    }
  }

  private void addInheritedNetworkSystemProperties(List<String> cmd) {
    for (String key : INHERITED_NETWORK_SYSTEM_PROPERTIES) {
      String value = System.getProperty(key);
      if (value != null && !value.isBlank()) {
        cmd.add("-D" + key + "=" + value);
      }
    }
  }

  public CompletableFuture<String> eval(String script, Map<String, Object> bindings) throws IOException {
    return eval(script, bindings, false);
  }

  public CompletableFuture<String> eval(String script, Map<String, Object> bindings, boolean testContext) throws IOException {
    ensureStarted();
    String id = UUID.randomUUID().toString();
    Map<String, Object> payload = new HashMap<>();
    payload.put("cmd", "eval");
    payload.put("id", id);
    payload.put("script", script);
    payload.put("testContext", testContext);
    if (bindings != null && !bindings.isEmpty()) {
      payload.put("bindings", bindings);
    }
    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
    pending.put(id, future);
    send(payload);
    return future.thenApply(map -> (String) map.getOrDefault("result", ""));
  }

  public CompletableFuture<Map<String, String>> fetchBindings() throws IOException {
    ensureStarted();
    String id = UUID.randomUUID().toString();
    Map<String, Object> payload = Map.of("cmd", "bindings", "id", id);
    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
    pending.put(id, future);
    send(payload);
    return future.thenApply(map -> {
      Object bindings = map.get("bindings");
      if (bindings instanceof Map<?, ?> m) {
        Map<String, String> result = new HashMap<>();
        m.forEach((k, v) -> result.put(String.valueOf(k), v == null ? "null" : String.valueOf(v)));
        return result;
      }
      return Collections.emptyMap();
    });
  }

  public void setWorkingDir(File dir) {
    this.workingDir = dir;
    if (dir == null) {
      return;
    }
    try {
      if (process != null && process.isAlive() && socket != null && !socket.isClosed()) {
        send(Map.of("cmd", "setWorkingDir", "id", UUID.randomUUID().toString(),
            "dir", dir.getAbsolutePath()));
      }
    } catch (IOException e) {
      log.debug("Failed to send setWorkingDir to runner", e);
    }
  }

  public void interrupt() throws IOException {
    if (ensureStarted()) {
      send(Map.of("cmd", "interrupt", "id", UUID.randomUUID().toString()));
    }
  }

  public synchronized void stop() {
    try {
      if (process != null && process.isAlive()) {
        send(Map.of("cmd", "shutdown", "id", UUID.randomUUID().toString()));
        process.destroy();
      }
    } catch (Exception e) {
      log.debug("Failed to shutdown runner cleanly", e);
      if (process != null) {
        process.destroyForcibly();
      }
    } finally {
      closeQuietly(socketReader);
      closeQuietly(socketWriter);
      closeQuietly(socket);
      cleanup();
    }
  }

  private void send(Map<String, Object> payload) throws IOException {
    synchronized (procLock) {
      IOException last = null;
      for (int attempt = 0; attempt < 2; attempt++) {
        if (!ensureStarted()) {
          throw new IOException("Runner process is not available");
        }
        try {
          if (log.isDebugEnabled()) {
            log.debug("Sending to runner {} attempt {} (alive={}, socketClosed={}, port={}): {}",
                runtime.getName(), attempt + 1,
                process != null && process.isAlive(),
                socket == null || socket.isClosed(),
                runnerPort,
                payload.get("cmd"));
          }
          if (socket == null || socket.isClosed()) {
            throw new IOException("Runner socket is closed before send");
          }
          socketWriter.write(ProtocolXml.toXml(payload));
          socketWriter.write("\n");
          socketWriter.flush();
          return;
        } catch (IOException e) {
          last = e;
          log.warn("send failed (attempt {}), processAlive={}, socketClosed={}", attempt + 1,
              process != null && process.isAlive(), socket == null || socket.isClosed(), e);
          logProcessExit("send failure");
          cleanup();
        }
      }
      throw last == null ? new IOException("Runner process is not available") : last;
    }
  }

  private synchronized boolean ensureStarted() throws IOException {
    synchronized (procLock) {
      if (process != null) {
        if (process.isAlive()) {
          if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return true;
          }
        }
        logProcessExit("ensureStarted");
      }
      cleanup();
      start();
      return process != null && process.isAlive() && socket != null && socket.isConnected() && !socket.isClosed();
    }
  }

  private void socketReadLoop() {
    try {
      log.info("Socket read loop started for runtime {}", runtime.getName());
      String line;
      while ((line = socketReader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        if (log.isDebugEnabled()) {
          log.debug("Runner {} -> {}", runtime.getName(), line);
        }
        try {
          Map<String, Object> msg = ProtocolXml.fromXml(line);
          handleMessage(msg);
        } catch (Exception parse) {
          log.warn("Failed to parse runner message: {}", line, parse);
        }
      }
      log.info("Socket read loop ended (EOF) for runtime {}", runtime.getName());
    } catch (IOException e) {
      log.warn("Runner socketReadLoop ended with exception: {}", e.toString());
    } finally {
      pending.values().forEach(f -> f.completeExceptionally(new IllegalStateException("Runner stopped")));
      pending.clear();
      logProcessExit("socketReadLoop end");
      cleanup();
    }
  }

  private void stderrLoop() {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        console.appendWarningFx(line);
        log.warn("Runner stderr: {}", line);
        bufferStderrLine(line);
      }
    } catch (IOException e) {
      log.debug("Runner stderrLoop ended: {}", e.getMessage());
    } finally {
      logProcessExit("stderrLoop end");
    }
  }

  private void bufferStderrLine(String line) {
    while (!stderrBuffer.offer(line)) {
      stderrBuffer.poll();
    }
  }

  private String readAll(InputStream in) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      return sb.toString();
    } catch (IOException e) {
      return "";
    }
  }

  private void logProcessExit(String context) {
    synchronized (procLock) {
      if (process == null) {
        return;
      }
      if (process.isAlive()) {
        return;
      }
      try {
        int exit = process.exitValue();
        String err = readAll(process.getErrorStream());
        String out = readAll(process.getInputStream());
        log.warn("Runner for {} exited in {} with code {}", runtime.getName(), context, exit);
        if (!err.isBlank()) {
          log.warn("Runner stderr ({}):\n{}", context, err);
          console.appendWarningFx("Runtime runner stderr: " + err);
        }
        if (!out.isBlank()) {
          log.warn("Runner stdout ({}):\n{}", context, out);
        }
      } catch (IllegalThreadStateException ignored) {
        // still alive
      }
    }
  }

  private void handleMessage(Map<String, Object> msg) {
    String type = (String) msg.get("type");
    if (type == null) {
      return;
    }
    switch (type) {
      case "hello" -> {
        String version = (String) msg.get("protocolVersion");
        if (!ProtocolVersion.isCompatible(version)) {
          log.error("Runner {} protocol version incompatible: {}", runtime.getName(),
              ProtocolVersion.getCompatibilityMessage(version));
          console.appendWarningFx("Runner protocol version incompatible: " + version);
        } else {
          log.debug("Runner {} handshake received: {} - {}",
              runtime.getName(), msg, ProtocolVersion.getCompatibilityMessage(version));
        }
      }
      case "out" -> {
        String text = (String) msg.getOrDefault("text", "");
        log.info("Runner {} out: {}", runtime.getName(), text.replace("\n", "\\n"));
        console.appendFx(text, false);
      }
      case "err" -> {
        String text = (String) msg.getOrDefault("text", "");
        log.info("Runner {} err: {}", runtime.getName(), text.replace("\n", "\\n"));
        console.appendWarningFx(text);
      }
      case "result", "bindings", "interrupted", "shutdown" -> complete(msg);
      case "error" -> completeExceptionally(msg);
      case "gui_request" -> handleGuiRequest(msg);
      default -> log.debug("Unhandled runner message type {}", type);
    }
  }

  private void complete(Map<String, Object> msg) {
    String id = (String) msg.get("id");
    CompletableFuture<Map<String, Object>> future = pending.remove(id);
    if (future != null) {
      future.complete(msg);
    }
  }

  private void completeExceptionally(Map<String, Object> msg) {
    String id = (String) msg.get("id");
    CompletableFuture<Map<String, Object>> future = pending.remove(id);
    if (future != null) {
      future.completeExceptionally(new RuntimeException((String) msg.getOrDefault("error", "Runner error")));
    }
    String err = (String) msg.getOrDefault("error", "");
    String stack = (String) msg.getOrDefault("stacktrace", "");
    console.appendWarningFx(err + (stack == null ? "" : "\n" + stack));
  }

  /**
   * Handle GUI request from remote runner.
   * Deserializes arguments, invokes the real InOut method on JavaFX thread, and sends response.
   */
  private void handleGuiRequest(Map<String, Object> msg) {
    String id = (String) msg.get("id");
    String method = (String) msg.get("method");
    List<?> argsList = (List<?>) msg.get("args");

    if (id == null || method == null) {
      log.warn("GUI request missing id or method, ignoring: {}", msg);
      return;
    }

    log.debug("Handling GUI request: method={}, id={}", method, id);

    // resolveDependency runs on a background thread, not the FX thread
    if ("resolveDependency".equals(method)) {
      handleResolveDependency(id, argsList);
      return;
    }

    // Run on JavaFX thread since GUI operations require it
    Platform.runLater(() -> {
      try {
        // Get the real InOut instance
        GuiInteraction inOut = guiInteractions.get("io");
        if (inOut == null) {
          sendGuiError(id, "InOut instance not available");
          return;
        }

        // Deserialize arguments
        Object[] args = argsList == null ? new Object[0] : argsList.stream()
            .map(ArgumentSerializer::deserialize)
            .toArray();

        // Invoke method via reflection
        Object result = invokeMethod(inOut, method, args);

        // Serialize result
        Object serializedResult = ArgumentSerializer.serialize(result);

        // Send response
        Map<String, Object> response = new HashMap<>();
        response.put("type", "gui_response");
        response.put("id", id);
        response.put("result", serializedResult);
        send(response);

        log.debug("GUI request completed: method={}, id={}", method, id);

      } catch (Exception e) {
        log.error("GUI request failed: method={}, id={}", method, id, e);
        sendGuiError(id, e.getMessage());
      }
    });
  }

  /**
   * Invoke a method on the InOut instance using reflection.
   * Attempts to find a matching method by name and argument count/types.
   */
  private Object invokeMethod(GuiInteraction inOut, String methodName, Object[] args) throws Exception {
    Class<?> clazz = inOut.getClass();

    // Try to find matching method by name and argument count
    Method[] methods = clazz.getMethods();
    Method bestMatch = null;
    int bestScore = -1;

    for (Method method : methods) {
      if (!method.getName().equals(methodName)) {
        continue;
      }

      Class<?>[] paramTypes = method.getParameterTypes();

      // Handle varargs by checking minimum parameter count
      int minParams = paramTypes.length;
      boolean hasVarargs = method.isVarArgs();
      if (hasVarargs) {
        minParams = paramTypes.length - 1;
      }

      // Check if argument count matches
      if (args.length < minParams || (!hasVarargs && args.length != paramTypes.length)) {
        continue;
      }

      // Score based on how well types match
      int score = scoreMethodMatch(paramTypes, args, hasVarargs);
      if (score > bestScore) {
        bestScore = score;
        bestMatch = method;
      }
    }

    if (bestMatch == null) {
      throw new NoSuchMethodException("No matching method found: " + methodName + " with " + args.length + " arguments");
    }

    // Invoke the method with proper varargs handling
    if (bestMatch.isVarArgs()) {
      Class<?>[] paramTypes = bestMatch.getParameterTypes();
      int fixedParams = paramTypes.length - 1;

      // Prepare arguments array with varargs properly set up
      Object[] invokeArgs = new Object[paramTypes.length];

      // Copy fixed parameters
      for (int i = 0; i < fixedParams; i++) {
        invokeArgs[i] = i < args.length ? args[i] : null;
      }

      // Create varargs array
      Class<?> varargsType = paramTypes[fixedParams].getComponentType();
      int varargsCount = Math.max(0, args.length - fixedParams);
      Object varargsArray = java.lang.reflect.Array.newInstance(varargsType, varargsCount);
      for (int i = 0; i < varargsCount; i++) {
        java.lang.reflect.Array.set(varargsArray, i, args[fixedParams + i]);
      }
      invokeArgs[fixedParams] = varargsArray;

      return bestMatch.invoke(inOut, invokeArgs);
    } else {
      return bestMatch.invoke(inOut, args);
    }
  }

  /**
   * Score how well a method's parameters match the provided arguments.
   * Higher score means better match.
   */
  private int scoreMethodMatch(Class<?>[] paramTypes, Object[] args, boolean hasVarargs) {
    int score = 0;

    for (int i = 0; i < args.length; i++) {
      if (args[i] == null) {
        score += 1; // Null matches any reference type
        continue;
      }

      Class<?> paramType;
      if (hasVarargs && i >= paramTypes.length - 1) {
        // Varargs parameter
        paramType = paramTypes[paramTypes.length - 1].getComponentType();
      } else if (i < paramTypes.length) {
        paramType = paramTypes[i];
      } else {
        return -1; // Too many arguments
      }

      Class<?> argType = args[i].getClass();

      if (paramType.isAssignableFrom(argType)) {
        score += 10; // Exact or subtype match
      } else if (isCompatiblePrimitive(paramType, argType)) {
        score += 5; // Primitive/wrapper match
      } else {
        return -1; // Incompatible type
      }
    }

    return score;
  }

  /**
   * Check if a primitive type and wrapper type are compatible.
   */
  private boolean isCompatiblePrimitive(Class<?> paramType, Class<?> argType) {
    if (paramType == int.class && argType == Integer.class) return true;
    if (paramType == long.class && argType == Long.class) return true;
    if (paramType == double.class && argType == Double.class) return true;
    if (paramType == float.class && argType == Float.class) return true;
    if (paramType == boolean.class && argType == Boolean.class) return true;
    if (paramType == byte.class && argType == Byte.class) return true;
    if (paramType == short.class && argType == Short.class) return true;
    if (paramType == char.class && argType == Character.class) return true;
    return false;
  }

  /**
   * Send GUI error response back to runner.
   */
  private void sendGuiError(String id, String error) {
    try {
      Map<String, Object> response = new HashMap<>();
      response.put("type", "gui_error");
      response.put("id", id);
      response.put("error", error == null ? "GUI operation failed" : error);
      send(response);
    } catch (IOException e) {
      log.error("Failed to send GUI error response for id={}", id, e);
    }
  }

  /**
   * Handle resolveDependency request on a background thread.
   * Resolves a Maven dependency and returns the jar paths to the subprocess.
   */
  private void handleResolveDependency(String id, List<?> argsList) {
    CompletableFuture.runAsync(() -> {
      try {
        String depString = argsList != null && !argsList.isEmpty()
            ? String.valueOf(ArgumentSerializer.deserialize(argsList.get(0)))
            : null;
        if (depString == null || depString.isBlank()) {
          sendGuiError(id, "resolveDependency requires a dependency string argument");
          return;
        }

        Dependency dep = new Dependency(depString);
        GradleUtils.addDependencies(dep);

        // Collect resolved jar paths from the dynamic classloader
        List<String> jarPaths = new ArrayList<>();
        java.net.URL[] urls = Gade.instance().dynamicClassLoader.getURLs();
        for (java.net.URL url : urls) {
          try {
            jarPaths.add(java.nio.file.Paths.get(url.toURI()).toFile().getAbsolutePath());
          } catch (Exception e) {
            log.debug("Failed to convert resolved URL {}", url, e);
          }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("type", "gui_response");
        response.put("id", id);
        response.put("result", jarPaths);
        send(response);

        log.debug("resolveDependency completed: dep={}, jars={}", depString, jarPaths.size());

      } catch (Exception e) {
        log.error("resolveDependency failed: id={}", id, e);
        sendGuiError(id, e.getMessage());
      }
    }).orTimeout(60, TimeUnit.SECONDS)
      .exceptionally(ex -> {
        if (ex instanceof java.util.concurrent.TimeoutException) {
          sendGuiError(id, "resolveDependency timed out after 60 seconds");
        }
        return null;
      });
  }

  private String resolveJavaExecutable() {
    String javaHome = runtime.getJavaHome();
    if (javaHome == null || javaHome.isBlank()) {
      javaHome = System.getProperty("java.home");
    }
    if (javaHome != null && !javaHome.isBlank()) {
      File java = new File(javaHome, "bin/java");
      if (java.exists()) {
        return java.getAbsolutePath();
      }
      console.appendWarningFx("JAVA_HOME '" + javaHome + "' is not usable, falling back to current JVM");
      log.warn("JAVA_HOME '{}' does not contain bin/java, falling back to default", javaHome);
    }
    return "java";
  }

  private void cleanup() {
    synchronized (procLock) {
      if (readerService != null) {
        readerService.shutdownNow();
      }
      process = null;
      closeQuietly(socketReader);
      closeQuietly(socketWriter);
      closeQuietly(socket);
      socket = null;
      socketReader = null;
      socketWriter = null;
    }
  }

  private void closeQuietly(Closeable c) {
    if (c == null) {
      return;
    }
    try {
      c.close();
    } catch (IOException e) {
      log.debug("Failed to close closeable during cleanup, continuing", e);
    }
  }

  private int pickPort() throws IOException {
    try (ServerSocket ss = new ServerSocket(0, 1, loopbackV4())) {
      return ss.getLocalPort();
    }
  }

  private void connectWithRetries() throws IOException {
    IOException last = null;
    for (int i = 0; i < MAX_CONNECT_RETRIES; i++) {
      try {
        socket = new Socket(loopbackV4(), runnerPort);
        socketWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        // Synchronous handshake: expect a hello from the runner so we know the socket is usable
        try {
          socket.setSoTimeout(CONNECT_HANDSHAKE_TIMEOUT_MS);
          String helloLine = null;
          while (true) {
            String line = socketReader.readLine();
            if (line == null) {
              break;
            }
            if (line.isBlank()) {
              continue;
            }
            Map<String, Object> msg;
            try {
              msg = ProtocolXml.fromXml(line);
            } catch (Exception parse) {
              log.debug("Ignoring unparsable handshake line from runner: {}", line, parse);
              continue;
            }
            String type = (String) msg.get("type");
            if ("out".equals(type)) {
              console.appendFx(String.valueOf(msg.getOrDefault("text", "")), false);
              continue;
            }
            if ("err".equals(type)) {
              console.appendWarningFx(String.valueOf(msg.getOrDefault("text", "")));
              continue;
            }
            if ("error".equals(type)) {
              throw new IOException("Runner failed during startup: " + msg.getOrDefault("error", "unknown error"));
            }
            if ("hello".equals(type)) {
              helloLine = line;
              break;
            }
          }
          if (helloLine == null) {
            throw new IOException("Runner socket closed before handshake");
          }

          // Validate protocol version
          Map<String, Object> helloMsg = ProtocolXml.fromXml(helloLine);
          String version = (String) helloMsg.get("protocolVersion");
          if (!ProtocolVersion.isCompatible(version)) {
            String errorMsg = "Incompatible protocol version: " + ProtocolVersion.getCompatibilityMessage(version);
            log.error("Runner {} - {}", runtime.getName(), errorMsg);
            throw new IOException(errorMsg);
          }

          log.info("Received handshake from runner {}: {} - {}",
              runtime.getName(), helloLine, ProtocolVersion.getCompatibilityMessage(version));

          // Send addClasspath command with dependency entries
          sendAddClasspath();

          // Wait for classpathAdded ack
          waitForClasspathAdded(socket);

        } finally {
          try {
            socket.setSoTimeout(0);
          } catch (IOException e) {
            log.debug("Failed to reset socket timeout, continuing", e);
          }
        }
        readerService.submit(this::socketReadLoop);
        log.info("Connected to runner {} on port {}", runtime.getName(), runnerPort);
        return;
      } catch (IOException e) {
        last = e;
        if (i == MAX_CONNECT_RETRIES - 1) {
          log.warn("Failed to connect/handshake with runner on port {} after {} attempts: {}", runnerPort, i + 1, e.toString());
        } else {
          log.debug("Retrying connect/handshake with runner on port {}, attempt {}: {}", runnerPort, i + 1, e.toString());
        }
        sleepQuietly(CONNECT_RETRY_SLEEP_MS);
      }
    }
    throw new IOException("Failed to connect to runner on port " + runnerPort, last);
  }

  /**
   * Sends an addClasspath command with Groovy bootstrap entries and split project
   * dependency entries to the runner subprocess.
   * The runner uses these to build its classloader hierarchy:
   * <ul>
   *   <li>{@code groovyEntries} → bootstrap URLClassLoader (Groovy/Ivy jars)</li>
   *   <li>{@code mainEntries} → main script classloader (compile/runtime deps)</li>
   *   <li>{@code testEntries} → test script classloader (test-only deps)</li>
   * </ul>
   * For GADE/Custom runtimes main/test entries are empty.
   */
  private void sendAddClasspath() throws IOException {
    Map<String, Object> payload = new HashMap<>();
    payload.put("cmd", "addClasspath");
    payload.put("runtimeType", runtime.getType() == null ? RuntimeType.GADE.name() : runtime.getType().name());
    payload.put("groovyEntries", groovyEntries);
    payload.put("mainEntries", mainDepEntries);
    payload.put("testEntries", testDepEntries);
    socketWriter.write(ProtocolXml.toXml(payload));
    socketWriter.write("\n");
    socketWriter.flush();
    log.debug("Sent addClasspath to runner {} type={} with {} groovy + {} main + {} test entries",
        runtime.getName(), runtime.getType(), groovyEntries.size(), mainDepEntries.size(), testDepEntries.size());
  }

  /**
   * Waits for a classpathAdded acknowledgement from the runner subprocess.
   * Handles out/err messages that may arrive before the ack.
   */
  private void waitForClasspathAdded(Socket socket) throws IOException {
    // Reuse the handshake timeout for classpath loading
    socket.setSoTimeout(30_000); // 30 seconds — classpath loading may take time
    try {
      while (true) {
        String line = socketReader.readLine();
        if (line == null) {
          throw new IOException("Runner socket closed before classpathAdded");
        }
        if (line.isBlank()) {
          continue;
        }
        Map<String, Object> msg;
        try {
          msg = ProtocolXml.fromXml(line);
        } catch (Exception parse) {
          log.debug("Ignoring unparsable line during classpath loading: {}", line, parse);
          continue;
        }
        String type = (String) msg.get("type");
        if ("out".equals(type)) {
          console.appendFx(String.valueOf(msg.getOrDefault("text", "")), false);
          continue;
        }
        if ("err".equals(type)) {
          console.appendWarningFx(String.valueOf(msg.getOrDefault("text", "")));
          continue;
        }
        if ("error".equals(type)) {
          throw new IOException("Runner failed during classpath loading: " + msg.getOrDefault("error", "unknown error"));
        }
        if ("classpathAdded".equals(type)) {
          log.info("Runner {} acknowledged classpath loaded", runtime.getName());
          return;
        }
        log.debug("Ignoring unexpected message during classpath loading: {}", type);
      }
    } finally {
      try {
        socket.setSoTimeout(0);
      } catch (IOException e) {
        log.debug("Failed to reset socket timeout after classpath loading", e);
      }
    }
  }

  private void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  private InetAddress loopbackV4() throws IOException {
    try {
      return InetAddress.getByName("127.0.0.1");
    } catch (IOException e) {
      log.warn("Failed to resolve IPv4 loopback, falling back to default: {}", e.getMessage());
      return InetAddress.getLoopbackAddress();
    }
  }

  private boolean isRunnerPath(String path) {
    if (path == null) {
      return false;
    }
    String p = path.toLowerCase(Locale.ROOT);
    return p.contains("gade") || p.contains("build/classes/java/main") || p.contains("build/resources/main");
  }

  private boolean hasProcessRootLoaderOnClasspath(List<String> classpathEntries) {
    for (String entry : classpathEntries) {
      if (entry == null || entry.isBlank()) {
        continue;
      }
      File file = new File(entry);
      if (file.isFile()) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.startsWith("gade-runner-boot") && name.endsWith(".jar")) {
          return true;
        }
      } else if (file.isDirectory()) {
        File cls = new File(file, "se/alipsa/gade/runner/ProcessRootLoader.class");
        if (cls.exists()) {
          return true;
        }
      } else if (isRunnerPath(entry)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void close() {
    stop();
  }
}
