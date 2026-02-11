package se.alipsa.gade.runner;

import se.alipsa.gade.runtime.ProtocolVersion;
import se.alipsa.gade.runtime.ProtocolXml;

import java.io.*;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bootstrap entry point for the runner subprocess.
 * <p>
 * This class has NO Groovy imports. It:
 * <ol>
 *   <li>Creates a {@link ServerSocket} and accepts a connection from the main Gade process</li>
 *   <li>Redirects {@code System.out}/{@code System.err} to {@link EventOutputStream}</li>
 *   <li>Sends a {@code hello} handshake</li>
 *   <li>Waits for an {@code addClasspath} command with Groovy and project dependency entries</li>
 *   <li>Adds Groovy bootstrap jars to {@link ProcessRootLoader} (Gradle/Maven runtimes)</li>
 *   <li>For GADE/Custom: Groovy is already on the system classpath, so the engine is loaded
 *       directly from the system classloader</li>
 *   <li>Loads {@link GadeRunnerEngine} and invokes its
 *       {@code run(BufferedReader, BufferedWriter, String, String[], String[], String[])} method</li>
 * </ol>
 */
public class GadeRunnerMain {

  private static final OutputStream ROOT_OUT = new FileOutputStream(FileDescriptor.out);
  private static final OutputStream ROOT_ERR = new FileOutputStream(FileDescriptor.err);
  private static final boolean VERBOSE = Boolean.getBoolean("gade.runner.verbose");

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

          // Send hello handshake
          emit(Map.of("type", "hello", "port", actualPort, "protocolVersion", ProtocolVersion.CURRENT), writer);

          // Wait for addClasspath command (blocking read before engine starts)
          Map<String, Object> cpCmd = waitForClasspath(reader, writer);
          String runtimeType = cpCmd.get("runtimeType") == null ? "GADE" : String.valueOf(cpCmd.get("runtimeType"));
          List<String> groovyEntries = toStringList(cpCmd.get("groovyEntries"));
          List<String> mainEntries = toStringList(cpCmd.get("mainEntries"));
          List<String> testEntries = toStringList(cpCmd.get("testEntries"));
          List<String> guiInteractionKeys = toStringList(cpCmd.get("guiInteractionKeys"));
          emitRaw("received " + groovyEntries.size() + " groovy entries, "
              + mainEntries.size() + " main entries and "
              + testEntries.size() + " test entries for runtime " + runtimeType);

          // Build classloader for the engine.
          // Project deps are NOT loaded here — they are passed to GadeRunnerEngine
          // which builds main/test GroovyClassLoaders and adds dependencies via addURL().
          ClassLoader engineCL;

          if (!groovyEntries.isEmpty()) {
            // Add Groovy/Ivy jars + engine JAR to ProcessRootLoader.
            // The engine JAR is already in groovyEntries (added by GroovyRuntimeManager).
            // Boot JAR classes (GadeRunnerMain, ProcessRootLoader, ProtocolXml) stay
            // on the App classloader via -cp; engine classes + Groovy are found here.
            ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
            if (systemClassLoader instanceof ProcessRootLoader processRootLoader) {
              for (URL u : toUrls(groovyEntries)) {
                processRootLoader.addURL(u);
              }
              engineCL = processRootLoader;
            } else {
              // Fallback for environments where custom system classloader is unavailable.
              List<URL> bootstrapUrls = new ArrayList<>();
              for (URL u : toUrls(groovyEntries)) {
                bootstrapUrls.add(u);
              }
              engineCL = new URLClassLoader(bootstrapUrls.toArray(new URL[0]),
                  ClassLoader.getPlatformClassLoader());
            }
          } else {
            // GADE/Custom mode: Groovy is already on the system classpath
            engineCL = ClassLoader.getSystemClassLoader();
          }

          Thread.currentThread().setContextClassLoader(engineCL);

          // Send classpathAdded ack
          emit(Map.of("type", "classpathAdded"), writer);

          // Load and invoke GadeRunnerEngine via reflection (no Groovy import needed here).
          // Main/test dependency paths are passed so the engine can create
          // a classloader hierarchy and route each eval by testContext.
          Class<?> engineClass = engineCL.loadClass("se.alipsa.gade.runner.GadeRunnerEngine");
          Method runMethod = engineClass.getMethod("run",
              BufferedReader.class, BufferedWriter.class, String.class,
              String[].class, String[].class, String[].class);
          runMethod.invoke(null, reader, writer, runtimeType,
              mainEntries.toArray(new String[0]), testEntries.toArray(new String[0]),
              guiInteractionKeys.toArray(new String[0]));

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

  /**
   * Reads lines from the socket until an {@code addClasspath} command is received.
   * Returns the parsed command map containing classpath and runtime metadata.
   * Any non-addClasspath messages received during this phase are logged and skipped.
   */
  private static Map<String, Object> waitForClasspath(BufferedReader reader, BufferedWriter writer) throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      if (line.isBlank()) {
        continue;
      }
      emitRaw("bootstrap received: " + line);
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> cmd = ProtocolXml.fromXml(line);
        String action = (String) cmd.get("cmd");
        if ("addClasspath".equals(action)) {
          return cmd;
        } else {
          emitRaw("ignoring non-addClasspath command during bootstrap: " + action);
        }
      } catch (Exception parse) {
        emitRaw("bootstrap parse failed: " + parse);
      }
    }
    throw new IOException("Socket closed before addClasspath command received");
  }

  /**
   * Converts a list-like object from the protocol into a {@code List<String>}.
   */
  private static List<String> toStringList(Object obj) {
    if (!(obj instanceof List<?> list)) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (Object item : list) {
      if (item != null) {
        result.add(String.valueOf(item));
      }
    }
    return result;
  }

  /**
   * Converts a list of file paths to an array of URLs.
   */
  private static URL[] toUrls(List<String> paths) {
    List<URL> urls = new ArrayList<>();
    for (String path : paths) {
      try {
        urls.add(new File(path).toURI().toURL());
      } catch (Exception e) {
        emitRaw("skipping bad classpath entry: " + path + " (" + e + ")");
      }
    }
    return urls.toArray(new URL[0]);
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

  private static void emitError(String id, String msg, String stackTrace, BufferedWriter writer) {
    java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
    payload.put("type", "error");
    payload.put("id", id);
    payload.put("error", msg);
    if (stackTrace != null) {
      payload.put("stacktrace", stackTrace);
    }
    emit(payload, writer);
  }

  private static void emit(Map<String, ?> payload, BufferedWriter writer) {
    String xml = ProtocolXml.toXml(payload);
    if (writer == null) {
      try {
        ROOT_OUT.write((xml + "\n").getBytes(StandardCharsets.UTF_8));
        ROOT_OUT.flush();
      } catch (IOException e) {
        emitRaw("emit to ROOT_OUT failed: " + e);
      }
      return;
    }
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

  private static String getStackTrace(Throwable t) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }

  /**
   * OutputStream that turns writes into XML events on the socket.
   * No Groovy deps — writes ProtocolXml directly.
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
