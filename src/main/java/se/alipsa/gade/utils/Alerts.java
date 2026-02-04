package se.alipsa.gade.utils;

import static se.alipsa.gade.Constants.BRIGHT_THEME;
import static se.alipsa.gade.Constants.THEME;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.gade.Gade;

import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import se.alipsa.gade.UnStyledCodeArea;

public class Alerts {


  public static  Optional<ButtonType> info(String title, String content) {
    return showAlert(title, content, Alert.AlertType.INFORMATION);
  }

  public static void infoFx(String title, String content) {
    showAlertFx(title, content, Alert.AlertType.INFORMATION);
  }

  public static  Optional<ButtonType> warn(String title, String content) {
    return showAlert(title, content, Alert.AlertType.WARNING);
  }

  public static void warnFx(String title, String content) {
    showAlertFx(title, content, Alert.AlertType.WARNING);
  }

  public static boolean confirm(String title, String headerText, String contentText) {
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION, contentText, ButtonType.YES, ButtonType.NO);
    Gade gui = Gade.instance();
    alert.setTitle(title);
    alert.setHeaderText(headerText);

    alert.initOwner(gui.getStage());
    String styleSheetPath = gui.getPrefs().get(THEME, BRIGHT_THEME);

    URL styleSheetUrl = FileUtils.getResourceUrl(styleSheetPath);
    if (styleSheetUrl != null) {
      alert.getDialogPane().getStylesheets().add(styleSheetUrl.toExternalForm());
    }

    Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
    stage.getIcons().addAll(gui.getStage().getIcons());

    Optional<ButtonType> result = alert.showAndWait();
    return result.isPresent() && result.get() == ButtonType.YES;
  }

  /**
   * Shows a confirmation dialog from a background thread, blocking until the user responds.
   * Safe to call from any thread - will execute on FX Application Thread.
   *
   * @param title the dialog title
   * @param content the dialog content
   * @return true if user confirmed (clicked OK), false otherwise
   */
  public static boolean confirmFx(String title, String content) {
    if (Platform.isFxApplicationThread()) {
      return confirm(title, null, content);
    }

    AtomicBoolean result = new AtomicBoolean(false);
    CountDownLatch latch = new CountDownLatch(1);

    Platform.runLater(() -> {
      try {
        result.set(confirm(title, null, content));
      } finally {
        latch.countDown();
      }
    });

    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }

    return result.get();
  }
  public static Optional<ButtonType> showAlert(String title, String content, Alert.AlertType information) {

      TextArea textArea = new TextArea(content);
      textArea.setEditable(false);
      textArea.setWrapText(true);

      BorderPane pane = new BorderPane();
      pane.setCenter(textArea);

      Alert alert = new Alert(information);
      alert.setTitle(title);
      alert.setHeaderText(null);
      alert.getDialogPane().setContent(pane);
      alert.setResizable(true);

      Gade gui = Gade.instance();
      if (gui != null) {
         String styleSheetPath = gui.getPrefs().get(THEME, BRIGHT_THEME);

         URL styleSheetUrl = FileUtils.getResourceUrl(styleSheetPath);
         if (styleSheetUrl != null) {
            alert.getDialogPane().getStylesheets().add(styleSheetUrl.toExternalForm());
         }

        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().addAll(gui.getStage().getIcons());
      }

      return alert.showAndWait();
  }

  public static void showAlertFx(String title, String content, Alert.AlertType information) {
    Platform.runLater(() -> showAlert(title, content, information));
  }

  public static void infoStyled(String title, String content) {
    Platform.runLater(() -> {

      WebView view = new WebView();
      view.getEngine().setUserStyleSheetLocation(Objects.requireNonNull(FileUtils.getResourceUrl(BRIGHT_THEME)).toExternalForm());
      view.getEngine().loadContent(content);

      BorderPane pane = new BorderPane();
      pane.setCenter(view);

      Alert alert = new Alert(Alert.AlertType.INFORMATION);
      alert.setTitle(title);
      alert.setHeaderText(null);
      alert.getDialogPane().setContent(pane);
      Gade gui = Gade.instance();
      String styleSheetPath = gui.getPrefs().get(THEME, BRIGHT_THEME);
      URL styleSheetUrl = FileUtils.getResourceUrl(styleSheetPath);
      if (styleSheetUrl != null) {
         alert.getDialogPane().getStylesheets().add(styleSheetUrl.toExternalForm());
      }
      Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
      stage.getIcons().addAll(gui.getStage().getIcons());
      alert.setResizable(true);
      alert.showAndWait();
    });
  }

  public static void showInfoAlert(String title, String content, double contentWidth, double contentHeight) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle(title);
    alert.setHeaderText(null);
    UnStyledCodeArea ta = new UnStyledCodeArea();
    ta.getStyleClass().add("txtarea");
    ta.setWrapText(true);
    ta.replaceText(content);
    ta.setEditable(false);
    VirtualizedScrollPane<UnStyledCodeArea> scrollPane = new VirtualizedScrollPane<>(ta);
    alert.getDialogPane().setContent(scrollPane);
    alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
    alert.getDialogPane().setMinWidth(Region.USE_PREF_SIZE);
    alert.setResizable(true);

    alert.getDialogPane().setPrefHeight(contentHeight);
    alert.getDialogPane().setPrefWidth(contentWidth);

    String styleSheetPath = Gade.instance().getPrefs().get(THEME, BRIGHT_THEME);
    URL styleSheetUrl = FileUtils.getResourceUrl(styleSheetPath);
    if (styleSheetUrl != null) {
      alert.getDialogPane().getStylesheets().add(styleSheetUrl.toExternalForm());
    }

    Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
    stage.getIcons().addAll(Gade.instance().getStage().getIcons());

    alert.showAndWait();
  }

  public static void showInfoAlert(String title, StringBuilder content, double contentWidth, double contentHeight) {
    showInfoAlert(title, content.toString(), contentWidth, contentHeight);
  }
}
