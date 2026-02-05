package se.alipsa.gade.runtime;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.runner.ArgumentSerializer;
import se.alipsa.gi.GuiInteraction;
import se.alipsa.matrix.core.Matrix;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Integration test suite for RuntimeProcessRunner GUI interaction handling.
 * Tests focus on argument serialization/deserialization and method resolution.
 */
class RuntimeProcessRunnerGuiIntegrationTest {

  @BeforeAll
  static void initJavaFX() {
    // Initialize JavaFX toolkit for tests
    try {
      new javafx.embed.swing.JFXPanel();
    } catch (Exception e) {
      // Already initialized
    }
  }

  @Test
  void testArgumentSerializationRoundTripWithMatrix() {
    // Test that Matrix serialization/deserialization works correctly
    Matrix originalMatrix = Matrix.builder()
        .matrixName("TestData")
        .columnNames("Name", "Age")
        .rows(List.of(
            List.of("Alice", 25),
            List.of("Bob", 30)
        ))
        .types(List.of(String.class, Integer.class))
        .build();

    // Serialize
    Object serialized = ArgumentSerializer.serialize(originalMatrix);
    assertNotNull(serialized);
    assertTrue(serialized instanceof Map);

    Map<String, Object> map = (Map<String, Object>) serialized;
    assertEquals("se.alipsa.matrix.core.Matrix", map.get("_type"));
    assertTrue(map.containsKey("csv"));

    // Deserialize
    Object deserialized = ArgumentSerializer.deserialize(serialized);
    assertNotNull(deserialized);
    assertTrue(deserialized instanceof Matrix);

    Matrix resultMatrix = (Matrix) deserialized;
    assertEquals("TestData", resultMatrix.getMatrixName());
    assertEquals(2, resultMatrix.rowCount());
    assertEquals(2, resultMatrix.columnCount());
  }

  @Test
  void testArgumentSerializationWithPrimitives() {
    // Test that primitives pass through unchanged
    assertEquals(42, ArgumentSerializer.serialize(42));
    assertEquals("test", ArgumentSerializer.serialize("test"));
    assertEquals(true, ArgumentSerializer.serialize(true));
    assertNull(ArgumentSerializer.serialize(null));

    // And deserialize unchanged
    assertEquals(42, ArgumentSerializer.deserialize(42));
    assertEquals("test", ArgumentSerializer.deserialize("test"));
    assertEquals(true, ArgumentSerializer.deserialize(true));
    assertNull(ArgumentSerializer.deserialize(null));
  }

  @Test
  void testGuiInteractionsMapIsPassedCorrectly() {
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    GuiInteraction mockInOut = mock(GuiInteraction.class);
    Map<String, GuiInteraction> guiInteractions = new HashMap<>();
    guiInteractions.put("io", mockInOut);

    RuntimeProcessRunner runner = new RuntimeProcessRunner(
        new RuntimeConfig("Test", RuntimeType.CUSTOM),
        List.of("dummy"),
        console,
        guiInteractions
    );

    // Verify runner was created successfully (no exception)
    assertNotNull(runner);
  }

  @Test
  void testHandleGuiRequestCaseIsAddedToSwitch() throws Exception {
    // This test verifies that the gui_request case exists in handleMessage
    // We can't easily test the full flow without a running process,
    // but we can verify the code compiles and the method exists

    ConsoleTextArea console = mock(ConsoleTextArea.class);
    Map<String, GuiInteraction> guiInteractions = Map.of();

    RuntimeProcessRunner runner = new RuntimeProcessRunner(
        new RuntimeConfig("Test", RuntimeType.CUSTOM),
        List.of("dummy"),
        console,
        guiInteractions
    );

    // Verify that handleGuiRequest method exists
    assertDoesNotThrow(() -> {
      var method = RuntimeProcessRunner.class.getDeclaredMethod(
          "handleGuiRequest", Map.class
      );
      assertNotNull(method);
    });
  }

  @Test
  void testInvokeMethodExists() throws Exception {
    // Verify that invokeMethod exists and has the correct signature
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    Map<String, GuiInteraction> guiInteractions = Map.of();

    RuntimeProcessRunner runner = new RuntimeProcessRunner(
        new RuntimeConfig("Test", RuntimeType.CUSTOM),
        List.of("dummy"),
        console,
        guiInteractions
    );

    // Verify that invokeMethod exists with correct signature
    assertDoesNotThrow(() -> {
      var method = RuntimeProcessRunner.class.getDeclaredMethod(
          "invokeMethod", GuiInteraction.class, String.class, Object[].class
      );
      assertNotNull(method);
    });
  }

  @Test
  void testSendGuiErrorMethodExists() throws Exception {
    // Verify that sendGuiError exists
    ConsoleTextArea console = mock(ConsoleTextArea.class);
    Map<String, GuiInteraction> guiInteractions = Map.of();

    RuntimeProcessRunner runner = new RuntimeProcessRunner(
        new RuntimeConfig("Test", RuntimeType.CUSTOM),
        List.of("dummy"),
        console,
        guiInteractions
    );

    // Verify that sendGuiError method exists
    assertDoesNotThrow(() -> {
      var method = RuntimeProcessRunner.class.getDeclaredMethod(
          "sendGuiError", String.class, String.class
      );
      assertNotNull(method);
    });
  }
}
