package se.alipsa.gade;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Headless implementation of InOut for testing purposes.
 * Extends InOut from gi-console (se.alipsa.gi.txt package) to provide headless-compatible implementations.
 *
 * This allows TestFX GUI tests to run without requiring gi-fx initialization,
 * which would fail in headless mode due to missing graphical environment.
 *
 * The gi-console InOut provides sensible defaults for headless mode:
 * - File choosers return null
 * - Prompts return defaults
 * - Display methods are no-ops or save to file
 */
public class HeadlessInOut extends se.alipsa.gi.txt.InOut {

  private static final Logger log = LogManager.getLogger(HeadlessInOut.class);

  public HeadlessInOut() {
    super();
    log.debug("HeadlessInOut initialized for testing (using gi-console)");
  }

  @Override
  public String toString() {
    return "Headless InOut for testing (gi-console)";
  }
}
