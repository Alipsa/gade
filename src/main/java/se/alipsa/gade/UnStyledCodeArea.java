package se.alipsa.gade;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.CodeArea;

public class UnStyledCodeArea extends CodeArea {

  private boolean forwardedCommandDown;
  private boolean suppressNextTypedEvent;

  public UnStyledCodeArea() {
    getStylesheets().clear();
    getStyleClass().add("styled-text-area");
    getStyleClass().add("code-area");

    // When Gade runs remotely over X11 forwarding, macOS Command is received
    // as the Meta modifier while JavaFX is running on Linux. JavaFX therefore
    // does not apply its normal platform shortcut handling to these keys.
    addEventFilter(KeyEvent.ANY, event -> {
      if (event.getEventType() == KeyEvent.KEY_PRESSED) {
        if (isForwardedCommandKey(event.getCode())) {
          forwardedCommandDown = true;
        } else if (forwardedCommandDown || event.isMetaDown()) {
          if (KeyCode.C.equals(event.getCode())) {
            copy();
            suppressNextTypedEvent = true;
            event.consume();
          } else if (KeyCode.V.equals(event.getCode())) {
            paste();
            suppressNextTypedEvent = true;
            event.consume();
          } else if (KeyCode.A.equals(event.getCode())) {
            selectAll();
            suppressNextTypedEvent = true;
            event.consume();
          }
        }
      } else if (event.getEventType() == KeyEvent.KEY_TYPED && suppressNextTypedEvent) {
        suppressNextTypedEvent = false;
        event.consume();
      } else if (event.getEventType() == KeyEvent.KEY_RELEASED
          && isForwardedCommandKey(event.getCode())) {
          forwardedCommandDown = false;
      }
    });
  }

  private boolean isForwardedCommandKey(KeyCode keyCode) {
    return KeyCode.META.equals(keyCode)
        || KeyCode.COMMAND.equals(keyCode)
        || KeyCode.WINDOWS.equals(keyCode)
        || KeyCode.SHORTCUT.equals(keyCode);
  }
}
