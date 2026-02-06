package se.alipsa.gade.console;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.runner.GadeRunnerEngine;
import se.alipsa.gi.GuiInteraction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility methods for script execution in ConsoleComponent.
 * <p>
 * This class provides helper methods for:
 * <ul>
 *   <li>Creating error messages from exceptions</li>
 *   <li>Preparing bindings for subprocess execution</li>
 *   <li>Serializing bindings for IPC</li>
 * </ul>
 *
 * @see ConsoleComponent
 */
final class ScriptExecutionHelper {

  private static final Logger log = LogManager.getLogger(ScriptExecutionHelper.class);
  private static final String GUI_INTERACTION_KEYS = GadeRunnerEngine.GUI_INTERACTION_KEYS;

  private ScriptExecutionHelper() {
    throw new AssertionError("No instances");
  }

  /**
   * Creates a descriptive error message based on the exception type.
   *
   * @param ex the exception to describe
   * @return a message prefix describing the error type
   */
  static String createMessageFromEvalException(Throwable ex) {
    String msg;

    if (ex instanceof RuntimeException) {
      msg = "An unknown error occurred running Groovy script: ";
    } else if (ex instanceof IOException) {
      msg = "Failed to communicate with runtime process: ";
    } else if (ex instanceof RuntimeScriptException) {
      msg = "An unknown error occurred running Groovy script: ";
    } else if (ex instanceof Exception) {
      msg = "An Exception occurred: ";
    } else if (ex != null) {
      msg = "Unknown exception of type " + ex.getClass() + ": " + ex.getMessage();
    } else {
      // this should never happen
      msg = "An unknown error occurred (the Throwable is null): ";
    }
    return msg;
  }

  /**
   * Prepares bindings for the subprocess runner, including GUI interaction keys.
   *
   * @param additionalParams additional parameters to include
   * @param guiInteractions the GUI interaction map
   * @return map of bindings for the runner
   */
  static Map<String, Object> prepareRunnerBindings(Map<String, Object> additionalParams,
                                                    Map<String, GuiInteraction> guiInteractions) {
    Map<String, Object> bindings = new HashMap<>(serializeBindings(additionalParams));
    if (guiInteractions != null && !guiInteractions.isEmpty()) {
      bindings.put(GUI_INTERACTION_KEYS, new ArrayList<>(guiInteractions.keySet()));
    }
    return bindings;
  }

  /**
   * Serializes binding values to strings for IPC transfer.
   *
   * @param bindings the bindings to serialize
   * @return map with string values
   */
  static Map<String, Object> serializeBindings(Map<String, Object> bindings) {
    if (bindings == null || bindings.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, Object> sanitized = new HashMap<>();
    bindings.forEach((k, v) -> sanitized.put(k, v == null ? "null" : String.valueOf(v)));
    return sanitized;
  }
}
