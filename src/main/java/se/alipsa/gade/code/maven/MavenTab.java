package se.alipsa.gade.code.maven;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.ExecutableTab;
import se.alipsa.gade.code.xmltab.XmlTextArea;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.maven.MavenBuildUtils;

import java.io.File;
import java.util.prefs.Preferences;

public class MavenTab extends ExecutableTab {

  private static final Logger log = LogManager.getLogger(MavenTab.class);
  private static final String PREF_LAST_GOALS_PREFIX = "MavenTab.lastGoals.";

  private final XmlTextArea xmlTextArea;
  private final TextField goalsField;

  public MavenTab(String title, Gade gui) {
    super(gui, CodeType.MAVEN, "Run build");
    setTitle(title);

    Label goalLabel = new Label("Goals:");
    goalsField = new TextField();
    goalsField.setPrefColumnCount(30);
    goalsField.setText(loadDefaultGoals());

    buttonPane.getChildren().addAll(goalLabel, goalsField);

    xmlTextArea = new XmlTextArea(this);
    VirtualizedScrollPane<CodeTextArea> xmlPane = new VirtualizedScrollPane<>(xmlTextArea);
    pane.setCenter(xmlPane);
  }

  @Override
  protected void executeAction() {
    runMaven();
  }

  @Override
  protected CodeTextArea getTextArea() {
    return xmlTextArea;
  }

  private String loadDefaultGoals() {
    File projectDir = resolveProjectDir();
    Preferences prefs = gui.getPrefs();
    String defaultGoals = "test";
    if (projectDir == null) {
      return defaultGoals;
    }
    return prefs.get(PREF_LAST_GOALS_PREFIX + MavenBuildUtils.projectKey(projectDir), defaultGoals);
  }

  public void runMaven() {
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
    String args = goalsField.getText();
    if (StringUtils.isBlank(args)) {
      Alerts.warn("Maven arguments", "No goals (e.g. test) was supplied to Maven");
      return;
    }
    File projectDir = resolveProjectDir();
    if (projectDir == null) {
      Alerts.warn("Maven build", "No project directory found for this pom.xml");
      return;
    }
    gui.getPrefs().put(PREF_LAST_GOALS_PREFIX + MavenBuildUtils.projectKey(projectDir), args.trim());
    gui.getConsoleComponent().addOutput("\n>Running 'mvn " + args.trim() + "'", "", false, true);

    String javaHome = gui.getRuntimeManager().getSelectedRuntime(projectDir).getJavaHome();
    MavenBuildUtils build = new MavenBuildUtils(projectDir, javaHome);
    try {
      build.buildProject(args, gui.getConsoleComponent(), this);
    } catch (Exception e) {
      log.warn("Failed to start Maven build", e);
      Alerts.warn("Maven build", "Failed to start Maven build: " + e.getMessage());
    }
  }

  private File resolveProjectDir() {
    File projectDir = gui.getInoutComponent().projectDir();
    if (projectDir != null && projectDir.exists()) {
      return projectDir;
    }
    File file = getFile();
    return file == null ? null : file.getParentFile();
  }

  @Override
  public void replaceContentText(String content, boolean isReadFromFile) {
    xmlTextArea.replaceText(content);
    if (isReadFromFile) {
      contentSaved();
    }
  }
}

