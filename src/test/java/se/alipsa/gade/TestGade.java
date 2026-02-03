package se.alipsa.gade;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gi.GuiInteraction;

/**
 * Test-specific Gade subclass that uses HeadlessInOut for TestFX smoke tests.
 *
 * This allows GUI tests to run without gi-fx initialization errors in headless mode.
 * The only difference from production Gade is the InOut implementation used.
 *
 * Usage in tests:
 * <pre>
 *   TestGade app = new TestGade();
 *   app.start(stage);
 * </pre>
 */
public class TestGade extends Gade {

  private static final Logger log = LogManager.getLogger(TestGade.class);

  /**
   * Override to provide HeadlessInOut instead of regular InOut.
   * This prevents gi-fx initialization which fails in headless environments.
   */
  @Override
  protected GuiInteraction createInOut() {
    log.info("Creating HeadlessInOut for testing");
    return new HeadlessInOut();
  }
}
