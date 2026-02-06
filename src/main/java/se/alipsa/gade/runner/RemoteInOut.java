package se.alipsa.gade.runner;

import groovy.lang.GroovyObjectSupport;
import se.alipsa.gade.runtime.ProtocolXml;

import java.io.BufferedWriter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Proxy implementation that forwards all GUI method calls over the socket protocol
 * to the real InOut instance in the main Gade process.
 * <p>
 * This class uses GroovyObjectSupport to avoid dependencies on JavaFX or gi-fx libraries
 * which are not available in the runner process classpath. All method calls are intercepted
 * via invokeMethod() and forwarded over the socket.
 * <p>
 * This enables external runtimes (Gradle, Maven, Custom) to use the 'io' object
 * for GUI interactions by serializing arguments, sending gui_request messages,
 * and blocking for responses.
 */
public class RemoteInOut extends GroovyObjectSupport {

  private static final long TIMEOUT_MS = 60000; // 60 seconds

  private final BufferedWriter writer;
  private final ConcurrentHashMap<String, CompletableFuture<Object>> pending;

  public RemoteInOut(BufferedWriter writer,
                     ConcurrentHashMap<String, CompletableFuture<Object>> pending) {
    this.writer = writer;
    this.pending = pending;
  }

  /**
   * Intercept all method calls and forward them via socket to the real InOut.
   * This is called by Groovy when any method is invoked on this object.
   */
  @Override
  public Object invokeMethod(String name, Object args) {
    // Convert args to array
    Object[] argsArray;
    if (args == null) {
      argsArray = new Object[0];
    } else if (args instanceof Object[]) {
      argsArray = (Object[]) args;
    } else {
      argsArray = new Object[]{args};
    }

    // Send GUI request and wait for response
    return sendGuiRequest(name, argsArray);
  }

  /**
   * Send a GUI request over the socket and wait for response.
   *
   * @param method The method name to invoke on the remote InOut
   * @param args   The arguments to pass (will be serialized)
   * @return The result from the remote method (deserialized)
   */
  private Object sendGuiRequest(String method, Object... args) {
    try {
      String id = UUID.randomUUID().toString();

      // Serialize arguments
      List<Object> serializedArgs = new ArrayList<>();
      for (Object arg : args) {
        serializedArgs.add(ArgumentSerializer.serialize(arg));
      }

      // Create request
      Map<String, Object> request = new HashMap<>();
      request.put("type", "gui_request");
      request.put("id", id);
      request.put("method", method);
      request.put("args", serializedArgs);

      // Register pending future
      CompletableFuture<Object> future = new CompletableFuture<>();
      pending.put(id, future);

      // Send request
      synchronized (writer) {
        writer.write(ProtocolXml.toXml(request));
        writer.write("\n");
        writer.flush();
      }

      // Wait for response
      Object result = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
      return ArgumentSerializer.deserialize(result);

    } catch (TimeoutException e) {
      throw new RuntimeException("GUI operation timed out after " + TIMEOUT_MS + "ms: " + method, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("GUI operation interrupted: " + method, e);
    } catch (ExecutionException e) {
      // The future was completed exceptionally (gui_error received)
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      throw new RuntimeException("GUI operation failed: " + method, cause);
    } catch (Exception e) {
      throw new RuntimeException("GUI operation failed: " + method, e);
    }
  }

  @Override
  public String toString() {
    return "Remote GUI interaction proxy (forwards to main Gade process)";
  }
}
