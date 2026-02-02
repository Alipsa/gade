package se.alipsa.gade.menu;

import static se.alipsa.gade.Constants.*;
import static se.alipsa.gade.menu.GlobalOptions.*;
import static se.alipsa.gade.utils.StringUtils.formatNumber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.GroovySystem;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.commons.text.CaseUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.jetbrains.annotations.NotNull;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import se.alipsa.gade.runtime.RuntimeEditorDialog;
import se.alipsa.gade.runtime.RuntimeEditorResult;
import se.alipsa.gade.runtime.RuntimeConfig;
import se.alipsa.gade.runtime.RuntimeManager;
import se.alipsa.gade.runtime.RuntimeType;
import se.alipsa.gade.Constants;
import se.alipsa.gade.Gade;
import se.alipsa.gade.UnStyledCodeArea;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.TextAreaTab;
import se.alipsa.gade.code.munin.MuninGmdTab;
import se.alipsa.gade.code.munin.MuninGroovyTab;
import se.alipsa.gade.model.ReportType;
import se.alipsa.gade.model.MuninConnection;
import se.alipsa.gade.model.MuninReport;
import se.alipsa.gade.utils.*;
import se.alipsa.gade.utils.git.GitUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.prefs.Preferences;

public class MainMenu extends MenuBar {

  private final Gade gui;
  private MenuItem interruptMI;
  private static final Logger log = LogManager.getLogger(MainMenu.class);
  private final List<String> searchStrings = new UniqueList<>();
  private Stage searchWindow;
  private Menu runtimesMenu;
  private final ToggleGroup runtimeToggleGroup = new ToggleGroup();
  private final PauseTransition runtimeReloadDebounce = new PauseTransition(Duration.millis(300));
  private String runtimeReloadReason;

  public MainMenu(Gade gui) {
    this.gui = gui;
    Menu menuFile = createFileMenu();
    Menu menuEdit = createEditMenu();
    Menu menuCode = createCodeMenu();
    //Menu menuView = new Menu("View");
    //Menu menuPlots = new Menu("Plots");
    Menu menuSession = createSessionMenu();
    Menu menuRuntimes = createRuntimesMenu();
    //Menu menuBuild = new Menu("Build");
    //Menu menuDebug = new Menu("Debug");
    //Menu menuProfile = new Menu("Profile");
    Menu menuTools = createToolsMenu();
    Menu menuMunin = createMuninMenu();
    Menu menuHelp = createHelpMenu();
    getMenus().addAll(menuFile, menuEdit, menuCode, /*menuView, menuPlots,*/ menuSession,
       menuRuntimes, /*menuBuild, menuDebug, menuProfile, */ menuTools, menuMunin, menuHelp);

    runtimeReloadDebounce.setOnFinished(e -> {
      if (runtimeReloadReason == null) {
        return;
      }
      String reason = runtimeReloadReason;
      runtimeReloadReason = null;
      log.debug("Restarting runtime due to {}", reason);
      restartEngine();
    });
  }

  // TODO:
  //  - add actions to all items
  //  - add custom items if defined before
  //  - mark the currently selected runtime if any
  private Menu createRuntimesMenu() {
    runtimesMenu = new Menu("Runtimes");
    refreshRuntimesMenu();
    return runtimesMenu;
  }

  public void refreshRuntimesMenu() {
    if (runtimesMenu == null) {
      return;
    }
    runtimesMenu.getItems().clear();
    runtimeToggleGroup.getToggles().clear();
    RuntimeManager manager = gui.getRuntimeManager();
    File projectDir = gui.getProjectDir();
    RuntimeConfig active = gui.getActiveRuntime();

    for (RuntimeConfig runtime : manager.getAllRuntimes()) {
      boolean available = manager.isAvailable(runtime, projectDir);
      runtimesMenu.getItems().add(createRuntimeMenuItem(runtime, active, available));
    }

    runtimesMenu.getItems().add(new SeparatorMenuItem());
    MenuItem addCustomItem = new MenuItem("Add custom runtime");
    addCustomItem.setOnAction(e -> openRuntimeEditor(null, true));
    MenuItem editCustomItems = new MenuItem("Edit custom runtimes");
    editCustomItems.setOnAction(e -> openRuntimeEditor(null, false));
    runtimesMenu.getItems().addAll(addCustomItem, editCustomItems);
  }

  private RadioMenuItem createRuntimeMenuItem(RuntimeConfig runtime, RuntimeConfig active, boolean available) {
    RadioMenuItem item = new RadioMenuItem(runtime.getName());
    item.setToggleGroup(runtimeToggleGroup);
    item.setDisable(!available);
    if (active != null && active.getName().equalsIgnoreCase(runtime.getName())) {
      item.setSelected(true);
    }
    item.setOnAction(e -> selectRuntime(runtime));
    return item;
  }

  private void selectRuntime(RuntimeConfig runtime) {
    gui.selectRuntime(runtime);
  }

  private void openRuntimeEditor(RuntimeConfig runtime, boolean newRuntime) {
    RuntimeEditorDialog dialog = new RuntimeEditorDialog(gui, runtime, newRuntime);
    Optional<RuntimeEditorResult> result = dialog.showAndWait();
    if (result.isEmpty()) {
      return;
    }
    RuntimeEditorResult res = result.get();
    RuntimeManager manager = gui.getRuntimeManager();
    List<RuntimeConfig> previous = new ArrayList<>(manager.getCustomRuntimes());
    res.getCustomRuntimes().forEach(manager::addOrUpdateCustomRuntime);
    previous.stream()
        .filter(prev -> res.getCustomRuntimes().stream().noneMatch(updated -> updated.getName().equalsIgnoreCase(prev.getName())))
        .forEach(prev -> manager.deleteCustomRuntime(prev.getName()));
    RuntimeConfig selected = res.getSelectedRuntime();
    if (selected != null) {
      gui.selectRuntime(selected);
    }
    refreshRuntimesMenu();
  }

  private Menu createCodeMenu() {
    Menu menu = new Menu("Code");
    MenuItem commentItem = new MenuItem("Toggle line comments  ctrl+shift+C");
    commentItem.setOnAction(this::commentLines);
    menu.getItems().add(commentItem);
    SeparatorMenuItem separator = new SeparatorMenuItem();
    menu.getItems().add(separator);

    MenuItem projectWizard = new MenuItem("Create project");
    projectWizard.setOnAction(this::showProjectWizard);
    menu.getItems().add(projectWizard);

    MenuItem packageWizard = new MenuItem("Create library");
    packageWizard.setOnAction(this::showLibraryWizard);
    menu.getItems().add(packageWizard);

    MenuItem createBuildFileMI = new MenuItem("Create build file");
    createBuildFileMI.setOnAction(this::createBuildFile);
    menu.getItems().add(createBuildFileMI);

    MenuItem cloneProjectMI = new MenuItem("Clone a git project");
    cloneProjectMI.setOnAction(this::cloneProject);
    menu.getItems().add(cloneProjectMI);

    return menu;
  }

  private Menu createMuninMenu() {
    Menu menu = new Menu("Munin");
    MenuItem configureMI = new MenuItem("Configure");
    configureMI.setOnAction(a -> configureMuninConnection());
    MenuItem loadReportMI = new MenuItem("Load report");
    loadReportMI.setOnAction(a -> loadMuninReport());
    MenuItem createUnmanagedReportMI = new MenuItem("Create Groovy report");
    createUnmanagedReportMI.setOnAction(a -> createUnmanagedReport());
    MenuItem createMdrReportMI = new MenuItem("Create gmd report");
    createMdrReportMI.setOnAction(a -> createGmdReport());
    menu.getItems().addAll(configureMI, loadReportMI, createUnmanagedReportMI, createMdrReportMI);
    return menu;
  }

  private void loadMuninReport() {
    MuninConnection con = (MuninConnection) gui.getSessionObject(SESSION_MUNIN_CONNECTION);
    if (con == null) {
      con = configureMuninConnection();
    }
    if (con == null) return;

    MuninReportDialog reportDialog = new MuninReportDialog(gui);
    Optional<MuninReport> result = reportDialog.showAndWait();
    if (result.isEmpty()) return;
    MuninReport report = result.get();
    TextAreaTab tab;
    tab = ReportType.GMD.equals(report.getReportType()) ? new MuninGmdTab(gui, report) : new MuninGroovyTab(gui, report);
    //tab.replaceContentText(0, 0, report.getDefinition());
    gui.getCodeComponent().addTabAndActivate(tab);
  }

  private void createUnmanagedReport() {
    MuninReport report = new MuninReport(DEFAULT_GROOVY_REPORT_NAME, ReportType.UNMANAGED);
    report.setDefinition("library('se.alipsa:htmlcreator')\n\n" +
        "html.clear()\n" +
        "html.add('<h1>add content like this here</h1>')");
    var tab = new MuninGroovyTab(gui, report);
    gui.getCodeComponent().addTabAndActivate(tab);
  }

  private void createGmdReport() {
    MuninReport report = new MuninReport(DEFAULT_GMD_REPORT_NAME, ReportType.GMD);
    MuninGmdTab tab = new MuninGmdTab(gui, report);
    gui.getCodeComponent().addTabAndActivate(tab);
  }

  private void cloneProject(ActionEvent actionEvent) {
    CloneProjectDialog dialog = new CloneProjectDialog(gui);
    Optional<CloneProjectDialogResult> result = dialog.showAndWait();
    if (result.isEmpty()) {
      return;
    }
    CloneProjectDialogResult res = result.get();
    try {
      gui.setWaitCursor();
      gui.getInoutComponent().cloneGitRepo(res.url, res.targetDir);
      gui.setNormalCursor();
    } catch (GitAPIException | RuntimeException e) {
      ExceptionAlert.showAlert("Failed to clone repository: " + e.getMessage(), e);
    }
  }

  private void createBuildFile(ActionEvent actionEvent) {
    CreateProjectWizardDialog dialog = new CreateProjectWizardDialog(gui, "Create build file", false);
    Optional<CreateProjectWizardResult> result = dialog.showAndWait();
    if (result.isEmpty()) {
      return;
    }
    CreateProjectWizardResult res = result.get();
    try {
      if (BuildSystem.NONE.equals(res.buildSystem)) {
        Alerts.infoFx("No build file selected", "Build system was set to None; no build file was generated.");
        return;
      }
      if (BuildSystem.GRADLE.equals(res.buildSystem)) {
        String mainProjectScript = camelCasedPackageName(res) + ".groovy";
        String scriptContent = createBuildScript("templates/project_build.gradle", res.groupName, res.projectName, mainProjectScript);
        FileUtils.writeToFile(new File(res.dir, "build.gradle"), scriptContent);
      } else if (BuildSystem.MAVEN.equals(res.buildSystem)) {
        String pomContent = createBuildScript("templates/project-pom.xml", res.groupName, res.projectName);
        FileUtils.writeToFile(new File(res.dir, "pom.xml"), pomContent);
      }
      gui.getInoutComponent().refreshFileTree();
    } catch (IOException e) {
      ExceptionAlert.showAlert("Failed to create build file", e);
    }
  }


  private String camelCasedPackageName(CreateProjectWizardResult res) {
    return CaseUtils.toCamelCase(res.projectName, true,
        ' ', '_', '-', ',', '.', '/', '\\');
  }

  private void showProjectWizard(ActionEvent actionEvent) {
    CreateProjectWizardDialog dialog = new CreateProjectWizardDialog(gui);
    Optional<CreateProjectWizardResult> result = dialog.showAndWait();
    if (result.isEmpty()) {
      return;
    }
    CreateProjectWizardResult res = result.get();
    try {
      Files.createDirectories(res.dir.toPath());

      String camelCasedProjectName = camelCasedPackageName(res);
      String mainProjectScript = camelCasedProjectName + ".groovy";
      if (BuildSystem.GRADLE.equals(res.buildSystem)) {
        String buildScriptContent = createBuildScript("templates/project_build.gradle", res.groupName, res.projectName, mainProjectScript);
        FileUtils.writeToFile(new File(res.dir, "build.gradle"), buildScriptContent);
        FileUtils.copy("templates/settings.gradle", res.dir, Map.of("[artifactId]", res.projectName));
      } else if (BuildSystem.MAVEN.equals(res.buildSystem)) {
        String pomContent = createBuildScript("templates/project-pom.xml", res.groupName, res.projectName);
        FileUtils.writeToFile(new File(res.dir, "pom.xml"), pomContent);
      }

      Path mainPath = new File(res.dir, "src/main/groovy").toPath();
      Files.createDirectories(mainPath);
      Path mainFile = mainPath.resolve(mainProjectScript);
      Files.createFile(mainFile);
      Path testPath = new File(res.dir, "src/test/groovy").toPath();
      Files.createDirectories(testPath);
      Path testFile = Files.createFile(testPath.resolve(camelCasedProjectName + "Test.groovy"));
      FileUtils.writeToFile(testFile.toFile(), createTest(camelCasedProjectName)
      );

      Path testResourcePath = new File(res.dir, "src/test/resources/").toPath();
      Files.createDirectories(testResourcePath);
      FileUtils.copy("templates/log4j.properties", testResourcePath.toFile());

      if (res.changeToDir) {
        gui.getInoutComponent().changeRootDir(res.dir);
        selectDefaultRuntimeForBuildSystem(res.dir, res.buildSystem, true);
      } else {
        selectDefaultRuntimeForBuildSystem(res.dir, res.buildSystem, false);
        gui.getInoutComponent().refreshFileTree();
      }
    } catch (IOException e) {
      ExceptionAlert.showAlert("Failed to create package project", e);
    }
  }

  @NotNull
  private String createTest(String camelCasedProjectName) {
    return """
        import org.junit.jupiter.api.*
        import se.alipsa.groovy.matrix.*
        import org.codehaus.groovy.runtime.InvokerHelper
                
        class [className]Test {
              
          void test[className]() {
            // 1. Create a binding, possibly with parameters which will be equivalent to the main args.
            Binding context = new Binding();
            // 2. Create and invoke the script
            Script script = InvokerHelper.createScript([className].class, context);
            script.run()
            // 3. Access "global" (@Field) variables from the binding context, e.g:
            //Table table = context.getVariable("table") as Table
            
            //4. Make assertions on these variables as appropriate
          }
        }
        """.replace("[className]", camelCasedProjectName);
  }

  private String createBuildScript(String filePath, String groupName, String projectName, String... mainProjectScript) throws IOException {
    String content = FileUtils.readContent(filePath);
    if (mainProjectScript.length > 0) {
      content = content.replace("[mainScriptName]", mainProjectScript[0]);
    }
    return content
            .replace("[groupId]", groupName)
            .replace("[artifactId]", projectName)
            .replace("[name]", projectName)
            .replace("[groovyVersion]", GroovySystem.getVersion());
  }

  private void showLibraryWizard(ActionEvent actionEvent) {
    CreateLibraryWizardDialog dialog = new CreateLibraryWizardDialog(gui);
    Optional<CreateLibraryWizardResult> result = dialog.showAndWait();
    if (result.isEmpty()) {
      return;
    }
    CreateLibraryWizardResult res = result.get();
    try {
      Files.createDirectories(res.dir.toPath());

      String camelCasedLibName = CaseUtils.toCamelCase(res.libName, true,
         ' ', '_', '-', ',', '.', '/', '\\');

      if (BuildSystem.GRADLE.equals(res.buildSystem)) {
        String scriptContent = createBuildScript("templates/library_build.gradle", res.groupName, res.libName);
        FileUtils.writeToFile(new File(res.dir, "build.gradle"), scriptContent);
        FileUtils.copy("templates/settings.gradle", res.dir, Map.of("[artifactId]", res.libName));
      } else if (BuildSystem.MAVEN.equals(res.buildSystem)) {
        String pomContent = createBuildScript("templates/package-pom.xml", res.groupName, res.libName);
        FileUtils.writeToFile(new File(res.dir, "pom.xml"), pomContent);
      }

      Path mainPath = new File(res.dir, "src/main/groovy").toPath();
      Files.createDirectories(mainPath);
      Path scriptFile = mainPath.resolve(camelCasedLibName + ".groovy");
      Files.createFile(scriptFile);
      //FileUtils.writeToFile(rFile.toFile(), "# remember to add export(function name) to NAMESPACE to make them available");
      Path testPath = new File(res.dir, "src/test/groovy").toPath();
      Files.createDirectories(testPath);
      Path testFile = Files.createFile(testPath.resolve(camelCasedLibName + "Test.groovy"));
      FileUtils.writeToFile(testFile.toFile(), createTest(camelCasedLibName));
      Path testResourcePath = new File(res.dir, "src/test/resources/").toPath();
      Files.createDirectories(testResourcePath);
      FileUtils.copy("templates/log4j.properties", testResourcePath.toFile());
      if (res.changeToDir) {
        gui.getInoutComponent().changeRootDir(res.dir);
        selectDefaultRuntimeForBuildSystem(res.dir, res.buildSystem, true);
      } else {
        selectDefaultRuntimeForBuildSystem(res.dir, res.buildSystem, false);
        gui.getInoutComponent().refreshFileTree();
      }
    } catch (IOException e) {
      ExceptionAlert.showAlert("Failed to create package project", e);
    }
  }

  private void selectDefaultRuntimeForBuildSystem(File projectDir, BuildSystem buildSystem, boolean activateNow) {
    if (projectDir == null || buildSystem == null) {
      return;
    }
    RuntimeManager manager = gui.getRuntimeManager();
    RuntimeConfig runtime = switch (buildSystem) {
      case MAVEN -> manager.findRuntime(RuntimeManager.RUNTIME_MAVEN).orElse(new RuntimeConfig(RuntimeManager.RUNTIME_MAVEN, RuntimeType.MAVEN));
      case GRADLE -> manager.findRuntime(RuntimeManager.RUNTIME_GRADLE).orElse(new RuntimeConfig(RuntimeManager.RUNTIME_GRADLE, RuntimeType.GRADLE));
      case NONE -> manager.findRuntime(RuntimeManager.RUNTIME_GADE).orElse(new RuntimeConfig(RuntimeManager.RUNTIME_GADE, RuntimeType.GADE));
    };
    manager.setSelectedRuntime(projectDir, runtime);
    if (activateNow) {
      gui.selectRuntime(runtime);
    } else {
      refreshRuntimesMenu();
    }
  }

  private void commentLines(ActionEvent actionEvent) {
    commentLines();
  }

  public void commentLines() {
    CodeTextArea codeArea = gui.getCodeComponent().getActiveTab().getCodeArea();
    String lineComment;
    switch (gui.getCodeComponent().getActiveTab().getCodeType()) {
      case SQL:
        lineComment = "--";
        break;
      case JAVA, GROOVY:
        lineComment = "//";
        break;
      default:
        return;
    }
    String selected = codeArea.selectedTextProperty().getValue();
    // if text is selected then go with that
    if (selected != null && !"".equals(selected)) {

      IndexRange range = codeArea.getSelection();
      String s = toggelComment(selected, lineComment);
      codeArea.replaceText(range, s);
    } else { // toggle current line
      String text = codeArea.getText(codeArea.getCurrentParagraph());
      String s = toggelComment(text, lineComment);
      int org = codeArea.getCaretPosition();
      codeArea.moveTo(codeArea.getCurrentParagraph(), 0);
      int start = codeArea.getCaretPosition();
      int end = start + text.length();
      codeArea.replaceText(start, end, s);
      codeArea.moveTo(org);
    }
  }

  private String toggelComment(String selected, String lineComment) {
    String[] lines = selected.split("\n");
    List<String> commented = new ArrayList<>();
    for (String line : lines) {
      if (line.startsWith(lineComment)) {
        commented.add(line.substring(lineComment.length()));
      } else {
        commented.add(lineComment + line);
      }
    }
    return String.join("\n", commented);
  }

  private Menu createEditMenu() {
    Menu menu = new Menu("Edit");
    MenuItem undo = new MenuItem("Undo  ctrl+Z");
    undo.setOnAction(this::undo);
    MenuItem redo = new MenuItem("Redo ctrl+Y");
    redo.setOnAction(this::redo);
    MenuItem find = new MenuItem("Find ctrl+F");
    find.setOnAction(this::displayFind);
    menu.getItems().addAll(undo, redo, find);
    return menu;
  }

  private void redo(ActionEvent actionEvent) {
    TextAreaTab codeTab = gui.getCodeComponent().getActiveTab();
    CodeTextArea codeArea = codeTab.getCodeArea();
    codeArea.redo();
  }

  private void undo(ActionEvent actionEvent) {
    TextAreaTab codeTab = gui.getCodeComponent().getActiveTab();
    CodeTextArea codeArea = codeTab.getCodeArea();
    codeArea.undo();
  }

  private void displayFind(ActionEvent actionEvent) {
    displayFind();
  }

  public void displayFind() {
    if (searchWindow != null) {
      searchWindow.toFront();
      searchWindow.requestFocus();
      return;
    }

    VBox vBox = new VBox();
    vBox.setPadding(new Insets(3));
    FlowPane pane = new FlowPane();
    vBox.getChildren().add(pane);
    Label resultLabel = new Label();
    resultLabel.setPadding(new Insets(1));
    vBox.getChildren().add(resultLabel);
    pane.setPadding(Constants.FLOWPANE_INSETS);
    pane.setHgap(Constants.HGAP);
    pane.setVgap(Constants.VGAP);
    Button findButton = new Button("search");

    ComboBox<String> searchInput = new ComboBox<>();
    searchInput.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) {
        findButton.fire();
      }
    });
    searchInput.setEditable(true);
    if (searchStrings.size() > 0) {
      searchStrings.forEach(s -> searchInput.getItems().add(s));
      searchInput.setValue(searchStrings.get(searchStrings.size()-1));
    }

    findButton.setOnAction(e -> {
      TextAreaTab codeTab = gui.getCodeComponent().getActiveTab();
      if (codeTab == null) {
        resultLabel.setText("No active code tab exists, nothing to search in");
        return;
      }
      CodeTextArea codeArea = codeTab.getCodeArea();
      int caretPos = codeArea.getCaretPosition();
      String text = codeTab.getAllTextContent().substring(caretPos);
      String searchWord = searchInput.getValue();
      if (searchWord == null) {
        searchWord = searchInput.getEditor().getText();
        if (searchWord == null) {
          log.warn("searchWord is null and nothing entered in the combobox text field, nothing that can be searched");
          resultLabel.setText("Nothing to search for");
          return;
        }
      }
      searchStrings.add(searchWord);
      if (!searchInput.getItems().contains(searchWord)) {
        searchInput.getItems().add(searchWord);
      }
      if (text.contains(searchWord)) {
        int place = text.indexOf(searchWord);
        codeArea.moveTo(place);
        codeArea.selectRange(caretPos + place, caretPos + place + searchWord.length());
        codeArea.requestFollowCaret();
        resultLabel.setText("found on line " + (codeArea.getCurrentParagraph() + 1));
      } else {
        resultLabel.setText(searchWord + " not found");
      }
    });

    Button toTopButton = new Button("To beginning");
    toTopButton.setOnAction(a -> {
      TextAreaTab codeTab = gui.getCodeComponent().getActiveTab();
      CodeTextArea codeArea = codeTab.getCodeArea();
      codeArea.moveTo(0);
      codeArea.requestFollowCaret();
    });
    pane.getChildren().addAll(searchInput, findButton, toTopButton);
    Scene scene = new Scene(vBox);
    scene.getStylesheets().addAll(Gade.instance().getStyleSheets());
    searchWindow = new Stage();
    searchWindow.setOnCloseRequest(event -> searchWindow = null);
    searchWindow.setTitle("Find");
    searchWindow.setScene(scene);
    searchWindow.sizeToScene();
    searchWindow.show();
    searchWindow.toFront();
    searchWindow.setAlwaysOnTop(true);

  }

  private Menu createHelpMenu() {
    Menu menu = new Menu("Help");
    
    MenuItem manual = new MenuItem("User Manual");
    manual.setOnAction(this::displayUserManual);
    
    MenuItem about = new MenuItem("About Gade");
    about.setOnAction(a -> AboutDialog.displayAbout());

    MenuItem checkVersion = new MenuItem("Check for updates");
    checkVersion.setOnAction(this::checkForUpdates);

    MenuItem viewLogFile = new MenuItem("View logfile");
    viewLogFile.setOnAction(this::viewLogFile);

    menu.getItems().addAll(manual, checkVersion, viewLogFile, about);
    return menu;
  }

  private void viewLogFile(ActionEvent actionEvent) {
    try {
      org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
      Map.Entry<String, Appender> appenderEntry = logger.get().getAppenders().entrySet().stream()
          .filter(e -> "GadeLog".equals(e.getKey())).findAny().orElse(null);
      if (appenderEntry == null) {
        Alerts.warn("Failed to find log file", "Failed to find an appender called GadeLog");
        return;
      }
      FileAppender appender = (FileAppender) appenderEntry.getValue();

      File logFile = new File(appender.getFileName());
      if (!logFile.exists()) {
        Alerts.warn("Failed to find log file", "Failed to find log file " + logFile.getAbsolutePath());
        return;
      }
      try {
        String content = FileUtils.readContent(logFile);
        Rectangle2D screenBounds = Screen.getPrimary().getBounds();

        Alerts.showInfoAlert(logFile.getAbsolutePath(), content,  Math.min(screenBounds.getWidth(), 1200.0), Math.min(screenBounds.getHeight(), 830.0));
      } catch (IOException e) {
        ExceptionAlert.showAlert("Failed to read log file content", e);
      }
    } catch (RuntimeException e) {
      ExceptionAlert.showAlert("Failed to show log file", e);
    }
  }

  private void checkForUpdates(ActionEvent actionEvent) {
      gui.setWaitCursor();
      Alert popup = new Alert(Alert.AlertType.INFORMATION);
      popup.setTitle("Check latest version");
      popup.getDialogPane().setHeaderText("Gade version info");
      TextArea textArea = new TextArea("Checking for the latest version....");
      textArea.setEditable(false);
      textArea.setWrapText(true);
      GridPane gridPane = new GridPane();
      gridPane.setMaxWidth(Double.MAX_VALUE);
      gridPane.add(textArea, 0, 0);
      popup.getDialogPane().setContent(gridPane);
      popup.setResizable(true);
      popup.initOwner(gui.getStage());
      popup.show();

      Platform.runLater(() -> {
        try {
          URL url = new URI("https://api.github.com/repos/perNyfelt/gade/releases/latest").toURL();
          ObjectMapper mapper = new ObjectMapper();
          JsonNode rootNode;
          URLConnection conn = url.openConnection();
          conn.setConnectTimeout(5_000);
          conn.setReadTimeout(8_000);
          try (InputStream in = conn.getInputStream()) {
            rootNode = mapper.readTree(in);
          }
          JsonNode tagNode = rootNode.findValue("tag_name");
          String tag = tagNode.asText();
          String releaseTag = "unknown";
          String version = "unknown";
          Properties props = new Properties();
          try (InputStream is = Objects.requireNonNull(FileUtils.getResourceUrl("version.properties")).openStream()) {
            props.load(is);
            version = props.getProperty("version");
            releaseTag = props.getProperty("releaseTag");
          } catch (IOException e) {
            ExceptionAlert.showAlert("Failed to load properties file", e);
          }
          StringBuilder sb = new StringBuilder("Your version: ")
              .append(version)
              .append("\nYour release tag:")
              .append(releaseTag)
              .append("\n\nLatest version on github: ").append(tag);

          int versionDiff = SemanticVersion.compare(releaseTag, tag);
          boolean identicalVersion = releaseTag.equalsIgnoreCase(tag);
          if (versionDiff < 0) {
            sb.append("\nA newer version is available.");
          } else if (versionDiff > 0) {
            sb.append("\nYou appear to be running a later version than what is released on github");
          } else {
            sb.append("\nThe semantic version number matches with the latest release.");
            if(!identicalVersion) {
              sb.append("\nHowever, versions are not identical");
            }
          }
          if (identicalVersion) {
            sb.append("\nYou are running the latest version");
          } else if (versionDiff < 1){
            sb.append("\nGet the latest release from https://github.com/perNyfelt/gade/releases/latest");
          }
          textArea.setText(sb.toString());
          gui.setNormalCursor();
        } catch (IOException | URISyntaxException e) {
          gui.setNormalCursor();
          ExceptionAlert.showAlert("Failed to get latest version", e);
        }
    });
  }

  private void displayUserManual(ActionEvent actionEvent) {
    new UserManual(gui).show();
  }

  private Menu createToolsMenu() {

    Menu toolsMenu = new Menu("Tools");
    MenuItem globalOption = new MenuItem("Global Options");
    globalOption.setOnAction(this::handleGlobalOptions);
    toolsMenu.getItems().add(globalOption);
    return toolsMenu;
  }

  private void handleGlobalOptions(ActionEvent actionEvent) {
    GlobalOptionsDialog dialog = new GlobalOptionsDialog(gui);
    Optional<GlobalOptions> res = dialog.showAndWait();
    boolean shouldRestart = false;

    if (res.isEmpty()) {
      return;
    }

    gui.setWaitCursor();
    GlobalOptions result = res.get();

    int consoleMaxLength = result.getInt(CONSOLE_MAX_LENGTH_PREF);
    if (gui.getConsoleComponent().getConsoleMaxSize() != consoleMaxLength) {
      gui.getPrefs().putInt(CONSOLE_MAX_LENGTH_PREF, consoleMaxLength);
      gui.getConsoleComponent().setConsoleMaxSize(consoleMaxLength);
    }

    String theme = result.getString(THEME);
    if (!gui.getScene().getStylesheets().contains(theme)) {
      gui.getScene().getStylesheets().clear();
      gui.addStyleSheet(theme);
      gui.getPrefs().put(THEME, theme);
    }

    Locale.setDefault(Locale.forLanguageTag(result.getString(DEFAULT_LOCALE)));

    TimeZone.setDefault(TimeZone.getTimeZone(result.getString(TIMEZONE)));
    gui.getPrefs().put(TIMEZONE, result.getString(TIMEZONE));

    boolean restartSessionAfterGradle = result.getBoolean(RESTART_SESSION_AFTER_GRADLE_RUN);
    gui.getPrefs().putBoolean(RESTART_SESSION_AFTER_GRADLE_RUN, restartSessionAfterGradle);

    boolean addBuildDirToClasspath = result.getBoolean(ADD_BUILDDIR_TO_CLASSPATH);
    if (addBuildDirToClasspath != gui.getPrefs().getBoolean(ADD_BUILDDIR_TO_CLASSPATH, !addBuildDirToClasspath)) {
      log.info("addBuildDirToClasspath changed, restarting Groovy session");
      gui.getPrefs().putBoolean(ADD_BUILDDIR_TO_CLASSPATH, addBuildDirToClasspath);
      shouldRestart = true;
    }

    boolean enableGit = result.getBoolean(ENABLE_GIT);
    gui.getInoutComponent().setEnableGit(enableGit);
    gui.getPrefs().putBoolean(ENABLE_GIT, enableGit);

    boolean runAutoRunGlobal = result.getBoolean(AUTORUN_GLOBAL);
    if (runAutoRunGlobal != gui.getPrefs().getBoolean(AUTORUN_GLOBAL, !runAutoRunGlobal)) {
      gui.getPrefs().putBoolean(AUTORUN_GLOBAL, runAutoRunGlobal);
    }

    boolean runAutoRunProject = result.getBoolean(AUTORUN_PROJECT);
    if (runAutoRunProject != gui.getPrefs().getBoolean(AUTORUN_PROJECT, !runAutoRunProject)) {
      gui.getPrefs().putBoolean(AUTORUN_PROJECT, runAutoRunProject);
    }

    gui.getPrefs().putBoolean(ADD_IMPORTS, result.getBoolean(ADD_IMPORTS));
    gui.getPrefs().putBoolean(ADD_DEPENDENCIES, result.getBoolean(ADD_DEPENDENCIES));

    if (shouldRestart) {
      restartEngine();
    }

    gui.setNormalCursor();
  }

  public void disableInterruptMenuItem() {
    interruptMI.setDisable(true);
  }

  public void enableInterruptMenuItem() {
    interruptMI.setDisable(false);
  }

  private Menu createSessionMenu() {
    Menu sessionMenu = new Menu("Session");
    MenuItem restartMI = new MenuItem("Restart Groovy");
    restartMI.setOnAction(this::restartEngine);
    interruptMI = new MenuItem("Interrupt Groovy");
    interruptMI.setOnAction(this::interruptProcess);
    disableInterruptMenuItem();

    MenuItem sessionInfo = new MenuItem("SessionInfo");
    sessionInfo.setOnAction(this::showSessionInfo);

    sessionMenu.getItems().addAll(restartMI, interruptMI, sessionInfo);
    return sessionMenu;
  }

  private void showSessionInfo(ActionEvent actionEvent) {
    StringBuilder content = new StringBuilder();
    content.append(" Available cpu cores: ");
    content.append(Runtime.getRuntime().availableProcessors());
    content.append("\n Allocated Memory: ");
    long totalMem = Runtime.getRuntime().totalMemory();
    content.append(formatNumber(Math.round((double)totalMem / 1024 / 1024))).append(" MB");
    content.append("\n Used Memory: ");
    long freeMem = Runtime.getRuntime().freeMemory();
    long usedMem = totalMem - freeMem;
    content.append(formatNumber(Math.round((double)usedMem / 1024 / 1024))).append(" MB");
    content.append("\n Free memory: ");
    content.append(formatNumber(Math.round((double)freeMem / 1024 / 1024))).append(" MB");
    content.append("\n Maximum allowed memory: ");
    content.append(formatNumber(Math.round((double)Runtime.getRuntime().maxMemory() / 1024 / 1024))).append(" MB");

    String inUseJava = System.getProperty("java.runtime.version");
    content.append("\n\n Java Runtime Version (in use): ")
        .append(inUseJava)
        .append(" (").append(System.getProperty("os.arch")).append(")");

    if (gui.getConsoleComponent() != null) {
      gui.getConsoleComponent().getConfiguredJavaVersion().ifPresent(configured -> {
        content.append("\n Configured runtime Java: ").append(configured);
        if (gui.getConsoleComponent().isConfiguredJavaDifferent(configured)) {
          content.append(" (differs from in-use JVM)");
        }
      });
    }

    content.append("\n Groovy version: ").append(gui.getConsoleComponent() == null
        ? GroovySystem.getVersion()
        : gui.getConsoleComponent().getActiveGroovyVersion());
    Alerts.showInfoAlert("Session info", content, 600, 300);
  }

  private void interruptProcess(ActionEvent actionEvent) {
    gui.getConsoleComponent().interruptProcess();
  }

  private void restartEngine(ActionEvent evt) {
    restartEngine();
  }

  public void restartEngine() {
    gui.getConsoleComponent().restartGroovy();
    gui.getInoutComponent().setPackages(null);
    gui.getEnvironmentComponent().restarted();
  }

  private Menu createFileMenu() {
    Menu menu = new Menu("File");

    Menu fileMenu = new Menu("New File");

    MenuItem nGroovy = new MenuItem("Groovy");
    nGroovy.setOnAction(a -> gui.getCodeComponent().addCodeTab(CodeType.GROOVY));
    fileMenu.getItems().add(nGroovy);

    MenuItem nSql = new MenuItem("SQL");
    nSql.setOnAction(a -> gui.getCodeComponent().addCodeTab(CodeType.SQL));
    fileMenu.getItems().add(nSql);

    MenuItem gMarkdown = new MenuItem("Groovy Markdown");
    gMarkdown.setOnAction(a -> gui.getCodeComponent().addCodeTab(CodeType.GMD));
    fileMenu.getItems().add(gMarkdown);

    MenuItem nMarkdown = new MenuItem("Markdown");
    nMarkdown.setOnAction(a -> gui.getCodeComponent().addCodeTab(CodeType.MD));
    fileMenu.getItems().add(nMarkdown);

    MenuItem nText = new MenuItem("Text");
    nText.setOnAction(a -> gui.getCodeComponent().addCodeTab(CodeType.TXT));
    fileMenu.getItems().add(nText);

    MenuItem nXml = new MenuItem("Xml");
    nXml.setOnAction(a -> gui.getCodeComponent().addCodeTab(CodeType.XML));
    fileMenu.getItems().add(nXml);

    MenuItem nJs = new MenuItem("Javascript");
    nJs.setOnAction(a -> gui.getCodeComponent().addCodeTab(CodeType.JAVA_SCRIPT));
    fileMenu.getItems().add(nJs);

    MenuItem nJava = new MenuItem("Java");
    nJava.setOnAction(a -> gui.getCodeComponent().addCodeTab(CodeType.JAVA));
    fileMenu.getItems().add(nJava);

    MenuItem nSas = new MenuItem("SAS");
    nSas.setOnAction(a -> gui.getCodeComponent().addCodeTab(CodeType.SAS));
    fileMenu.getItems().add(nSas);

    MenuItem nR = new MenuItem("R");
    nR.setOnAction(a -> gui.getCodeComponent().addCodeTab(CodeType.R));
    fileMenu.getItems().add(nR);

    MenuItem save = new MenuItem("Save  ctrl+S");
    save.setOnAction(this::saveContent);

    MenuItem saveAs = new MenuItem("Save as");
    saveAs.setOnAction(this::saveContentAs);

    MenuItem quit = new MenuItem("Quit Session");
    quit.setOnAction(e -> gui.endProgram());

    menu.getItems().addAll(fileMenu, save, saveAs, quit);
    return menu;
  }


  private void saveContent(ActionEvent event) {
    TextAreaTab codeArea = gui.getCodeComponent().getActiveTab();
    saveContent(codeArea);
  }

  private void saveContentAs(ActionEvent event) {
    TextAreaTab codeArea = gui.getCodeComponent().getActiveTab();
    saveContentAs(codeArea);
  }

  public void saveContent(TextAreaTab codeArea) {
    File file = codeArea.getFile();
    if (file == null) {
      file = promptForFile();
      if (file == null) {
        return;
      }
    }
    try {
      saveFile(codeArea, file);
      Git git = gui.getInoutComponent().getGit();
      if(codeArea.getTreeItem() != null && git != null) {
        String path = GitUtils.asRelativePath(codeArea.getFile(), gui.getInoutComponent().projectDir());
        GitUtils.colorNode(git, path, codeArea.getTreeItem());
      }
    } catch (FileNotFoundException e) {
      ExceptionAlert.showAlert("Failed to save file " + file, e);
    }
  }

  private void saveContentAs(TextAreaTab codeArea) {
    File file = promptForFile();
    if (file == null) {
      return;
    }
    try {
      saveFile(codeArea, file);
    } catch (FileNotFoundException e) {
      ExceptionAlert.showAlert("Failed to save file " + file, e);
    }
  }

  private void saveFile(TextAreaTab codeArea, File file) throws FileNotFoundException {
    boolean fileExisted = file.exists();
    FileUtils.writeToFile(file, codeArea.getAllTextContent());
    log.debug("File {} saved", file.getAbsolutePath());
    codeArea.setTitle(file.getName());
    if (!fileExisted) {
      gui.getInoutComponent().fileAdded(file);
    }
    gui.getCodeComponent().fileSaved(file);
    codeArea.contentSaved();
    maybeReloadRuntimeAfterSave(file);
  }

  private void maybeReloadRuntimeAfterSave(File file) {
    if (file == null || gui.getConsoleComponent() == null) {
      return;
    }
    File projectDir = gui.getProjectDir();
    if (projectDir == null) {
      return;
    }
    Path projectPath = projectDir.toPath().toAbsolutePath().normalize();
    Path filePath = file.toPath().toAbsolutePath().normalize();
    if (!filePath.startsWith(projectPath)) {
      return;
    }

    RuntimeConfig activeRuntime = gui.getActiveRuntime();
    if (activeRuntime == null || activeRuntime.getType() == null) {
      return;
    }
    if (RuntimeType.GRADLE.equals(activeRuntime.getType())) {
      if (!isGradleClasspathFile(projectPath, filePath)) {
        return;
      }
    } else if (RuntimeType.MAVEN.equals(activeRuntime.getType())) {
      if (!isMavenClasspathFile(projectPath, filePath)) {
        return;
      }
    } else {
      return;
    }

    runtimeReloadReason = "saved " + projectPath.relativize(filePath);
    runtimeReloadDebounce.stop();
    runtimeReloadDebounce.playFromStart();
  }

  private static boolean isGradleClasspathFile(Path projectDir, Path filePath) {
    String name = filePath.getFileName() == null ? "" : filePath.getFileName().toString();
    if ("build.gradle".equals(name)
        || "build.gradle.kts".equals(name)
        || "settings.gradle".equals(name)
        || "settings.gradle.kts".equals(name)
        || "gradle.properties".equals(name)) {
      return true;
    }
    Path rel;
    try {
      rel = projectDir.relativize(filePath);
    } catch (IllegalArgumentException e) {
      return false;
    }
    return rel.equals(Path.of("gradle", "libs.versions.toml"))
        || rel.equals(Path.of("gradle", "wrapper", "gradle-wrapper.properties"));
  }

  private static boolean isMavenClasspathFile(Path projectDir, Path filePath) {
    String name = filePath.getFileName() == null ? "" : filePath.getFileName().toString();
    if ("pom.xml".equals(name)) {
      return true;
    }
    Path rel;
    try {
      rel = projectDir.relativize(filePath);
    } catch (IllegalArgumentException e) {
      return false;
    }
    if (!rel.startsWith(Path.of(".mvn"))) {
      return false;
    }
    return rel.equals(Path.of(".mvn", "maven.config"))
        || rel.equals(Path.of(".mvn", "extensions.xml"))
        || rel.equals(Path.of(".mvn", "wrapper", "maven-wrapper.properties"));
  }

  public File promptForFile() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setInitialDirectory(gui.getInoutComponent().projectDir());
    fileChooser.setTitle("Save File");
    return fileChooser.showSaveDialog(gui.getStage());
  }

  public File promptForFile(String fileTypeDescription, String extension, String suggestedName) {
    FileChooser fileChooser = new FileChooser();
    FileChooser.ExtensionFilter fileExtensions =
        new FileChooser.ExtensionFilter(
            fileTypeDescription, "*" + extension);
    fileChooser.getExtensionFilters().add(fileExtensions);
    fileChooser.setInitialDirectory(gui.getInoutComponent().projectDir());
    fileChooser.setTitle("Save File");
    fileChooser.setInitialFileName(suggestedName);
    File file = fileChooser.showSaveDialog(gui.getStage());
    if (file != null && !file.getName().endsWith(extension)) {
      File parent = file.getParentFile();
      if (parent != null) {
        file = new File(parent, file.getName() + extension);
      }
    }
    return file;
  }

  public MuninConnection configureMuninConnection() {
    Dialog<MuninConnection> dialog = new Dialog<>();
    GridPane pane = new GridPane();
    dialog.getDialogPane().setContent(pane);

    Preferences prefs = gui.getPrefs();

    pane.add(new Label("Server name or IP: "), 0, 0);
    TextField serverTf = new TextField();
    serverTf.setText(prefs.get(PREF_MUNIN_SERVER, ""));
    pane.add(serverTf, 1, 0);

    pane.add(new Label("Server port: "), 0, 1);
    IntField serverPortIf = new IntField(0, 65535, 8088);
    serverPortIf.setValue(prefs.getInt(PREF_MUNIN_PORT, 8088));
    pane.add(serverPortIf, 1, 1);

    pane.add(new Label("Username: "), 0, 2);
    TextField userNameTf = new TextField();
    userNameTf.setText(prefs.get(PREF_MUNIN_USERNAME, ""));
    pane.add(userNameTf, 1, 2);

    pane.add(new Label("Password: "), 0, 3);
    PasswordField pwdTf = new PasswordField();
    pane.add(pwdTf, 1, 3);

    dialog.setResultConverter(callback -> {
      MuninConnection con = new MuninConnection();
      con.setServerName(serverTf.getText());
      con.setServerPort(serverPortIf.getValue());
      con.setUserName(userNameTf.getText());
      con.setPassword(pwdTf.getText());
      return con;
    });
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    GuiUtils.addStyle(gui, dialog);

    if(serverTf.getText().length() > 0 && userNameTf.getText().length() > 0) {
      Platform.runLater(pwdTf::requestFocus);
    }

    Optional<MuninConnection> opt = dialog.showAndWait();
    MuninConnection con = opt.orElse(null);
    if (con == null) return null;
    gui.saveSessionObject(SESSION_MUNIN_CONNECTION, con);
    prefs.put(PREF_MUNIN_SERVER, con.getServerName());
    prefs.putInt(PREF_MUNIN_PORT, con.getServerPort());
    prefs.put(PREF_MUNIN_USERNAME, con.getUserName());
    return con;
  }
}
