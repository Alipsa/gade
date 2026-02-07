package se.alipsa.gade.runner;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import se.alipsa.gade.runtime.ProtocolXml;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Groovy-dependent eval engine for the runner subprocess.
 * <p>
 * For Gradle/Maven runtimes, this class is loaded from a {@link GroovyClassLoader}
 * that has project dependencies (via {@code addURL()}) and Groovy jars in its
 * parent bootstrap classloader. For GADE/Custom runtimes, it is loaded from the
 * system classloader which already has Groovy on {@code -cp}.
 * <p>
 * Entry point: {@link #run(BufferedReader, BufferedWriter)} — creates a
 * {@link GroovyClassLoader}, {@link GroovyShell}, and enters the main read loop
 * handling eval, bindings, interrupt, setWorkingDir, shutdown, gui_response, gui_error.
 */
public class GadeRunnerEngine {

  private static final OutputStream ROOT_ERR = new FileOutputStream(FileDescriptor.err);
  private static final boolean VERBOSE = Boolean.getBoolean("gade.runner.verbose");
  public static final String GUI_INTERACTION_KEYS = "__gadeGuiInteractionKeys";
  private static final AtomicReference<Thread> currentEvalThread = new AtomicReference<>();

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
   * @param projectDepPaths project dependency paths to add to the GroovyClassLoader
   *                        (empty for GADE/Custom runtimes)
   */
  public static void run(BufferedReader reader, BufferedWriter writer, String[] projectDepPaths) {
    Binding binding;
    GroovyShell shell;
    try {
      GroovyClassLoader gcl = new GroovyClassLoader(Thread.currentThread().getContextClassLoader());
      // Add project deps to the shell's GroovyClassLoader (like @Grab does)
      for (String path : projectDepPaths) {
        try {
          gcl.addURL(new java.io.File(path).toURI().toURL());
        } catch (Exception e) {
          emitRaw("skipping bad project dep: " + path + " (" + e + ")");
        }
      }
      binding = new Binding();
      shell = new GroovyShell(gcl, binding);
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
              case "eval" -> handleEval(binding, shell, id, (String) cmd.get("script"), bindings, writer, guiPending);
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
}
