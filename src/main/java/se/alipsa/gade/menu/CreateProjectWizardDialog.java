package se.alipsa.gade.menu;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.utils.GuiUtils;

import java.io.File;

public class CreateProjectWizardDialog extends Dialog<CreateProjectWizardResult> {

  private static final Logger log = LogManager.getLogger(CreateProjectWizardDialog.class);
  private static final String PREF_BUILD_SYSTEM = "CreateProjectWizardDialog.buildSystem";

  private final TextField groupNameField = new TextField();
  private final TextField projectNameField = new TextField();
  private File selectedDirectory;
  private final Gade gui;
  private final TextField dirField;
  private CheckBox changeToDir;
  private final TextField projectDirField = new TextField();
  private final boolean createProject;
  private final ToggleGroup buildSystemGroup = new ToggleGroup();

  // Used to create a new project
  CreateProjectWizardDialog(Gade gui) {
    this(gui, "Create Project Wizard", true);
  }

  // Also used to create a pom.xml only
  CreateProjectWizardDialog(Gade gui, String title, boolean createProject) {
    this.gui = gui;
    setTitle(title);
    this.createProject = createProject;

    getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    getDialogPane().lookupButton(ButtonType.OK).setDisable(true);

    Insets insets = new Insets(10, 15, 10, 10);

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(insets);
    ColumnConstraints col1 = new ColumnConstraints();
    col1.setHgrow(Priority.NEVER);
    ColumnConstraints col2 = new ColumnConstraints();
    col2.setHgrow(Priority.SOMETIMES);
    ColumnConstraints col3 = new ColumnConstraints();
    col3.setHgrow(Priority.ALWAYS);
    grid.getColumnConstraints().add(col1);
    grid.getColumnConstraints().add(col2);
    grid.getColumnConstraints().add(col3);
    getDialogPane().setContent(grid);

    Label groupNameLabel = new Label("Group Name");
    groupNameLabel.setWrapText(false);
    grid.add(groupNameLabel,0,0);
    groupNameField.setPrefColumnCount(12);
    groupNameField.setTooltip(new Tooltip("Should be reverse domain name of your org e.g. com.acme"));
    groupNameField.textProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue != null && newValue.length() > 0 && projectNameField.getText().length() > 0){
        getDialogPane().lookupButton(ButtonType.OK).setDisable(false);
      }
    });
    grid.add(groupNameField, 1,0,2, 1);

    Label packageNameLabel = new Label("Project Name");
    packageNameLabel.setWrapText(false);
    grid.add(packageNameLabel,0,1);
    projectNameField.setPrefColumnCount(10);
    projectNameField.setTooltip(new Tooltip("The name of your project; do not use spaces or slashes, only a-z, 0-9, _, -"));
    projectNameField.focusedProperty().addListener((arg0, wasFocused, isNowFocused) -> {
      if (!isNowFocused) {
        if (createProject) {
          updateDirField(projectNameField.getText());
        }
        if (groupNameField.getText().length() > 0 && projectNameField.getText().length() > 0) {
          getDialogPane().lookupButton(ButtonType.OK).setDisable(false);
        }
      }
    });
    grid.add(projectNameField, 1,1, 2, 1);

    Label buildSystemLabel = new Label("Build system");
    buildSystemLabel.setWrapText(false);
    grid.add(buildSystemLabel, 0, 2);
    RadioButton mavenBtn = new RadioButton("Maven");
    mavenBtn.setUserData(BuildSystem.MAVEN);
    mavenBtn.setToggleGroup(buildSystemGroup);
    RadioButton gradleBtn = new RadioButton("Gradle");
    gradleBtn.setUserData(BuildSystem.GRADLE);
    gradleBtn.setToggleGroup(buildSystemGroup);
    RadioButton noneBtn = new RadioButton("None");
    noneBtn.setUserData(BuildSystem.NONE);
    noneBtn.setToggleGroup(buildSystemGroup);
    noneBtn.setDisable(!createProject);

    String saved = gui.getPrefs().get(PREF_BUILD_SYSTEM, BuildSystem.MAVEN.name());
    BuildSystem initial;
    try {
      initial = BuildSystem.valueOf(saved);
    } catch (IllegalArgumentException e) {
      initial = BuildSystem.MAVEN;
    }
    if (!createProject && BuildSystem.NONE.equals(initial)) {
      initial = BuildSystem.MAVEN;
    }
    if (BuildSystem.GRADLE.equals(initial)) {
      gradleBtn.setSelected(true);
    } else if (BuildSystem.NONE.equals(initial)) {
      noneBtn.setSelected(true);
    } else {
      mavenBtn.setSelected(true);
    }

    FlowPane buildSystemPane = new FlowPane();
    buildSystemPane.setHgap(10);
    buildSystemPane.getChildren().addAll(mavenBtn, gradleBtn, noneBtn);
    grid.add(buildSystemPane, 1, 2, 2, 1);

    Label chooseDirLabel = new Label("Base dir");
    chooseDirLabel.setWrapText(false);
    grid.add(chooseDirLabel, 0,3);
    Button chooseDirButton = new Button("Browse...");
    grid.add(chooseDirButton, 1, 3);
    chooseDirButton.setOnAction(this::chooseProjectDir);
    dirField = new TextField();
    dirField.setPrefColumnCount(25);

    dirField.setDisable(true);
    grid.add(dirField, 2,3);

    selectedDirectory = gui.getInoutComponent().projectDir();
    dirField.setText(selectedDirectory.getAbsolutePath());
    dirField.setTooltip(new Tooltip(selectedDirectory.getAbsolutePath()));

    if (createProject) {
      Label packageDirlabel = new Label("Project dir");
      grid.add(packageDirlabel, 0, 4);
      projectDirField.setText(selectedDirectory.getAbsolutePath());
      projectDirField.setTooltip(new Tooltip(selectedDirectory.getAbsolutePath()));
      projectDirField.setDisable(true);
      grid.add(projectDirField, 1, 4, 2, 1);

      changeToDir = new CheckBox("Change to new project dir");
      changeToDir.setSelected(true);
      grid.add(changeToDir, 0, 5, 2, 1);
    }

    getDialogPane().setPrefSize(700, 300);
    getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
    setResizable(true);

    GuiUtils.addStyle(gui, this);

    setResultConverter(button -> button == ButtonType.OK ? createResult() : null);
  }

  private void updateDirField(String projectName) {
    String dir = new File(selectedDirectory, projectName.trim()).getAbsolutePath();
    projectDirField.setText(dir);
    projectDirField.setTooltip(new Tooltip(dir));
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
      String dir = new File(selectedDirectory, projectNameField.getText().trim()).getAbsolutePath();
      projectDirField.setText(dir);
      projectDirField.setTooltip(new Tooltip(dir));
      getDialogPane().lookupButton(ButtonType.OK).setDisable(false);
    }
  }

  private CreateProjectWizardResult createResult() {
    CreateProjectWizardResult res = new CreateProjectWizardResult();
    res.groupName = groupNameField.getText();
    res.projectName = projectNameField.getText();
    if (createProject) {
      res.dir = new File(projectDirField.getText());
    } else {
      res.dir = new File(dirField.getText());
    }
    Toggle selectedBuildSystem = buildSystemGroup.getSelectedToggle();
    if (selectedBuildSystem != null && selectedBuildSystem.getUserData() instanceof BuildSystem buildSystem) {
      res.buildSystem = buildSystem;
      gui.getPrefs().put(PREF_BUILD_SYSTEM, buildSystem.name());
    }
    if (changeToDir == null) {
      res.changeToDir = false;
    } else {
      res.changeToDir = changeToDir.isSelected();
    }
    return res;
  }
}
