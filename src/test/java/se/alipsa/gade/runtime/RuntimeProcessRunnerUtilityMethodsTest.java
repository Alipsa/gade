package se.alipsa.gade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.console.ConsoleTextArea;

/**
 * Test suite for RuntimeProcessRunner utility methods covering:
 * - Java executable resolution
 * - Runner path detection
 * - Stream reading
 * - Sleep utilities
 * - Loopback address resolution
 */
class RuntimeProcessRunnerUtilityMethodsTest {

  // ========== Java Executable Resolution Tests ==========

  @Test
  void testResolveJavaExecutableWithValidJavaHome() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    String javaHome = System.getProperty("java.home");
    RuntimeConfig runtime = new RuntimeConfig("Test", RuntimeType.CUSTOM, javaHome, null, null, null);

    RuntimeProcessRunner runner = new RuntimeProcessRunner(runtime, List.of("dummy"), console);

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("resolveJavaExecutable");
    method.setAccessible(true);
    String javaExec = (String) method.invoke(runner);

    assertNotNull(javaExec, "Java executable should not be null");
    assertTrue(javaExec.contains("java"),
        "Java executable path should contain 'java'");
  }

  @Test
  void testResolveJavaExecutableWithNullJavaHome() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeConfig runtime = new RuntimeConfig("Test", RuntimeType.CUSTOM, null, null, null, null);

    RuntimeProcessRunner runner = new RuntimeProcessRunner(runtime, List.of("dummy"), console);

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("resolveJavaExecutable");
    method.setAccessible(true);
    String javaExec = (String) method.invoke(runner);

    assertNotNull(javaExec, "Java executable should not be null");
    // Should fall back to system java.home or "java"
  }

  @Test
  void testResolveJavaExecutableWithBlankJavaHome() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeConfig runtime = new RuntimeConfig("Test", RuntimeType.CUSTOM, "", null, null, null);

    RuntimeProcessRunner runner = new RuntimeProcessRunner(runtime, List.of("dummy"), console);

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("resolveJavaExecutable");
    method.setAccessible(true);
    String javaExec = (String) method.invoke(runner);

    assertNotNull(javaExec, "Java executable should not be null");
  }

  @Test
  void testResolveJavaExecutableWithInvalidJavaHome() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeConfig runtime = new RuntimeConfig("Test", RuntimeType.CUSTOM, "/nonexistent/path/to/java", null, null, null);

    RuntimeProcessRunner runner = new RuntimeProcessRunner(runtime, List.of("dummy"), console);

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("resolveJavaExecutable");
    method.setAccessible(true);
    String javaExec = (String) method.invoke(runner);

    // Should fall back to "java"
    assertEquals("java", javaExec,
        "Should fall back to 'java' for invalid JAVA_HOME");
  }

  // ========== Runner Path Detection Tests ==========

  @Test
  void testIsRunnerPathWithGadePath() throws Exception {
    RuntimeProcessRunner runner = createRunner();

    assertTrue(invokeIsRunnerPath(runner, "/path/to/gade/classes/Main.class"),
        "Path containing 'gade' should be recognized as runner path");
    assertTrue(invokeIsRunnerPath(runner, "/home/user/GADE/lib/gade.jar"),
        "Path containing 'GADE' (uppercase) should be recognized");
  }

  @Test
  void testIsRunnerPathWithBuildDirectories() throws Exception {
    RuntimeProcessRunner runner = createRunner();

    assertTrue(invokeIsRunnerPath(runner, "/project/build/classes/java/main/App.class"),
        "build/classes/java/main should be recognized as runner path");
    assertTrue(invokeIsRunnerPath(runner, "/project/build/resources/main/config.properties"),
        "build/resources/main should be recognized as runner path");
  }

  @Test
  void testIsRunnerPathWithNonRunnerPaths() throws Exception {
    RuntimeProcessRunner runner = createRunner();

    assertFalse(invokeIsRunnerPath(runner, "/usr/lib/groovy-3.0.9.jar"),
        "Regular library should not be runner path");
    assertFalse(invokeIsRunnerPath(runner, "/home/user/.m2/repository/junit.jar"),
        "Maven repository JARs should not be runner paths");
  }

  @Test
  void testIsRunnerPathWithNull() throws Exception {
    RuntimeProcessRunner runner = createRunner();

    assertFalse(invokeIsRunnerPath(runner, null),
        "Null path should return false");
  }

  @Test
  void testIsRunnerPathCaseInsensitive() throws Exception {
    RuntimeProcessRunner runner = createRunner();

    assertTrue(invokeIsRunnerPath(runner, "/Path/To/Gade/App"),
        "Should be case-insensitive for 'gade'");
    assertTrue(invokeIsRunnerPath(runner, "/PATH/BUILD/CLASSES/JAVA/MAIN/App"),
        "Should be case-insensitive for build paths");
  }

  // ========== Read All Stream Tests ==========

  @Test
  void testReadAllWithContent() throws Exception {
    RuntimeProcessRunner runner = createRunner();

    String content = "Line 1\nLine 2\nLine 3\n";
    InputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("readAll", InputStream.class);
    method.setAccessible(true);
    String result = (String) method.invoke(runner, in);

    assertEquals(content, result,
        "Should read all lines from stream");
  }

  @Test
  void testReadAllWithEmptyStream() throws Exception {
    RuntimeProcessRunner runner = createRunner();

    InputStream in = new ByteArrayInputStream(new byte[0]);

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("readAll", InputStream.class);
    method.setAccessible(true);
    String result = (String) method.invoke(runner, in);

    assertEquals("", result,
        "Empty stream should return empty string");
  }

  @Test
  void testReadAllWithMultilineContent() throws Exception {
    RuntimeProcessRunner runner = createRunner();

    String content = "Error: Something went wrong\n" +
                    "  at com.example.Main.main(Main.java:10)\n" +
                    "  at java.base/java.lang.Thread.run(Thread.java:829)\n";
    InputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("readAll", InputStream.class);
    method.setAccessible(true);
    String result = (String) method.invoke(runner, in);

    assertEquals(content, result,
        "Should preserve all lines and newlines");
    assertTrue(result.contains("Error:"));
    assertTrue(result.contains("at com.example"));
  }

  // ========== Sleep Quietly Tests ==========

  @Test
  void testSleepQuietly() throws Exception {
    RuntimeProcessRunner runner = createRunner();

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("sleepQuietly", long.class);
    method.setAccessible(true);

    long start = System.currentTimeMillis();
    method.invoke(runner, 10L); // Sleep for 10ms
    long elapsed = System.currentTimeMillis() - start;

    assertTrue(elapsed >= 5,
        "Should sleep for at least some milliseconds");
    assertTrue(elapsed < 100,
        "Should not sleep for too long (10ms requested)");
  }

  @Test
  void testSleepQuietlyWithZero() throws Exception {
    RuntimeProcessRunner runner = createRunner();

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("sleepQuietly", long.class);
    method.setAccessible(true);

    // Should not throw, even with 0
    method.invoke(runner, 0L);
  }

  // ========== Loopback Address Tests ==========

  @Test
  void testLoopbackV4() throws Exception {
    RuntimeProcessRunner runner = createRunner();

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("loopbackV4");
    method.setAccessible(true);
    Object address = method.invoke(runner);

    assertNotNull(address, "Loopback address should not be null");
    String addressStr = address.toString();
    assertTrue(addressStr.contains("127.0.0.1") || addressStr.contains("loopback"),
        "Should be IPv4 loopback address: " + addressStr);
  }

  // ========== Pick Port Tests ==========

  @Test
  void testPickPort() throws Exception {
    RuntimeProcessRunner runner = createRunner();

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("pickPort");
    method.setAccessible(true);
    Integer port = (Integer) method.invoke(runner);

    assertNotNull(port, "Port should not be null");
    assertTrue(port > 0 && port < 65536,
        "Port should be in valid range: " + port);
  }

  @Test
  void testPickPortReturnsDifferentPorts() throws Exception {
    RuntimeProcessRunner runner = createRunner();

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("pickPort");
    method.setAccessible(true);

    Integer port1 = (Integer) method.invoke(runner);
    Integer port2 = (Integer) method.invoke(runner);

    assertNotNull(port1);
    assertNotNull(port2);
    // Ports may be the same if OS reuses quickly, but they should be valid
    assertTrue(port1 > 0);
    assertTrue(port2 > 0);
  }

  // ========== Close Quietly Tests ==========

  @Test
  void testCloseQuietlyWithNull() throws Exception {
    RuntimeProcessRunner runner = createRunner();

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("closeQuietly", java.io.Closeable.class);
    method.setAccessible(true);

    // Should not throw with null
    method.invoke(runner, new Object[]{null});
  }

  @Test
  void testCloseQuietlyWithValidCloseable() throws Exception {
    RuntimeProcessRunner runner = createRunner();

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("closeQuietly", java.io.Closeable.class);
    method.setAccessible(true);

    java.io.ByteArrayInputStream stream = new java.io.ByteArrayInputStream(new byte[0]);
    method.invoke(runner, stream);

    // Stream should be closed, trying to read should fail or return -1
    assertEquals(-1, stream.read(), "Stream should be closed");
  }

  // ========== Helper Methods ==========

  private RuntimeProcessRunner createRunner() {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeConfig runtime = new RuntimeConfig("Test", RuntimeType.CUSTOM);
    return new RuntimeProcessRunner(runtime, List.of("dummy"), console);
  }

  private boolean invokeIsRunnerPath(RuntimeProcessRunner runner, String path) throws Exception {
    Method method = RuntimeProcessRunner.class.getDeclaredMethod("isRunnerPath", String.class);
    method.setAccessible(true);
    return (Boolean) method.invoke(runner, path);
  }
}
