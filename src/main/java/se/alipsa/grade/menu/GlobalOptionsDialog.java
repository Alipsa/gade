package se.alipsa.grade.menu;

import static se.alipsa.grade.Constants.*;
import static se.alipsa.grade.console.ConsoleTextArea.CONSOLE_MAX_LENGTH_DEFAULT;
import static se.alipsa.grade.menu.GlobalOptions.*;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import se.alipsa.grade.Grade;
import se.alipsa.grade.utils.ExceptionAlert;
import se.alipsa.grade.utils.GuiUtils;
import se.alipsa.grade.utils.IntField;

import java.util.*;

class GlobalOptionsDialog extends Dialog<GlobalOptions> {

  private IntField intField;
  private ComboBox<String> themes;
  private ComboBox<String> locals;
  private CheckBox useMavenFileClasspath;
  private TextField mavenHome;
  private CheckBox restartSessionAfterMvnRun;
  private TextField gradleHome;
  private CheckBox useGradleFileClasspath;
  private CheckBox restartSessionAfterGradleRun;
  private CheckBox addBuildDirToClasspath;
  private CheckBox enableGit;
  private CheckBox autoRunGlobal;
  private CheckBox autoRunProject;
  private CheckBox addImports;


  GlobalOptionsDialog(Grade gui) {
    try {
      setTitle("Global options");
      getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

      GridPane grid = new GridPane();
      grid.setHgap(10);
      grid.setVgap(15);
      grid.setPadding(new Insets(10, 15, 10, 10));
      getDialogPane().setContent(grid);

      Label consoleMaxSizeLabel = new Label("Console max size");
      grid.add(consoleMaxSizeLabel, 0, 0);
      intField = new IntField(1000, Integer.MAX_VALUE, gui.getPrefs().getInt(CONSOLE_MAX_LENGTH_PREF, CONSOLE_MAX_LENGTH_DEFAULT));
      grid.add(intField, 1, 0);

      Label styleTheme = new Label("Style theme");
      grid.add(styleTheme, 0, 1);
      themes = new ComboBox<>();
      themes.getItems().addAll(DARK_THEME, BRIGHT_THEME, BLUE_THEME);
      themes.getSelectionModel().select(gui.getPrefs().get(THEME, BRIGHT_THEME));
      grid.add(themes, 1, 1);

      Label defaultLocale = new Label("Default locale");
      grid.add(defaultLocale, 2, 1);

      locals = new ComboBox<>();
      Set<String> languageTags = new TreeSet<>();
      languageTags.add(new Locale("sv", "SE").toLanguageTag());
      for (var loc : Locale.getAvailableLocales()) {
        languageTags.add(loc.toLanguageTag());
      }
      locals.getItems().addAll(languageTags);
      locals.getSelectionModel().select(gui.getPrefs().get(DEFAULT_LOCALE, Locale.getDefault().toLanguageTag()));
      grid.add(locals, 3, 1);

      FlowPane cpPane = new FlowPane();
      grid.add(cpPane, 0,2, 4, 1);

      Label useMavenFileClasspathLabel = new Label("Use pom classpath");
      useMavenFileClasspathLabel.setTooltip(new Tooltip("Use classpath from pom.xml (if available) when running Groovy code"));
      useMavenFileClasspathLabel.setPadding(new Insets(0, 37, 0, 0));
      cpPane.getChildren().add(useMavenFileClasspathLabel);
      useMavenFileClasspath = new CheckBox();
      useMavenFileClasspath.setSelected(gui.getPrefs().getBoolean(USE_MAVEN_CLASSLOADER, false));
      cpPane.getChildren().add(useMavenFileClasspath);

      Label addBuildDirToClasspathLabel = new Label("Add build dir to classpath");
      addBuildDirToClasspathLabel.setPadding(new Insets(0, 27, 0, 70));
      addBuildDirToClasspathLabel.setTooltip(new Tooltip("Add target/classes and target/test-classes to classpath"));
      cpPane.getChildren().add(addBuildDirToClasspathLabel);
      addBuildDirToClasspath = new CheckBox();
      addBuildDirToClasspath.setSelected(gui.getPrefs().getBoolean(ADD_BUILDDIR_TO_CLASSPATH, true));
      cpPane.getChildren().add(addBuildDirToClasspath);

      // When developing packages we need to reload the session after mvn has been run
      // so that new definitions can be picked up from target/classes.
      Label restartSessionAfterMvnRunLabel = new Label("Restart session after mvn build");
      restartSessionAfterMvnRunLabel.setPadding(new Insets(0, 27, 0, 27));
      restartSessionAfterMvnRunLabel.setTooltip(new Tooltip("When developing packages we need to reload the session after mvn has been run\nso that new definitions can be picked up from target/classes"));
      cpPane.getChildren().add(restartSessionAfterMvnRunLabel);
      restartSessionAfterMvnRun = new CheckBox();
      restartSessionAfterMvnRun.setSelected(gui.getPrefs().getBoolean(RESTART_SESSION_AFTER_MVN_RUN, true));
      cpPane.getChildren().add(restartSessionAfterMvnRun);

      Label mavenHomeLabel = new Label("MAVEN_HOME");
      mavenHomeLabel.setTooltip(new Tooltip("The location of your maven installation directory"));
      //mavenHomeLabel.setPadding(new Insets(0, 27, 0, 0));
      grid.add(mavenHomeLabel, 0,3);

      HBox mavenHomePane = new HBox();
      mavenHomePane.setAlignment(Pos.CENTER_LEFT);
      mavenHome = new TextField();
      HBox.setHgrow(mavenHome, Priority.ALWAYS);
      mavenHome.setText(gui.getPrefs().get(MAVEN_HOME, System.getProperty("MAVEN_HOME", System.getenv("MAVEN_HOME"))));
      mavenHomePane.getChildren().add(mavenHome);
      grid.add(mavenHomePane, 1,3,3, 1);

      Label gradleHomeLabel = new Label("GRADLE_HOME");
      mavenHomeLabel.setTooltip(new Tooltip("The location of your gradle installation directory"));
      //mavenHomeLabel.setPadding(new Insets(0, 27, 0, 0));
      grid.add(gradleHomeLabel, 0,4);

      HBox gradleHomePane = new HBox();
      gradleHomePane.setAlignment(Pos.CENTER_LEFT);
      gradleHome = new TextField();
      HBox.setHgrow(gradleHome, Priority.ALWAYS);
      gradleHome.setText(gui.getPrefs().get(GRADLE_HOME, System.getProperty("GRADLE_HOME", System.getenv("GRADLE_HOME"))));
      gradleHomePane.getChildren().add(gradleHome);
      grid.add(gradleHomePane, 1,4,3, 1);

      FlowPane gradlePane = new FlowPane();
      grid.add(gradlePane, 0,5, 4, 1);

      Label useGradleFileClasspathLabel = new Label("Use build.gradle classpath");
      useGradleFileClasspathLabel.setTooltip(new Tooltip("Use classpath from build.gradle (if available) when running Groovy code"));
      useGradleFileClasspathLabel.setPadding(new Insets(0, 37, 0, 0));
      gradlePane.getChildren().add(useGradleFileClasspathLabel);
      useGradleFileClasspath = new CheckBox();
      useGradleFileClasspath.setSelected(gui.getPrefs().getBoolean(USE_GRADLE_CLASSLOADER, false));
      gradlePane.getChildren().add(useGradleFileClasspath);

      // When developing packages we need to reload the session after mvn has been run
      // so that new definitions can be picked up from target/classes.
      Label restartSessionAfterGradleRunLabel = new Label("Restart session after gradle build");
      restartSessionAfterGradleRunLabel.setPadding(new Insets(0, 27, 0, 27));
      restartSessionAfterGradleRunLabel.setTooltip(new Tooltip("When developing packages we need to reload the session after gradle has been run\nso that new definitions can be picked up from the build dir"));
      gradlePane.getChildren().add(restartSessionAfterGradleRunLabel);
      restartSessionAfterGradleRun = new CheckBox();
      restartSessionAfterGradleRun.setSelected(gui.getPrefs().getBoolean(RESTART_SESSION_AFTER_GRADLE_RUN, true));
      gradlePane.getChildren().add(restartSessionAfterGradleRun);

      FlowPane gitOptionPane = new FlowPane();
      Label enableGitLabel = new Label("Enable git integration");
      enableGitLabel.setPadding(new Insets(0, 20, 0, 0));
      enableGitLabel.setTooltip(new Tooltip("note: git must be initialized in the project dir for integration to work"));
      gitOptionPane.getChildren().add(enableGitLabel);
      enableGit = new CheckBox();
      enableGit.setSelected(gui.getPrefs().getBoolean(ENABLE_GIT, true));
      gitOptionPane.getChildren().add(enableGit);
      grid.add(gitOptionPane, 0, 6, 2, 1);

      FlowPane autoRunPane = new FlowPane();
      Label autoRunGlobalLabel = new Label("Run global autorun.groovy on session init");
      autoRunGlobalLabel.setTooltip(new Tooltip("Run autorun.groovy from Grade install dir each time a session (re)starts."));
      autoRunGlobalLabel.setPadding(new Insets(0, 20, 0, 0));
      autoRunGlobal = new CheckBox();
      autoRunGlobal.setSelected(gui.getPrefs().getBoolean(AUTORUN_GLOBAL, false));
      autoRunPane.getChildren().addAll(autoRunGlobalLabel, autoRunGlobal);

      Label autoRunProjectLabel = new Label("Run project autorun.groovy on session init");
      autoRunProjectLabel.setTooltip(new Tooltip("Run autorun.groovy from the project dir (working dir) each time a session (re)starts"));
      autoRunProjectLabel.setPadding(new Insets(0, 20, 0, 20));
      autoRunProject = new CheckBox();
      autoRunProject.setSelected(gui.getPrefs().getBoolean(AUTORUN_PROJECT, false));
      autoRunPane.getChildren().addAll(autoRunProjectLabel, autoRunProject);

      grid.add(autoRunPane, 0,7, 4, 1);

      FlowPane executionPane = new FlowPane();
      Label addImportsLabel = new Label("Add imports when running Groovy snippets");
      addImportsLabel.setPadding(new Insets(0, 20, 0, 0));
      executionPane.getChildren().add(addImportsLabel);
      addImports = new CheckBox();
      addImports.setSelected(gui.getPrefs().getBoolean(ADD_IMPORTS, gui.getPrefs().getBoolean(ADD_IMPORTS, true)));
      executionPane.getChildren().add(addImports);
      grid.add(executionPane, 0, 8,4, 1);

      getDialogPane().setPrefSize(800, 530);
      getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
      setResizable(true);

      GuiUtils.addStyle(gui, this);

      setResultConverter(button -> button == ButtonType.OK ? createResult() : null);
    } catch (Throwable t) {
      ExceptionAlert.showAlert(t.getMessage(), t);
    }
  }

  private GlobalOptions createResult() {
    GlobalOptions result = new GlobalOptions();
    result.put(CONSOLE_MAX_LENGTH_PREF, intField.getValue());
    result.put(THEME, themes.getValue());
    result.put(DEFAULT_LOCALE, locals.getValue());
    result.put(USE_MAVEN_CLASSLOADER, useMavenFileClasspath.isSelected());
    result.put(ADD_BUILDDIR_TO_CLASSPATH, addBuildDirToClasspath.isSelected());
    result.put(RESTART_SESSION_AFTER_MVN_RUN, restartSessionAfterMvnRun.isSelected());
    result.put(ENABLE_GIT, enableGit.isSelected());
    result.put(AUTORUN_GLOBAL, autoRunGlobal.isSelected());
    result.put(AUTORUN_PROJECT, autoRunProject.isSelected());
    result.put(ADD_IMPORTS, addImports.isSelected());
    return result;
  }


}
