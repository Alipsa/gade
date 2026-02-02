package se.alipsa.gade.runtime;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.runner.GadeRunnerMain;

import java.io.*;
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
 * script evaluation via JSON-RPC protocol over TCP sockets.
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
 * @threadsafe
 */
public class RuntimeProcessRunner implements Closeable {

  private static final Logger log = LogManager.getLogger(RuntimeProcessRunner.class);
  private static final int STDERR_BUFFER_SIZE = 50;
  private static final int MAX_CONNECT_RETRIES = 50;
  private static final long CONNECT_RETRY_SLEEP_MS = 50;
  private static final int CONNECT_HANDSHAKE_TIMEOUT_MS = 2000;
  private static final long STARTUP_EXIT_CHECK_TIMEOUT_MS = 200;

  private final RuntimeConfig runtime;
  private final List<String> classPathEntries;
  private final ConsoleTextArea console;
  // Keep the socket open across multiple JSON messages (one per line). The runner shares the socket streams
  // between threads; auto-closing would accidentally terminate the connection during mapper reads/writes.
  private final ObjectMapper mapper = new ObjectMapper()
      .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
      .configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);

  private Process process;
  private Socket socket;
  private BufferedWriter socketWriter;
  private BufferedReader socketReader;
  private int runnerPort;
  private ExecutorService readerService;
  private final Map<String, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();
  private final LinkedBlockingDeque<String> stderrBuffer = new LinkedBlockingDeque<>(STDERR_BUFFER_SIZE);
  private final Object procLock = new Object();

  public RuntimeProcessRunner(RuntimeConfig runtime, List<String> classPathEntries, ConsoleTextArea console) {
    this.runtime = runtime;
    this.classPathEntries = classPathEntries;
    this.console = console;
    if (log.isDebugEnabled()) {
      log.debug("Runner classpath entries ({}): {}", classPathEntries.size(), classPathEntries);
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
      cmd.add("-cp");
      cmd.add(String.join(File.pathSeparator, cpOrdered));
      cmd.add(GadeRunnerMain.class.getName());
      // Use a pre-selected loopback port for the runner socket
      cmd.add(String.valueOf(runnerPort));
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(false);
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

  public CompletableFuture<String> eval(String script, Map<String, Object> bindings) throws IOException {
    ensureStarted();
    String id = UUID.randomUUID().toString();
    Map<String, Object> payload = new HashMap<>();
    payload.put("cmd", "eval");
    payload.put("id", id);
    payload.put("script", script);
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
          mapper.writeValue(socketWriter, payload);
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
          Map<String, Object> msg = mapper.readValue(line, Map.class);
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
              msg = mapper.readValue(line, Map.class);
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
          Map<String, Object> helloMsg = mapper.readValue(helloLine, Map.class);
          String version = (String) helloMsg.get("protocolVersion");
          if (!ProtocolVersion.isCompatible(version)) {
            String errorMsg = "Incompatible protocol version: " + ProtocolVersion.getCompatibilityMessage(version);
            log.error("Runner {} - {}", runtime.getName(), errorMsg);
            throw new IOException(errorMsg);
          }

          log.info("Received handshake from runner {}: {} - {}",
              runtime.getName(), helloLine, ProtocolVersion.getCompatibilityMessage(version));
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

  @Override
  public void close() {
    stop();
  }
}
