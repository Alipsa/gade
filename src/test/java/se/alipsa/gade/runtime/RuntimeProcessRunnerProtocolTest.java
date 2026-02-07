package se.alipsa.gade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.console.ConsoleTextArea;

/**
 * Test suite for RuntimeProcessRunner protocol handling covering:
 * - JSON protocol message handling
 * - Bindings fetching
 * - Interrupt command
 * - Message completion
 * - Protocol edge cases
 */
class RuntimeProcessRunnerProtocolTest {

  // ========== Handle Message Edge Cases ==========

  @Test
  void testHandleMessageWithNullType() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    Map<String, Object> msg = new HashMap<>();
    msg.put("id", "test-1");
    // No "type" field

    invokeHandleMessage(runner, msg);

    // Should handle gracefully without throwing
  }

  @Test
  void testHandleMessageWithUnknownType() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    Map<String, Object> msg = Map.of("type", "unknown-type", "id", "test-2");

    invokeHandleMessage(runner, msg);

    // Should handle gracefully (logs debug message)
  }

  @Test
  void testHandleMessageHello() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    // Compatible version
    Map<String, Object> msg = Map.of(
        "type", "hello",
        "protocolVersion", "1.0"
    );

    invokeHandleMessage(runner, msg);

    // Should not warn for compatible version
  }

  @Test
  void testHandleMessageOutWithEmptyText() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    Map<String, Object> msg = Map.of("type", "out", "text", "");

    invokeHandleMessage(runner, msg);

    verify(console).appendFx("", false);
  }

  @Test
  void testHandleMessageErrWithEmptyText() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    Map<String, Object> msg = Map.of("type", "err", "text", "");

    invokeHandleMessage(runner, msg);

    verify(console).appendWarningFx("");
  }

  @Test
  void testHandleMessageOutWithMissingText() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    Map<String, Object> msg = Map.of("type", "out");

    invokeHandleMessage(runner, msg);

    // Should default to empty string
    verify(console).appendFx(anyString(), anyBoolean());
  }

  @Test
  void testHandleMessageBindingsType() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    Map<String, CompletableFuture<Map<String, Object>>> pending = getPending(runner);
    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
    String id = "bindings-123";
    pending.put(id, future);

    Map<String, Object> msg = new HashMap<>();
    msg.put("type", "bindings");
    msg.put("id", id);
    msg.put("bindings", Map.of("x", "10", "y", "hello"));

    invokeHandleMessage(runner, msg);

    assertTrue(future.isDone(), "Bindings future should complete");
    Map<String, Object> result = future.get();
    assertEquals(Map.of("x", "10", "y", "hello"), result.get("bindings"));
  }

  @Test
  void testHandleMessageInterruptedType() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    Map<String, CompletableFuture<Map<String, Object>>> pending = getPending(runner);
    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
    String id = "interrupt-456";
    pending.put(id, future);

    Map<String, Object> msg = Map.of("type", "interrupted", "id", id);

    invokeHandleMessage(runner, msg);

    assertTrue(future.isDone(), "Interrupted future should complete");
  }

  @Test
  void testHandleMessageShutdownType() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    Map<String, CompletableFuture<Map<String, Object>>> pending = getPending(runner);
    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
    String id = "shutdown-789";
    pending.put(id, future);

    Map<String, Object> msg = Map.of("type", "shutdown", "id", id);

    invokeHandleMessage(runner, msg);

    assertTrue(future.isDone(), "Shutdown future should complete");
  }

  // ========== Complete Tests ==========

  @Test
  void testCompleteWithNonexistentId() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("complete", Map.class);
    method.setAccessible(true);

    Map<String, Object> msg = Map.of("type", "result", "id", "nonexistent");

    // Should not throw even if ID doesn't exist
    method.invoke(runner, msg);
  }

  // ========== Complete Exceptionally Tests ==========

  @Test
  void testCompleteExceptionallyWithoutStacktrace() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    Map<String, CompletableFuture<Map<String, Object>>> pending = getPending(runner);
    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
    String id = "error-no-stack";
    pending.put(id, future);

    Map<String, Object> msg = new HashMap<>();
    msg.put("type", "error");
    msg.put("id", id);
    msg.put("error", "Something failed");

    invokeHandleMessage(runner, msg);

    assertTrue(future.isCompletedExceptionally());
    verify(console).appendWarningFx(contains("Something failed"));
  }

  @Test
  void testCompleteExceptionallyWithEmptyError() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    Map<String, CompletableFuture<Map<String, Object>>> pending = getPending(runner);
    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
    String id = "error-empty";
    pending.put(id, future);

    Map<String, Object> msg = new HashMap<>();
    msg.put("type", "error");
    msg.put("id", id);
    msg.put("error", "");

    invokeHandleMessage(runner, msg);

    assertTrue(future.isCompletedExceptionally());
  }

  @Test
  void testCompleteExceptionallyWithNonexistentId() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    Map<String, Object> msg = Map.of(
        "type", "error",
        "id", "does-not-exist",
        "error", "Failed"
    );

    // Should not throw even if future doesn't exist
    invokeHandleMessage(runner, msg);

    verify(console).appendWarningFx(contains("Failed"));
  }

  // ========== Buffer Stderr Line Tests ==========

  @Test
  void testBufferStderrLine() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("bufferStderrLine", String.class);
    method.setAccessible(true);

    // Add some stderr lines
    method.invoke(runner, "Error line 1");
    method.invoke(runner, "Error line 2");
    method.invoke(runner, "Error line 3");

    // Get the buffer and verify
    Field bufferField = RuntimeProcessRunner.class.getDeclaredField("stderrBuffer");
    bufferField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.concurrent.LinkedBlockingDeque<String> buffer =
        (java.util.concurrent.LinkedBlockingDeque<String>) bufferField.get(runner);

    assertNotNull(buffer);
    assertTrue(buffer.size() > 0, "Buffer should contain stderr lines");
  }

  @Test
  void testBufferStderrLineOverflow() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    Method method = RuntimeProcessRunner.class.getDeclaredMethod("bufferStderrLine", String.class);
    method.setAccessible(true);

    // Add more lines than buffer size (STDERR_BUFFER_SIZE = 50)
    for (int i = 0; i < 60; i++) {
      method.invoke(runner, "Error line " + i);
    }

    // Get the buffer and verify it doesn't exceed max size
    Field bufferField = RuntimeProcessRunner.class.getDeclaredField("stderrBuffer");
    bufferField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.concurrent.LinkedBlockingDeque<String> buffer =
        (java.util.concurrent.LinkedBlockingDeque<String>) bufferField.get(runner);

    assertTrue(buffer.size() <= 50,
        "Buffer should not exceed max size: " + buffer.size());
  }

  // ========== Constructor and Initialization Tests ==========

  @Test
  void testConstructorWithEmptyClasspath() {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeConfig runtime = new RuntimeConfig("Test", RuntimeType.CUSTOM);

    RuntimeProcessRunner runner = new RuntimeProcessRunner(runtime, List.of(), console, Map.of());

    assertNotNull(runner, "Should create runner even with empty classpath");
    // Start will fail with empty classpath (tested elsewhere)
  }

  @Test
  void testConstructorWithMultipleClasspathEntries() {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeConfig runtime = new RuntimeConfig("Test", RuntimeType.CUSTOM);
    List<String> cp = List.of("/lib/a.jar", "/lib/b.jar", "/lib/c.jar");

    RuntimeProcessRunner runner = new RuntimeProcessRunner(runtime, cp, console, Map.of());

    assertNotNull(runner);
  }

  // ========== resolveDependency Routing Tests ==========

  @Test
  void testResolveDependencyNotOnFxThread() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    // Build a gui_request message with method=resolveDependency
    Map<String, Object> msg = new HashMap<>();
    msg.put("type", "gui_request");
    msg.put("id", "resolve-test-1");
    msg.put("method", "resolveDependency");
    msg.put("args", List.of("com.h2database:h2:2.1.214"));

    // Invoke handleMessage — should NOT throw even without a running Gade instance.
    // The resolveDependency handler runs on a background thread and will fail
    // gracefully (sends gui_error) since Gade.instance() is null in tests.
    invokeHandleMessage(runner, msg);

    // Give the async handler a moment to execute
    Thread.sleep(200);

    // The key assertion is that we got here without hanging on the FX thread.
    // In a real scenario the FX thread would be free while resolution runs.
  }

  @Test
  void testResolveDependencyWithMissingArgs() throws Exception {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = createRunner(console);

    Map<String, Object> msg = new HashMap<>();
    msg.put("type", "gui_request");
    msg.put("id", "resolve-test-2");
    msg.put("method", "resolveDependency");
    msg.put("args", List.of());

    invokeHandleMessage(runner, msg);

    // Give async handler time to execute
    Thread.sleep(200);

    // Should handle gracefully — sends gui_error for missing dependency string
  }

  // ========== Helper Methods ==========

  private RuntimeProcessRunner createRunner(ConsoleTextArea console) {
    RuntimeConfig runtime = new RuntimeConfig("Test", RuntimeType.CUSTOM);
    return new RuntimeProcessRunner(runtime, List.of("dummy"), console, Map.of());
  }

  @SuppressWarnings("unchecked")
  private Map<String, CompletableFuture<Map<String, Object>>> getPending(RuntimeProcessRunner runner)
      throws Exception {
    Field field = RuntimeProcessRunner.class.getDeclaredField("pending");
    field.setAccessible(true);
    return (Map<String, CompletableFuture<Map<String, Object>>>) field.get(runner);
  }

  private void invokeHandleMessage(RuntimeProcessRunner runner, Map<String, Object> msg)
      throws Exception {
    Method method = RuntimeProcessRunner.class.getDeclaredMethod("handleMessage", Map.class);
    method.setAccessible(true);
    method.invoke(runner, msg);
  }
}
