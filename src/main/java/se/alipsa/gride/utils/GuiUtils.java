package se.alipsa.gride.utils;

import static se.alipsa.gride.Constants.BRIGHT_THEME;
import static se.alipsa.gride.Constants.REPORT_BUG;
import static se.alipsa.gride.Constants.THEME;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.control.Dialog;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gride.Gride;

import java.net.URL;

public final class GuiUtils {

  private static final Logger log = LogManager.getLogger();

  private GuiUtils() {
    // Utility class
  }

  public static void addStyle(Gride gui, Dialog<?> dialog) {
    if (validateDialogNotNull(gui, dialog)) return;
    addStyle(gui, dialog.getDialogPane(), 4);
    Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
    stage.getIcons().addAll(gui.getStage().getIcons());
  }

  public static void addStyle(Gride gui, Stage stage) {
    stage.getIcons().addAll(gui.getStage().getIcons());
    if (stage.getScene() == null) {
      throw new RuntimeException("You must assign the scene to the stage before calling addStyle");
    }
    stage.getScene().getStylesheets().addAll(gui.getStyleSheets());
  }

  private static boolean validateDialogNotNull(Gride gui, Object dialog) {
    if (dialog == null) {
      String msg = "GuiUtils.addStyle() : Ride instance is " + gui + ", dialog is null. Called from "
          + InvocationUtils.callingMethod(4) + ". " + REPORT_BUG;
      if (Platform.isFxApplicationThread()) {
        Alerts.warn("Unexpected error", msg);
        return true;
      } else {
        throw new RuntimeException(msg);
      }
    }
    return false;
  }

  public static void addStyle(Gride gui, Parent dialog, int... stacktraceElementOpt) {
    if (validateDialogNotNull(gui, dialog)) return;
    int stacktraceElement = stacktraceElementOpt.length > 0 ? stacktraceElementOpt[0] : 4;
    addStyle(gui, dialog.getStylesheets(), stacktraceElement);
  }

  public static void addStyle(Gride gui, ObservableList<String> styleSheetList, int... stacktraceElementOpt) {
    int stacktraceElement = stacktraceElementOpt.length > 0 ? stacktraceElementOpt[0] : 3;
    if (gui != null && styleSheetList != null ) {
      String styleSheetPath = gui.getPrefs().get(THEME, BRIGHT_THEME);

      URL styleSheetUrl = FileUtils.getResourceUrl(styleSheetPath);
      if (styleSheetUrl != null) {
        styleSheetList.add(styleSheetUrl.toExternalForm());
      }
    } else {
      String msg = "GuiUtils.addStyle() : Ride instance is " + gui + ", style sheet list is " + styleSheetList + ". Called from "
                   + InvocationUtils.callingMethod(stacktraceElement) + ". " + REPORT_BUG;
      log.error(msg);

      if (Platform.isFxApplicationThread()) {
        Alerts.warn("Unexpected error", msg);
      } else {
        throw new RuntimeException(msg);
      }
    }
  }


}
