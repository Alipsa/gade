package se.alipsa.gade.code.gradle;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.TaskListener;
import se.alipsa.gade.code.groovytab.GroovyTab;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.gradle.GradleUtils;

import java.io.File;
import java.io.FileNotFoundException;

import static se.alipsa.gade.menu.GlobalOptions.GRADLE_HOME;
import static se.alipsa.gade.menu.GlobalOptions.USE_GRADLE_CLASSLOADER;

public class GradleTab extends GroovyTab implements TaskListener {

  private static final Logger log = LogManager.getLogger(GradleTab.class);
  TextField targetsField;
  public GradleTab(String title, Gade gui) {
    super(title, gui, false);
    runButton.setText("Run build");
    runButton.setOnAction(a -> runGradle());

    Label goalLabel = new Label("Goals:");
    targetsField = new TextField();
    targetsField.setText("build");
    targetsField.setPrefColumnCount(30);
    buttonPane.getChildren().addAll(goalLabel, targetsField);

    saveButton.setOnAction(a -> saveContent());
  }

  private void saveContent() {
    gui.getMainMenu().saveContent(this);
    if (gui.getPrefs().getBoolean(USE_GRADLE_CLASSLOADER, false)) {
      gui.getConsoleComponent().restartGroovy();
    }
  }

  public void runGradle() {
    // check if text has changed. If it has, offer to save. If no save skip run
    if (isChanged()) {
      boolean doSave = Alerts.confirm(
          "The file has changed",
          "You must save before running the build",
          "save now?");
      if (doSave) {
        saveContent();
      } else {
        return;
      }
    }
    String args = targetsField.getText();
    if (args == null || StringUtils.isBlank(args)) {
      Alerts.warn("Gradle arguments", "No goals (e.g. build) was supplied to gradle");
      return;
    }
    final String[] gradleArgs = args.split(" ");
    getGui().getConsoleComponent().addOutput("\n>Running 'gradle " + String.join(" ", gradleArgs) + "'", "", false, true);
    runGradle(gradleArgs);
  }

  public void runGradle(String[] gradleArgs) {
    try {
      File projectDir = gui.getInoutComponent().projectDir();
      if (projectDir == null || !projectDir.exists()) {
        projectDir = getFile().getParentFile();
      }
      File gradleHome = new File(gui.getPrefs().get(GRADLE_HOME, GradleUtils.locateGradleHome()));
      if (!gradleHome.exists()) {
        Alerts.warn("GRADLE_HOME '" + gradleHome + "' does not exist",
            "GRADLE_HOME does not exist, set it in Tools -> Global Options");
        return;
      }
      GradleUtils gutil = new GradleUtils(gradleHome, projectDir);
      gutil.buildProject(gradleArgs);
    } catch (FileNotFoundException e) {
      ExceptionAlert.showAlert("Failed to run gradle", e);
    }
  }

  @Override
  public void taskStarted() {
    runButton.setDisable(true);
  }

  @Override
  public void taskEnded() {
    runButton.setDisable(false);
  }
}
