package se.alipsa.gade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import se.alipsa.gade.console.ConsoleTextArea;

class RuntimeProcessRunnerTest {

  @Test
  void startFailsWithoutClasspathEntries() {
    ConsoleTextArea console = Mockito.mock(ConsoleTextArea.class);
    RuntimeConfig runtime = new RuntimeConfig("TestRuntime", RuntimeType.CUSTOM);
    RuntimeProcessRunner runner = new RuntimeProcessRunner(runtime, List.of(), console);

    IOException ex = assertThrows(IOException.class, runner::start);
    assertTrue(ex.getMessage().contains("Classpath"), "Expected classpath warning in exception");
    verify(console).appendWarningFx(Mockito.contains("no classpath entries"));
  }

  @Test
  void handleMessageCompletesPendingResult() throws Exception {
    ConsoleTextArea console = Mockito.mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = new RuntimeProcessRunner(new RuntimeConfig("Test", RuntimeType.CUSTOM),
        List.of("dummy"), console);

    Map<String, CompletableFuture<Map<String, Object>>> pending = pending(runner);
    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
    String id = "abc-123";
    pending.put(id, future);

    Map<String, Object> msg = new HashMap<>();
    msg.put("type", "result");
    msg.put("id", id);
    msg.put("result", "ok");

    invokeHandleMessage(runner, msg);

    assertTrue(future.isDone(), "Expected result future to complete");
    assertEquals("ok", future.get().get("result"));
    assertTrue(pending.isEmpty(), "Pending map should be empty after completion");
  }

  @Test
  void handleMessageCompletesExceptionallyOnError() throws Exception {
    ConsoleTextArea console = Mockito.mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = new RuntimeProcessRunner(new RuntimeConfig("Test", RuntimeType.CUSTOM),
        List.of("dummy"), console);

    Map<String, CompletableFuture<Map<String, Object>>> pending = pending(runner);
    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
    String id = "err-1";
    pending.put(id, future);

    Map<String, Object> msg = new HashMap<>();
    msg.put("type", "error");
    msg.put("id", id);
    msg.put("error", "Boom");
    msg.put("stacktrace", "trace");

    invokeHandleMessage(runner, msg);

    assertTrue(future.isCompletedExceptionally(), "Expected error future to complete exceptionally");
    assertThrows(CompletionException.class, future::join);
    assertTrue(pending.isEmpty(), "Pending map should be empty after error");

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(console).appendWarningFx(captor.capture());
    String warning = captor.getValue();
    assertTrue(warning.contains("Boom"), "Expected warning to contain error message");
    assertTrue(warning.contains("trace"), "Expected warning to contain stacktrace");
  }

  @Test
  void handleMessageWritesOutputAndError() throws Exception {
    ConsoleTextArea console = Mockito.mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = new RuntimeProcessRunner(new RuntimeConfig("Test", RuntimeType.CUSTOM),
        List.of("dummy"), console);

    invokeHandleMessage(runner, Map.of("type", "out", "text", "hello"));
    invokeHandleMessage(runner, Map.of("type", "err", "text", "oops"));

    verify(console).appendFx("hello", false);
    verify(console).appendWarningFx("oops");
  }

  @Test
  void handleMessageWarnsOnIncompatibleProtocol() throws Exception {
    ConsoleTextArea console = Mockito.mock(ConsoleTextArea.class);
    RuntimeProcessRunner runner = new RuntimeProcessRunner(new RuntimeConfig("Test", RuntimeType.CUSTOM),
        List.of("dummy"), console);

    invokeHandleMessage(runner, Map.of("type", "hello", "protocolVersion", "2.0"));

    verify(console).appendWarningFx("Runner protocol version incompatible: 2.0");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, CompletableFuture<Map<String, Object>>> pending(RuntimeProcessRunner runner) throws Exception {
    Field field = RuntimeProcessRunner.class.getDeclaredField("pending");
    field.setAccessible(true);
    Map<String, CompletableFuture<Map<String, Object>>> pending =
        (Map<String, CompletableFuture<Map<String, Object>>>) field.get(runner);
    assertNotNull(pending, "Pending map should be available");
    return pending;
  }

  private static void invokeHandleMessage(RuntimeProcessRunner runner, Map<String, Object> msg) throws Exception {
    Method method = RuntimeProcessRunner.class.getDeclaredMethod("handleMessage", Map.class);
    method.setAccessible(true);
    method.invoke(runner, msg);
  }
}
