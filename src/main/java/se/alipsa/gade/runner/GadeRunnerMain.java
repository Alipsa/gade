package se.alipsa.gade.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import se.alipsa.gade.runtime.ProtocolVersion;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Small out-of-process runner that keeps a Groovy engine alive and communicates via JSON over stdin/stdout.
 * Stdout/stderr from scripts are forwarded as JSON events to the parent process.
 */
public class GadeRunnerMain {

  private static final OutputStream ROOT_OUT = new FileOutputStream(FileDescriptor.out);
  private static final OutputStream ROOT_ERR = new FileOutputStream(FileDescriptor.err);
  private static final boolean VERBOSE = Boolean.getBoolean("gade.runner.verbose");
  public static final String GUI_INTERACTION_KEYS = "__gadeGuiInteractionKeys";
  private static final ObjectMapper mapper = ProtocolMapper.create();

  private static final AtomicReference<Thread> currentEvalThread = new AtomicReference<>();

  private GadeRunnerMain() {}

  public static void main(String[] args) throws Exception {
    int port = args.length > 0 ? Integer.parseInt(args[0]) : 0;
    try (ServerSocket server = new ServerSocket(port, 1, loopbackV4())) {
      int actualPort = server.getLocalPort();
      emit(Map.of("type", "ready", "port", actualPort), null);

      try (Socket socket = server.accept();
           BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
           BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

        emitRaw("runner accepted connection on port " + actualPort);
        emitRaw("runner entering read loop");
        emitRaw("runner local=" + socket.getLocalSocketAddress() + " remote=" + socket.getRemoteSocketAddress());
        PrintStream previousOut = System.out;
        PrintStream previousErr = System.err;
        PrintStream outStream = null;
        PrintStream errStream = null;
        try {
          outStream = new PrintStream(new EventOutputStream("out", writer), true, StandardCharsets.UTF_8);
          errStream = new PrintStream(new EventOutputStream("err", writer), true, StandardCharsets.UTF_8);
          System.setOut(outStream);
          System.setErr(errStream);

          Binding binding;
          GroovyShell shell;
          try {
            binding = new Binding();
            shell = new GroovyShell(binding);
          } catch (Throwable t) {
            emitRaw("shell init failed: " + t);
            emitRaw(getStackTrace(t));
            emitError("init", "Shell init failed: " + t.getMessage(), getStackTrace(t), writer);
            return;
          }

          emit(Map.of("type", "hello", "port", actualPort, "protocolVersion", ProtocolVersion.CURRENT), writer);

          // Track pending GUI requests for async response handling
          ConcurrentHashMap<String, CompletableFuture<Object>> guiPending = new ConcurrentHashMap<>();

          try {
            String line;
            while ((line = reader.readLine()) != null) {
              if (line.isBlank()) {
                continue;
              }
              emitRaw("runner received: " + line);
              try {
                Map<String, Object> cmd = mapper.readValue(line, Map.class);
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

                // Handle commands from Gade (eval, bindings, interrupt, shutdown)
                try {
                  switch (action) {
                    case "eval" -> handleEval(binding, shell, id, (String) cmd.get("script"), (Map<String, Object>) cmd.get("bindings"), writer, guiPending);
                    case "bindings" -> handleBindings(binding, id, writer);
                    case "interrupt" -> handleInterrupt(id, writer);
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
            emitRaw("runner received EOF, shutting down");
          } catch (IOException loopEx) {
            emitRaw("runner read loop exception: " + loopEx);
            emitRaw(getStackTrace(loopEx));
          }
        } finally {
          System.setOut(previousOut);
          System.setErr(previousErr);
          if (outStream != null) {
            outStream.close();
          }
          if (errStream != null) {
            errStream.close();
          }
        }
      }
    } catch (Throwable t) {
      emitRaw("fatal runner failure: " + t);
      emitRaw(getStackTrace(t));
      emitError("init", "Runner failed: " + t.getMessage(), getStackTrace(t), null);
    }
  }

  private static InetAddress loopbackV4() throws IOException {
    try {
      return InetAddress.getByName("127.0.0.1");
    } catch (IOException e) {
      emitRaw("Failed to resolve IPv4 loopback, falling back to default: " + e.getMessage());
      return InetAddress.getLoopbackAddress();
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
      // Last resort - if stderr write fails, nowhere left to report it
    }
  }

  private static void handleEval(Binding binding, GroovyShell shell, String id, String script, Map<String, Object> bindings, BufferedWriter writer, ConcurrentHashMap<String, CompletableFuture<Object>> guiPending) {
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
      try {
        if (bindings != null) {
          ensureRemoteGuiInteractions(binding, bindings.get(GUI_INTERACTION_KEYS), writer, guiPending);
          bindings.forEach((k, v) -> {
            if (!GUI_INTERACTION_KEYS.equals(k)) {
              binding.setVariable(k, v);
            }
          });
        }
        Object result = shell.evaluate(script);
        emit(Map.of("type", "result", "id", id, "result", result == null ? "null" : String.valueOf(result)), writer);
      } catch (Exception e) {
        emitError(id, e.getMessage(), getStackTrace(e), writer);
      } finally {
        currentEvalThread.set(null);
      }
    }, "gade-runner-eval");
    t.start();
  }

  private static void ensureRemoteGuiInteractions(
      Binding binding,
      Object keys,
      BufferedWriter writer,
      ConcurrentHashMap<String, CompletableFuture<Object>> guiPending) {
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
      // Inject RemoteInOut proxy instead of UnsupportedGuiInteraction
      binding.setVariable(name, new RemoteInOut(writer, guiPending));
    }
  }

  private static void handleBindings(Binding binding, String id, BufferedWriter writer) {
    Map<String, Object> variables = binding.getVariables();
    Map<String, String> serialized = new HashMap<>();
    variables.forEach((k, v) -> serialized.put(String.valueOf(k), v == null ? "null" : v.toString()));
    emit(Map.of("type", "bindings", "id", id, "bindings", serialized), writer);
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

  private static void handleGuiResponse(Map<String, Object> cmd, ConcurrentHashMap<String, CompletableFuture<Object>> guiPending) {
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

  private static void handleGuiError(Map<String, Object> cmd, ConcurrentHashMap<String, CompletableFuture<Object>> guiPending) {
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

  private static void emit(Map<String, ?> payload, BufferedWriter writer) {
    if (writer == null) {
      try {
        mapper.writeValue(ROOT_OUT, payload);
        ROOT_OUT.write('\n');
        ROOT_OUT.flush();
      } catch (IOException e) {
        emitRaw("emit to ROOT_OUT failed: " + e);
      }
      return;
    }
    // Multiple threads (eval thread + stdout/stderr EventOutputStream) share the same writer.
    // Synchronize on the writer to avoid interleaved JSON messages corrupting the stream.
    synchronized (writer) {
      try {
        mapper.writeValue(writer, payload);
        writer.write("\n");
        writer.flush();
      } catch (IOException e) {
        emitRaw("emit failed: " + e);
      }
    }
  }

  private static String getStackTrace(Throwable t) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }

  /**
   * OutputStream that turns writes into JSON events on the root stdout/stderr to avoid recursion.
   */
  static class EventOutputStream extends OutputStream {
    private final String type;
    private final StringBuilder buffer = new StringBuilder();
    private final BufferedWriter writer;

    EventOutputStream(String type, BufferedWriter writer) {
      this.type = type;
      this.writer = writer;
    }

    @Override
    public void write(int b) {
      buffer.append((char) b);
      if (b == '\n') {
        flushBuffer();
      }
    }

    @Override
    public void flush() {
      flushBuffer();
    }

    private void flushBuffer() {
      if (buffer.length() == 0) {
        return;
      }
      String text = buffer.toString();
      buffer.setLength(0);
      Map<String, Object> payload = Map.of("type", type, "text", text);
      emit(payload, writer);
    }
  }
}
