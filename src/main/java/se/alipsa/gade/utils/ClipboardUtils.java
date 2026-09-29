package se.alipsa.gade.utils;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

public final class ClipboardUtils {

  private static final Logger log = LogManager.getLogger(ClipboardUtils.class);

  private ClipboardUtils() {
  }

  public static void copyText(String text) {
    if (SystemUtils.getPlatform() == SystemUtils.OS.MAC) {
      try {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        return;
      } catch (RuntimeException e) {
        log.warn("Could not copy text through the macOS clipboard; using JavaFX clipboard", e);
      }
    }

    ClipboardContent content = new ClipboardContent();
    content.putString(text);
    Clipboard.getSystemClipboard().setContent(content);
  }
}
