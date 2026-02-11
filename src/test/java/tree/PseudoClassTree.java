package tree;

import javafx.application.Application;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.TreeItem.TreeModificationEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/* works by itself but does not work with color themes*/
public class PseudoClassTree extends Application {

   private static final Logger log = LogManager.getLogger(PseudoClassTree.class);

   private TreeView<PseudoClassFileItem> treeView;
   private final TreeItem<PseudoClassFileItem> rootNode = new TreeItem<>(new PseudoClassFileItem(new File(".")));

   private Button greenButton;
   private Button blackButton;

   @Override
   public void start(Stage stage) {
      VBox box = new VBox();
      Scene scene = new Scene(box, 400, 400);

      treeView = new TreeView<>(rootNode);
      treeView.setShowRoot(true);
      rootNode.setExpanded(true);

      List<TreeItem<PseudoClassFileItem>> list = new ArrayList<>();
      list.add(createTreeItem(new PseudoClassFileItem(new File("blueTheme.css"))));
      list.add(createTreeItem(new PseudoClassFileItem(new File("brightTheme.css"))));
      list.add(createTreeItem(new PseudoClassFileItem(new File("darkTheme.css"))));
      rootNode.getChildren().setAll(list);

      treeView.setCellFactory((TreeView<PseudoClassFileItem> tv) -> {
         TreeCell<PseudoClassFileItem> cell = new TreeCell<PseudoClassFileItem>() {

            @Override
            protected void updateItem(PseudoClassFileItem item, boolean empty) {
               if (item != null) {
                  setText(item.getText());
                  log.info("updateItem: Updating pseudoclasses for {} setting {} to active",
                     item.getText(), item.getActivePseudoClass());
                  item.getPseudoClasses().forEach(pc -> {
                     if (pc.equals(item.getActivePseudoClass())) {
                        log.info("Activating {}", pc);
                        pseudoClassStateChanged(pc, true);
                     } else {
                        log.info("Inactivating {}", pc);
                        pseudoClassStateChanged(pc, false);
                     }
                  });
                  log.info("--- Pseudo classes for cell are ---");
                  getPseudoClassStates().forEach(c -> log.info("{}", c));
                  log.info("-----------------------------------");
                  log.info("--- style classes ---");
                  getStyleClass().forEach(m -> log.info("{}", m));
                  log.info("---------------------");
                  log.info("--- Css class metadata ---");
                  getClassCssMetaData().forEach(m -> log.info("{}", m));
                  log.info("--------------------------");
               }
               super.updateItem(item, empty);
            }
         };
         return cell;
      });


      greenButton = new Button("Added");
      greenButton.setOnAction(a -> updateTreeViewItem(PseudoClassFileItem.GIT_ADDED));
      blackButton = new Button("Changed");
      blackButton.setOnAction(a -> updateTreeViewItem(PseudoClassFileItem.GIT_CHANGED));

      ComboBox<String> styleSheetChooser = new ComboBox<>();
      styleSheetChooser.getItems().addAll("brightTheme.css", "blueTheme.css", "darkTheme.css");
      styleSheetChooser.setOnAction(a -> {
         scene.getStylesheets().clear();
         scene.getStylesheets().add(styleSheetChooser.getValue());
      });
      styleSheetChooser.setValue("blueTheme.css");
      scene.getStylesheets().add(styleSheetChooser.getValue());

      HBox buttonBox = new HBox();
      buttonBox.getChildren().addAll(blackButton, greenButton, styleSheetChooser);
      Insets insets = new Insets(10);
      HBox.setMargin(blackButton, insets );
      HBox.setMargin(greenButton, insets);
      HBox.setMargin(styleSheetChooser, insets);

      box.getChildren().addAll(treeView, buttonBox);
      VBox.setMargin(treeView, new Insets(10));
      VBox.setMargin(buttonBox, new Insets(10));

      stage.setScene(scene);
      stage.show();
   }


   private void updateTreeViewItem(PseudoClass styleClass) {
      TreeItem<PseudoClassFileItem> selectedItem = treeView.getSelectionModel().getSelectedItem();
      PseudoClassFileItem selected = selectedItem.getValue();
      selected.enablePseudoClass(styleClass);
   }

   public static void main(String[] args) {
      Application.launch(args);
   }


   private TreeItem<PseudoClassFileItem> createTreeItem(PseudoClassFileItem fileItem) {
      TreeItem<PseudoClassFileItem> treeItem = new TreeItem<>(fileItem);
      SetChangeListener<PseudoClass> fillListener = styleClass-> {
         log.info("PseudoClass change detected");
         TreeModificationEvent<PseudoClassFileItem> event = new TreeModificationEvent<>(TreeItem.valueChangedEvent(), treeItem);
         Event.fireEvent(treeItem, event);
      };
      fileItem.addPseudoClassChangeLister(fillListener);
      return treeItem;
   }
}
