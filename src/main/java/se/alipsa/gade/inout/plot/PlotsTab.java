package se.alipsa.gade.inout.plot;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.utils.ExceptionAlert;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

import static se.alipsa.gade.Constants.*;

public class PlotsTab extends Tab {

  private static final Logger log = LogManager.getLogger();
  TabPane imageTabPane;

  public PlotsTab() {
    setText("Plots");
    imageTabPane = new TabPane();
    setContent(imageTabPane);
  }

  public void showPlot(Node node, String[] title) {
    Tab tab = new Tab();
    imageTabPane.getTabs().add(tab);
    if (title.length > 0) {
      tab.setText(title[0]);
    }
    tab.setContent(node);
    final ContextMenu contextMenu = new ContextMenu();
    final MenuItem item = new MenuItem("save as image file");
    contextMenu.getItems().add(item);

    if (node instanceof ImageView) {
      var view = (ImageView)node;
      item.setOnAction(a -> promptAndWriteImage(tab.getText(), view.getImage()));

      view.setOnContextMenuRequested(e ->
        contextMenu.show(view, e.getScreenX(), e.getScreenY())
      );
    } else if (node instanceof WebView) {
      var view = (WebView) node;
      SnapshotParameters param = new SnapshotParameters();
      param.setDepthBuffer(true);
      item.setOnAction(a -> {
        WritableImage snapshot = view.snapshot(param, null);
        promptAndWriteImage(tab.getText(), snapshot);
      });
      view.setContextMenuEnabled(false);
      view.setOnMousePressed(e -> {
        if (e.getButton() == MouseButton.SECONDARY) {
          contextMenu.show(view, e.getScreenX(), e.getScreenY());
        } else {
          contextMenu.hide();
        }
      });
    } else {
      node.setOnContextMenuRequested(e ->
          contextMenu.show(node, e.getScreenX(), e.getScreenY())
      );
      item.setOnAction(a -> {
        try {
          SnapshotParameters param = new SnapshotParameters();
          // Chart background fill is not part of the snapshot, need to add it to params
          //param.setFill(parseColor("-fx-selected-bgcolor")); // works but it is the wrong color i.e. the wrong css rule
          // FIXME: Cannot find the css style for the background so below code is a workaround hack
          var theme = Gade.instance().getPrefs().get(THEME, BLUE_THEME);
          if (BLUE_THEME.equals(theme)) {
            param.setFill(Color.rgb(13, 61, 86));
          } else if (DARK_THEME.equals(theme)) {
            param.setFill(Color.rgb(69, 69, 69));
          } else if (BRIGHT_THEME.equals(theme)){
            param.setFill(Color.rgb(244, 244, 244));
          } else {
            param.setFill(Color.TRANSPARENT);
          }

          param.setDepthBuffer(true);
          var snapshot = node.snapshot(param, null);
          promptAndWriteImage(tab.getText(), snapshot);
        } catch (Throwable e) {
          ExceptionAlert.showAlert("Failed to get background", e);
        }
      });
    }

    SingleSelectionModel<Tab> imageTabsSelectionModel = imageTabPane.getSelectionModel();
    imageTabsSelectionModel.select(tab);
  }

  void promptAndWriteImage(String title, Image image) {
    FileChooser fc = new FileChooser();
    fc.setInitialFileName(title + ".png");
    File file = fc.showSaveDialog(Gade.instance().getStage());
    if (file == null) {
      return;
    }
    try {
      ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
    } catch (IOException e) {
      ExceptionAlert.showAlert("Failed to save image", e);
    }
  }
}
