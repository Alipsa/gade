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
import se.alipsa.gade.utils.gradle.GradleUtils;

import java.io.File;

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
  }

  public void runGradle() {
    // check if text has changed. If it has, offer to save. If no save skip run
    if (isChanged()) {
      boolean doSave = Alerts.confirm(
          "The file has changed",
          "You must save before running the build",
          "save now?");
      if (doSave) {
        gui.getMainMenu().saveContent(this);
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
    File projectDir = gui.getInoutComponent().projectDir();
    if (projectDir == null || !projectDir.exists()) {
      projectDir = getFile().getParentFile();
    }
    String javaHome = gui.getRuntimeManager().getSelectedRuntime(projectDir).getJavaHome();
    GradleUtils gutil = new GradleUtils(null, projectDir, javaHome);
    gutil.buildProject(gradleArgs);
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
