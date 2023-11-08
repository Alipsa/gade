package se.alipsa.gade;

import static se.alipsa.gade.Constants.BRIGHT_THEME;
import static se.alipsa.gade.Constants.THEME;
import static se.alipsa.gade.menu.GlobalOptions.*;

import groovy.lang.GroovyClassLoader;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//import se.alipsa.gade.interaction.*;
import se.alipsa.gi.*;
import se.alipsa.gi.fx.*;
import se.alipsa.gade.utils.gradle.GradleUtils;
import se.alipsa.gade.code.CodeComponent;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.environment.EnvironmentComponent;
import se.alipsa.gade.inout.FileOpener;
import se.alipsa.gade.inout.InoutComponent;
import se.alipsa.gade.menu.MainMenu;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.FileUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.prefs.Preferences;

public class Gade extends Application {

  Logger log = LogManager.getLogger(Gade.class);
  private ConsoleComponent consoleComponent;
  private CodeComponent codeComponent;
  private EnvironmentComponent environmentComponent;
  private InoutComponent inoutComponent;
  private Stage primaryStage;
  private Scene scene;
  private MainMenu mainMenu;
  private Preferences preferences;
  private final Map<String, Object> sessionMap = new HashMap<>();
  private File gadeBaseDir;
  private FileOpener fileOpener;
  private static Gade instance;
  // Drivers that uses dll files (e.g. for integrated security) cannot load the dll twice byt different classloaders
  // so we need to cache the classloader and reuse it.
  public GroovyClassLoader dynamicClassLoader = new GroovyClassLoader();

  public Map<String, GuiInteraction> guiInteractions;

  public static void main(String[] args) {
    launch(args);
  }

  public static Gade instance() {
    return instance;
  }

  @Override
  public void start(Stage primaryStage) {
    log.info("Starting Gade...");
    //Thread.currentThread().setContextClassLoader(new GroovyClassLoader());
    instance = this;
    gadeBaseDir = Path.of("").toAbsolutePath().toFile();

    preferences = Preferences.userRoot().node(Gade.class.getName());
    this.primaryStage = primaryStage;

    Locale.setDefault(Locale.forLanguageTag(getPrefs().get(DEFAULT_LOCALE, Locale.getDefault().toLanguageTag())));
    TimeZone.setDefault(TimeZone.getTimeZone(getPrefs().get(TIMEZONE, TimeZone.getDefault().getID())));

    // Allow global option for GRADLE_HOME to override system settings.
    String gradleHome = getPrefs().get(GRADLE_HOME, GradleUtils.locateGradleHome());
    if (gradleHome != null && !gradleHome.isBlank()) {
      System.setProperty("GRADLE_HOME", gradleHome);
    }
    BorderPane root = new BorderPane();
    VBox main = new VBox();
    main.setAlignment(Pos.CENTER);
    main.setFillWidth(true);

    root.setCenter(main);

    mainMenu = new MainMenu(this);
    root.setTop(mainMenu);

    scene = new Scene(root, 1366, 768);

    addStyleSheet(getPrefs().get(THEME, BRIGHT_THEME));

    SplitPane leftSplitPane = new SplitPane();
    leftSplitPane.setOrientation(Orientation.VERTICAL);

    consoleComponent = new ConsoleComponent(this);
    stretch(consoleComponent, root);

    environmentComponent = new EnvironmentComponent(this);
    stretch(environmentComponent, root);

    codeComponent = new CodeComponent(this);
    stretch(codeComponent, root);
    leftSplitPane.getItems().addAll(codeComponent, consoleComponent);

    fileOpener = new FileOpener(codeComponent);

    SplitPane rightSplitPane = new SplitPane();
    rightSplitPane.setOrientation(Orientation.VERTICAL);


    inoutComponent = new InoutComponent(this);
    stretch(inoutComponent, root);

    rightSplitPane.getItems().addAll(environmentComponent, inoutComponent);

    SplitPane splitPane = new SplitPane();
    splitPane.setOrientation(Orientation.HORIZONTAL);
    splitPane.getItems().addAll(leftSplitPane, rightSplitPane);
    splitPane.setDividerPositions(0.6, 0.4);

    main.getChildren().add(splitPane);

    primaryStage.setOnCloseRequest(t -> {
      if (getCodeComponent().hasUnsavedFiles()) {
        boolean exitAnyway = Alerts.confirm(
            "Are you sure you want to exit?",
            "There are unsaved files",
            "Are you sure you want to exit \n -even though you have unsaved files?"
        );
        if (!exitAnyway) {
          t.consume();
          return;
        }
      }
      endProgram();
    });

    primaryStage.setTitle("Gade, a Groovy Analytics IDE");
    primaryStage.getIcons().add(
        new Image(Objects.requireNonNull(getClass().getResourceAsStream("/image/logo.png")))
    );
    primaryStage.setScene(scene);
    enableDragDrop(scene);
    //consoleComponent.initGroovy(Gade.this.getClass().getClassLoader());
    guiInteractions = Map.of(
        "io", new InOut(primaryStage)
    );
    consoleComponent.initGroovy(Gade.instance().dynamicClassLoader);
    primaryStage.show();
  }

  private void enableDragDrop(Scene scene) {

    scene.setOnDragOver(event -> {
      Dragboard db = event.getDragboard();
      if (db.hasFiles()) {
        // I wish there was a TransferMode.OPEN but there is not
        event.acceptTransferModes(TransferMode.LINK);
        db.setDragView(new Image("image/file.png"));
      } else {
        event.consume();
      }
    });
    // Dropping over surface
    scene.setOnDragDropped(event -> {
      Dragboard db = event.getDragboard();
      boolean success = false;
      if (db.hasFiles()) {
        success = true;
        for (File file:db.getFiles()) {
          fileOpener.openFile(file, false);
        }
      }
      event.setDropCompleted(success);
      event.consume();
    });
  }

  public void addStyleSheet(String styleSheetPath) {
    scene.getStylesheets().add(Objects.requireNonNull(FileUtils.getResourceUrl(styleSheetPath)).toExternalForm());
  }

  public ObservableList<String> getStyleSheets() {
    return scene.getStylesheets();
  }

  public void endProgram() {
    Platform.exit();
    // Allow some time before calling system exist so stop() can be used to do stuff if needed
    Timer timer = new Timer();
    TimerTask task = new TimerTask() {
      public void run() {
        System.exit(0);
      }
    };
    timer.schedule(task, 200);
  }

  private void stretch(Pane component, Pane root) {
    component.prefHeightProperty().bind(root.heightProperty());
    component.prefWidthProperty().bind(root.widthProperty());
  }

  private void stretch(Control component, Pane root) {
    component.prefHeightProperty().bind(root.heightProperty());
    component.prefWidthProperty().bind(root.widthProperty());
  }

  public void setTitle(String title) {
    primaryStage.setTitle("Gade, a Groovy analytics IDE: " + title);
  }

  public ConsoleComponent getConsoleComponent() {
    return consoleComponent;
  }

  public CodeComponent getCodeComponent() {
    return codeComponent;
  }

  public EnvironmentComponent getEnvironmentComponent() {
    return environmentComponent;
  }

  public InoutComponent getInoutComponent() {
    return inoutComponent;
  }

  public Stage getStage() {
    return primaryStage;
  }

  public MainMenu getMainMenu() {
    return mainMenu;
  }

  public void setWaitCursor() {
    Platform.runLater(() -> {
      scene.setCursor(Cursor.WAIT);
      consoleComponent.busy();
    });
  }

  public boolean isWaitCursorSet() {
    return Cursor.WAIT.equals(scene.getCursor());
  }

  public void setNormalCursor() {
    Platform.runLater(() -> {
      scene.setCursor(Cursor.DEFAULT);
      consoleComponent.ready();
      environmentComponent.setNormalCursor();
    });
  }

  public Preferences getPrefs() {
    return preferences;
  }

  public Scene getScene() {
    return scene;
  }

  public void saveSessionObject(String key, Object val) {
    sessionMap.put(key, val);
  }

  public Object getSessionObject(String key) {
    return sessionMap.get(key);
  }

  public File getGadeBaseDir() {
    return gadeBaseDir;
  }

}
