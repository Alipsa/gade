package se.alipsa.gade;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.matrix.charts.Chart;
import se.alipsa.matrix.core.Matrix;

import javax.swing.JComponent;
import java.io.File;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;

/**
 * Headless implementation of InOut for testing purposes.
 * Extends AbstractInOut from gi-common to avoid gi-fx initialization.
 *
 * This allows TestFX GUI tests to run without requiring gi-fx initialization,
 * which would fail in headless mode due to missing graphical environment.
 *
 * Note: This is a minimal stub for Phase 1 (test-only support).
 * Interactive methods (prompts, file choosers, display) are noop or minimal stubs.
 * This is acceptable for smoke tests which only verify UI structure presence.
 *
 * TODO: When gi-console is published, switch to extending that instead.
 */
public class HeadlessInOut extends se.alipsa.gi.AbstractInOut {

  private static final Logger log = LogManager.getLogger(HeadlessInOut.class);

  public HeadlessInOut() {
    super();
    log.debug("HeadlessInOut initialized for testing (AbstractInOut stub)");
  }

  // File choosers - noop in headless mode
  @Override
  public File chooseFile(String title, File initialDirectory, String initialFileName, String... extensionFilters) {
    log.debug("chooseFile called in headless mode (noop)");
    return null;
  }

  @Override
  public File chooseFile(String title, String initialDirectory, String initialFileName, String... extensionFilters) {
    log.debug("chooseFile called in headless mode (noop)");
    return null;
  }

  @Override
  public File chooseDir(String title, File initialDirectory) {
    log.debug("chooseDir called in headless mode (noop)");
    return null;
  }

  @Override
  public File chooseDir(String title, String initialDirectory) {
    log.debug("chooseDir called in headless mode (noop)");
    return null;
  }

  // Prompts - return defaults or empty strings in headless mode
  @Override
  public String prompt(String headerText) {
    log.debug("prompt called in headless mode: {}", headerText);
    return "";
  }

  @Override
  public String prompt(String headerText, String label) {
    log.debug("prompt called in headless mode: {}", headerText);
    return "";
  }

  @Override
  public String prompt(String headerText, String label, String defaultValue) {
    log.debug("prompt called in headless mode: {}", headerText);
    return defaultValue != null ? defaultValue : "";
  }

  @Override
  public String prompt(String title, String headerText, String label, String defaultValue) {
    log.debug("prompt called in headless mode: {}", title);
    return defaultValue != null ? defaultValue : "";
  }

  @Override
  public YearMonth promptYearMonth(String title) {
    log.debug("promptYearMonth called in headless mode");
    return YearMonth.now();
  }

  @Override
  public YearMonth promptYearMonth(String title, String headerText, YearMonth initial, YearMonth min, YearMonth max) {
    log.debug("promptYearMonth called in headless mode");
    return initial != null ? initial : YearMonth.now();
  }

  @Override
  public LocalDate promptDate(String title, String headerText, LocalDate initial) {
    log.debug("promptDate called in headless mode");
    return initial != null ? initial : LocalDate.now();
  }

  @Override
  public Object promptSelect(String title, String headerText, String label, Collection<Object> options, Object defaultOption) {
    log.debug("promptSelect called in headless mode");
    return defaultOption;
  }

  @Override
  public String promptPassword(String title, String headerText) {
    log.debug("promptPassword called in headless mode");
    return "";
  }

  // Display methods - noop in headless mode
  @Override
  public void display(String fileName, String... title) {
    log.debug("display(String) called in headless mode: {}", fileName);
  }

  @Override
  public void display(File file, String... title) {
    log.debug("display(File) called in headless mode: {}", file);
  }

  @Override
  public void display(JComponent swingComponent, String... title) {
    log.debug("display(JComponent) called in headless mode");
  }

  @Override
  public void display(Chart chart, String... titleOpt) {
    log.debug("display(Chart) called in headless mode");
  }

  // View methods - noop in headless mode
  @Override
  public void view(File file, String... title) {
    log.debug("view(File) called in headless mode: {}", file);
  }

  @Override
  public void view(String html, String... title) {
    log.debug("view(String) called in headless mode");
  }

  @Override
  public void view(Matrix tableMatrix, String... title) {
    log.debug("view(Matrix) called in headless mode");
  }

  @Override
  public void view(List<List<?>> matrix, String... title) {
    log.debug("view(List) called in headless mode");
  }

  @Override
  public String toString() {
    return "Headless InOut for testing (AbstractInOut stub)";
  }
}
