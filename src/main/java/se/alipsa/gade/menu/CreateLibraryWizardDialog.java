package se.alipsa.gade.menu;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.utils.GuiUtils;

import java.io.File;

public class CreateLibraryWizardDialog extends Dialog<CreateLibraryWizardResult> {

  private static final Logger log = LogManager.getLogger();

  private final TextField groupNameField;
  private final TextField packageNameField;
  private File selectedDirectory;
  private final Gade gui;
  private final TextField dirField;
  private final CheckBox changeToDir;

  private final TextField packageDirField;
  private final Node okButton;

  CreateLibraryWizardDialog(Gade gui) {
    this.gui = gui;
    //initOwner(gui.getStage());
    setTitle("Create Package Wizard");

    Insets insets = new Insets(10, 15, 10, 10);

    getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    okButton = getDialogPane().lookupButton(ButtonType.OK);
    okButton.setDisable(true);

    BorderPane pane = new BorderPane();
    pane.setPadding(insets);
    getDialogPane().setContent(pane);
    VBox vBox = new VBox();
    vBox.setPadding(insets);
    pane.setCenter(vBox);

    HBox groupBox = new HBox();
    groupBox.setPadding(insets);
    groupBox.setSpacing(10);
    vBox.getChildren().add(groupBox);
    Label groupNameLabel = new Label("Group Name");
    groupBox.getChildren().add(groupNameLabel);
    groupNameField = new TextField();
    groupNameField.setPrefColumnCount(10);
    groupNameField.setTooltip(new Tooltip("Should be reverse domain name of your org e.g. com.acme"));
    groupNameField.focusedProperty().addListener((arg0, wasFocused, isNowFocused) -> {
      if (!isNowFocused) {
        checkAndMaybeEnableButtons();
      }
    });
    groupBox.getChildren().add(groupNameField);

    HBox packageBox = new HBox();
    packageBox.setPadding(insets);
    packageBox.setSpacing(10);
    vBox.getChildren().add(packageBox);
    Label packageNameLabel = new Label("Package Name");
    packageBox.getChildren().add(packageNameLabel);
    packageNameField = new TextField();
    packageNameField.setPrefColumnCount(10);
    packageNameField.setTooltip(new Tooltip("The name of your package; do not use spaces or slashes, only a-z, 0-9, _, -"));
    packageNameField.focusedProperty().addListener((arg0, wasFocused, isNowFocused) -> {
      if (!isNowFocused) {
        updateDirField(packageNameField.getText());
      }
    });
    packageBox.getChildren().add(packageNameField);

    HBox dirBox = new HBox();
    dirBox.setPadding(insets);
    dirBox.setSpacing(10);
    vBox.getChildren().add(dirBox);
    Label chooseDirLabel = new Label("Base dir");
    dirBox.getChildren().add(chooseDirLabel);
    Button chooseDirButton = new Button("Browse...");
    dirBox.getChildren().add(chooseDirButton);
    chooseDirButton.setOnAction(this::chooseProjectDir);
    dirField = new TextField();
    // Need to wrap it as disabled nodes cannot show tooltips.
    Label dirWrapper = new Label("", dirField);
    dirField.setDisable(true);
    HBox.setHgrow(dirField, Priority.ALWAYS);
    HBox.setHgrow(dirWrapper, Priority.ALWAYS);
    dirWrapper.setMaxWidth(Double.MAX_VALUE);
    dirField.setMaxWidth(Double.MAX_VALUE);
    selectedDirectory = gui.getInoutComponent().projectDir();
    dirField.setText(selectedDirectory.getAbsolutePath());
    dirWrapper.setTooltip(new Tooltip(selectedDirectory.getAbsolutePath()));
    dirBox.getChildren().add(dirWrapper);

    HBox packageDirBox = new HBox();
    packageDirBox.setPadding(insets);
    packageDirBox.setSpacing(10);
    vBox.getChildren().add(packageDirBox);
    Label packageDirlabel = new Label("Package project dir");
    packageDirField = new TextField();
    packageDirField.setText(selectedDirectory.getAbsolutePath());
    packageDirField.setDisable(true);
    Label packageDirWrapper = new Label("", packageDirField);
    HBox.setHgrow(packageDirField, Priority.ALWAYS);
    HBox.setHgrow(packageDirWrapper, Priority.ALWAYS);
    packageDirWrapper.setMaxWidth(Double.MAX_VALUE);
    packageDirField.setMaxWidth(Double.MAX_VALUE);
    packageDirBox.getChildren().addAll(packageDirlabel, packageDirWrapper);

    HBox changeToDirBox = new HBox();
    changeToDirBox.setPadding(insets);
    changeToDirBox.setSpacing(10);
    vBox.getChildren().add(changeToDirBox);
    changeToDir = new CheckBox("Change to new project dir");
    changeToDir.setSelected(true);
    changeToDirBox.getChildren().add(changeToDir);


    HBox packageLayoutBox = new HBox();
    packageLayoutBox.setPadding(insets);
    packageLayoutBox.setSpacing(10);
    vBox.getChildren().add(packageLayoutBox);

    getDialogPane().setPrefSize(700, 320);
    getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
    setResizable(true);

    GuiUtils.addStyle(gui, this);

    setResultConverter(button -> button == ButtonType.OK ? createResult() : null);
  }

  private void updateDirField(String artifactName) {
    packageDirField.setText(new File(selectedDirectory, artifactName.trim()).getAbsolutePath());
    checkAndMaybeEnableButtons();
  }

  private void chooseProjectDir(ActionEvent actionEvent) {
    DirectoryChooser dirChooser = new DirectoryChooser();
    File rootDir = gui.getInoutComponent().projectDir();
    if (rootDir != null && rootDir.exists()) {
      dirChooser.setInitialDirectory(rootDir);
    }
    File orgSelectedDir = selectedDirectory;
    selectedDirectory = dirChooser.showDialog(gui.getStage());

    if (selectedDirectory == null) {
      log.info("No Directory selected, revert to previous dir ({})", orgSelectedDir);
      selectedDirectory = orgSelectedDir;
    } else {
      dirField.setText(selectedDirectory.getAbsolutePath());
      packageDirField.setText(new File(selectedDirectory, packageNameField.getText().trim()).getAbsolutePath());
      checkAndMaybeEnableButtons();
    }
  }

  private void checkAndMaybeEnableButtons() {
    if (!packageNameField.getText().trim().equals("") && !groupNameField.getText().trim().equals("") && !packageDirField.getText().trim().equals("")) {
      okButton.setDisable(false);
    }
  }

  private CreateLibraryWizardResult createResult() {
    CreateLibraryWizardResult res = new CreateLibraryWizardResult();
    res.groupName = groupNameField.getText();
    res.libName = packageNameField.getText();
    res.dir = new File(packageDirField.getText().trim());
    res.changeToDir = changeToDir.isSelected();
    return res;
  }
}
