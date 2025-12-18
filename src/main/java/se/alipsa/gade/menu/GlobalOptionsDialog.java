package se.alipsa.gade.menu;

import static se.alipsa.gade.Constants.*;
import static se.alipsa.gade.console.ConsoleTextArea.CONSOLE_MAX_LENGTH_DEFAULT;
import static se.alipsa.gade.menu.GlobalOptions.*;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.GuiUtils;
import se.alipsa.gade.utils.IntField;

import java.io.File;
import java.util.*;

class GlobalOptionsDialog extends Dialog<GlobalOptions> {

  private static final Logger log = LogManager.getLogger(GlobalOptionsDialog.class);
  private IntField intField;
  private ComboBox<String> themes;
  private ComboBox<String> locals;
  private CheckBox restartSessionAfterGradleRun;
  private CheckBox addBuildDirToClasspath;
  private CheckBox enableGit;
  private CheckBox autoRunGlobal;
  private CheckBox autoRunProject;
  private CheckBox addImports;

  private CheckBox addDependencies;
  private ComboBox<String> timezone;

  GlobalOptionsDialog(Gade gui) {
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

      Label styleTheme = new Label("Theme");
      grid.add(styleTheme, 0, 1);

      themes = new ComboBox<>();
      themes.getItems().addAll(DARK_THEME, BRIGHT_THEME, BLUE_THEME);
      themes.getSelectionModel().select(gui.getPrefs().get(THEME, BRIGHT_THEME));
      grid.add(themes, 1, 1);

      //FlowPane localePane = new FlowPane();
      //grid.add(localePane, 0, 2, 4, 1);

      Label defaultLocale = new Label("Locale");
      grid.add(defaultLocale, 0, 2);
      //localePane.getChildren().add(defaultLocale);

      locals = new ComboBox<>();
      Set<String> languageTags = new TreeSet<>();
      languageTags.add(Locale.of("sv", "SE").toLanguageTag());
      for (var loc : Locale.getAvailableLocales()) {
        languageTags.add(loc.toLanguageTag());
      }
      locals.getItems().addAll(languageTags);
      locals.getSelectionModel().select(gui.getPrefs().get(DEFAULT_LOCALE, Locale.getDefault().toLanguageTag()));

      grid.add(locals, 1, 2);
      //localePane.getChildren().add(locals);

      Label timeZoneLabel = new Label("Timezone");
      grid.add(timeZoneLabel, 2, 2);
      //localePane.getChildren().add(timeZoneLabel);

      timezone = new ComboBox<>();
      timezone.getItems().addAll(List.of(TimeZone.getAvailableIDs()));
      timezone.getSelectionModel().select(gui.getPrefs().get(TIMEZONE, TimeZone.getDefault().getID()));
      grid.add(timezone, 3, 2);
      //localePane.getChildren().add(timezone);

      FlowPane useCpPane = new FlowPane();
      grid.add(useCpPane, 0,3, 4, 1);

      Label addBuildDirToClasspathLabel = new Label("Add build dir to classpath");
      addBuildDirToClasspathLabel.setPadding(new Insets(0, 27, 0, 0));
      addBuildDirToClasspathLabel.setTooltip(new Tooltip("Add target/classes and target/test-classes to classpath"));
      useCpPane.getChildren().add(addBuildDirToClasspathLabel);
      addBuildDirToClasspath = new CheckBox();
      addBuildDirToClasspath.setSelected(gui.getPrefs().getBoolean(ADD_BUILDDIR_TO_CLASSPATH, true));
      useCpPane.getChildren().add(addBuildDirToClasspath);

      // When developing packages we need to reload the session after mvn has been run
      // so that new definitions can be picked up from target/classes.
      FlowPane restartPane = new FlowPane();
      grid.add(restartPane, 0,5, 4, 1);
      Label restartSessionAfterGradleRunLabel = new Label("Restart session after build");
      restartSessionAfterGradleRunLabel.setPadding(new Insets(0, 27, 0, 0));
      restartSessionAfterGradleRunLabel.setTooltip(new Tooltip("When developing packages we need to reload the session after gradle has been run\nso that new definitions can be picked up from the build dir"));
      restartPane.getChildren().add(restartSessionAfterGradleRunLabel);
      restartSessionAfterGradleRun = new CheckBox();
      restartSessionAfterGradleRun.setSelected(gui.getPrefs().getBoolean(RESTART_SESSION_AFTER_GRADLE_RUN, true));
      restartPane.getChildren().add(restartSessionAfterGradleRun);

      FlowPane gitOptionPane = new FlowPane();
      Label enableGitLabel = new Label("Enable git integration");
      enableGitLabel.setPadding(new Insets(0, 20, 0, 0));
      enableGitLabel.setTooltip(new Tooltip("note: git must be initialized in the project dir for integration to work"));
      gitOptionPane.getChildren().add(enableGitLabel);
      enableGit = new CheckBox();
      enableGit.setSelected(gui.getPrefs().getBoolean(ENABLE_GIT, true));
      gitOptionPane.getChildren().add(enableGit);
      grid.add(gitOptionPane, 0, 6, 2, 1);

      HBox autoRunPane = new HBox();
      Label autoRunGlobalLabel = new Label("Run global autorun.groovy on init");
      autoRunGlobalLabel.setTooltip(new Tooltip("Run autorun.groovy from Gade install dir each time a session (re)starts."));
      autoRunGlobalLabel.setPadding(new Insets(0, 20, 0, 0));
      autoRunGlobal = new CheckBox();
      autoRunGlobal.setSelected(gui.getPrefs().getBoolean(AUTORUN_GLOBAL, false));
      autoRunPane.getChildren().addAll(autoRunGlobalLabel, autoRunGlobal);

      Label autoRunProjectLabel = new Label("Run project autorun.groovy on init");
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
      //grid.add(executionPane, 0, 8,4, 1);

      Label addDependeciesLabel = new Label("Run dependencies (grab, addDependency) before script");
      addDependeciesLabel.setPadding(new Insets(0, 20, 0, 20));
      executionPane.getChildren().add(addDependeciesLabel);
      addDependencies = new CheckBox();
      addDependencies.setSelected(gui.getPrefs().getBoolean(ADD_DEPENDENCIES, gui.getPrefs().getBoolean(ADD_DEPENDENCIES, true)));
      executionPane.getChildren().add(addDependencies);
      grid.add(executionPane, 0, 8,4, 1);

      getDialogPane().setPrefSize(780, 370);
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
    result.put(TIMEZONE, timezone.getValue());
    result.put(ADD_BUILDDIR_TO_CLASSPATH, addBuildDirToClasspath.isSelected());
    result.put(RESTART_SESSION_AFTER_GRADLE_RUN, restartSessionAfterGradleRun.isSelected());
    result.put(ENABLE_GIT, enableGit.isSelected());
    result.put(AUTORUN_GLOBAL, autoRunGlobal.isSelected());
    result.put(AUTORUN_PROJECT, autoRunProject.isSelected());
    result.put(ADD_IMPORTS, addImports.isSelected());
    result.put(ADD_DEPENDENCIES, addDependencies.isSelected());
    return result;
  }


}
