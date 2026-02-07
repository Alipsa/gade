package se.alipsa.gade.runner;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObjectSupport;
import se.alipsa.gade.runtime.ProtocolXml;
import se.alipsa.groovy.datautil.ConnectionInfo;
import se.alipsa.groovy.datautil.SqlUtil;

import java.io.BufferedWriter;
import java.net.URL;
import java.sql.Connection;
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
  private GroovyClassLoader scriptClassLoader;

  public RemoteInOut(BufferedWriter writer,
                     ConcurrentHashMap<String, CompletableFuture<Object>> pending) {
    this.writer = writer;
    this.pending = pending;
  }

  public void setScriptClassLoader(GroovyClassLoader cl) {
    this.scriptClassLoader = cl;
  }

  /**
   * Intercept all method calls and forward them via socket to the real InOut.
   * This is called by Groovy when any method is invoked on this object.
   */
  @Override
  public Object invokeMethod(String name, Object args) {
    Object[] argsArray = normalizeArgs(args);

    if ("dbConnect".equals(name)) {
      return handleDbConnect(argsArray);
    }

    // Send GUI request and wait for response
    return sendGuiRequest(name, argsArray);
  }

  private static Object[] normalizeArgs(Object args) {
    if (args == null) {
      return new Object[0];
    } else if (args instanceof Object[]) {
      return (Object[]) args;
    } else {
      return new Object[]{args};
    }
  }

  /**
   * Handle dbConnect locally in the subprocess.
   * <p>
   * If the argument is a String, proxy {@code dbConnection(name)} to the main process
   * to retrieve the {@link ConnectionInfo}. If the argument is already a
   * {@link ConnectionInfo}, use it directly. Resolves JDBC driver dependencies
   * and prompts for password if needed, then creates a live {@link Connection}.
   */
  private Object handleDbConnect(Object[] args) {
    if (args.length == 0) {
      throw new IllegalArgumentException("dbConnect requires a connection name or ConnectionInfo argument");
    }

    ConnectionInfo ci;
    Object arg = args[0];
    if (arg instanceof String) {
      // Proxy dbConnection(name) to main process to get ConnectionInfo
      Object result = sendGuiRequest("dbConnection", arg);
      if (!(result instanceof ConnectionInfo)) {
        throw new RuntimeException("dbConnection(\"" + arg + "\") did not return a ConnectionInfo");
      }
      ci = (ConnectionInfo) result;
    } else if (arg instanceof ConnectionInfo) {
      ci = (ConnectionInfo) arg;
    } else {
      throw new IllegalArgumentException("dbConnect requires a String or ConnectionInfo argument, got: "
          + arg.getClass().getName());
    }

    // Resolve JDBC driver dependency if specified
    String dep = ci.getDependency();
    if (dep != null && !dep.isBlank()) {
      resolveDependencyLocally(dep);
    }

    // Prompt for password if needed
    String password = ci.getPassword();
    if ((password == null || password.isBlank()) && !urlContainsLogin(ci.getUrl())) {
      String pwd = (String) sendGuiRequest("promptPassword",
          "Password required", "Enter password to " + ci.getName() + " for " + ci.getUser());
      ci.setPassword(pwd);
    }

    try {
      return SqlUtil.connect(ci);
    } catch (Exception e) {
      throw new RuntimeException("Failed to connect to " + ci.getName() + ": " + e.getMessage(), e);
    }
  }

  /**
   * Proxy resolveDependency to the main process and add returned jar paths
   * to the script classloader.
   */
  private void resolveDependencyLocally(String dependency) {
    Object result = sendGuiRequest("resolveDependency", dependency);
    if (scriptClassLoader != null && result instanceof List<?> jarPaths) {
      for (Object path : jarPaths) {
        try {
          scriptClassLoader.addURL(new java.io.File(String.valueOf(path)).toURI().toURL());
        } catch (Exception e) {
          System.err.println("RemoteInOut: Failed to add resolved jar: " + path + " (" + e + ")");
        }
      }
    }
  }

  /**
   * Check if the JDBC URL already contains embedded login credentials.
   * Copied from ConnectionHandler to avoid dependency on JavaFX classes.
   */
  private static boolean urlContainsLogin(String url) {
    if (url == null) {
      return false;
    }
    String lc = url.toLowerCase();
    return (lc.contains("user") && lc.contains("pass"))
        || (lc.contains("@") && !url.contains("jdbc:oracle"))
        || lc.contains("integratedsecurity=true");
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
