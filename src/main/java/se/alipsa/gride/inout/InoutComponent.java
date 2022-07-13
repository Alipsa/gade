package se.alipsa.gride.inout;

import static se.alipsa.gride.menu.GlobalOptions.ENABLE_GIT;
import static se.alipsa.gride.menu.GlobalOptions.USE_MAVEN_CLASSLOADER;
import static se.alipsa.gride.utils.TableUtils.transpose;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jetbrains.annotations.NotNull;
import se.alipsa.gride.Gride;
import se.alipsa.gride.console.ConsoleTextArea;
import se.alipsa.gride.inout.plot.PlotsTab;
import se.alipsa.gride.inout.viewer.ViewTab;
import se.alipsa.gride.utils.*;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

import java.io.File;
import java.util.*;

public class InoutComponent extends TabPane  {

  private final FileTree fileTree;
  private final PlotsTab plotsTab;
  private final PackagesTab packages;
  private final ViewTab viewer;
  private final HelpTab helpTab;
  private final Gride gui;
  private final Label branchLabel;
  private final TextField statusField;
  private boolean enableGit;


  private static final Logger log = LogManager.getLogger(InoutComponent.class);

  public InoutComponent(Gride gui) {

    this.gui = gui;
    enableGit = gui.getPrefs().getBoolean(ENABLE_GIT, true);

    fileTree = new FileTree(gui, this);

    Tab filesTab = new Tab();
    filesTab.setText("Files");

    BorderPane filesPane = new BorderPane();
    FlowPane filesButtonPane = new FlowPane();

    Button refreshButton = new Button("Refresh");
    refreshButton.setOnAction(this::handleRefresh);
    filesButtonPane.getChildren().add(refreshButton);

    Button changeDirButton = new Button("Change dir");
    changeDirButton.setOnAction(this::handleChangeDir);
    filesButtonPane.getChildren().add(changeDirButton);

    branchLabel = new Label("");
    branchLabel.setPadding(new Insets(0, 0, 0, 10));
    filesButtonPane.getChildren().add(branchLabel);


    HBox hbox = new HBox();
    //Label statusLabel = new Label("Status");
    //statusLabel.setPadding(new Insets(0,5,0,10));
    //hbox.getChildren().add(statusLabel);
    statusField = new TextField();
    statusField.setPadding(new Insets(1, 10, 1, 10));
    statusField.setDisable(true);

    HBox.setHgrow(statusField, Priority.ALWAYS);
    hbox.getChildren().add(statusField);

    filesPane.setTop(filesButtonPane);
    filesPane.setCenter(fileTree);
    filesPane.setBottom(hbox);
    filesTab.setContent(filesPane);
    getTabs().add(filesTab);

    plotsTab = new PlotsTab();

    getTabs().add(plotsTab);

    packages = new PackagesTab(gui);

    getTabs().add(packages);

    helpTab = new HelpTab();
    helpTab.setText("Help");

    getTabs().add(helpTab);

    viewer = new ViewTab();

    getTabs().add(viewer);

    setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

  }

  private void handleChangeDir(ActionEvent actionEvent) {
    DirectoryChooser dirChooser = new DirectoryChooser();
    File rootDir = gui.getInoutComponent().projectDir();
    if (rootDir != null && rootDir.exists()) {
      dirChooser.setInitialDirectory(rootDir);
    }
    File selectedDirectory = dirChooser.showDialog(gui.getStage());

    if (selectedDirectory == null) {
      log.info("No Directory selected");
    } else {
      setBusy(true);
      changeRootDir(selectedDirectory);
      setBusy(false);
    }
  }

  private void setBusy(boolean busy) {
    if (busy) {
      gui.setWaitCursor();
      this.setCursor(Cursor.WAIT);
    } else {
      gui.setNormalCursor();
      this.setCursor(Cursor.DEFAULT);
    }
  }

  public void changeRootDir(File dir) {
    if (!dir.equals(projectDir())) {
      fileTree.refresh(dir);
      if (gui.getPrefs().getBoolean(USE_MAVEN_CLASSLOADER, false)) {
        //gui.getConsoleComponent().initGroovy(gui.getClass().getClassLoader());
        gui.getConsoleComponent().initGroovy(gui.dynamicClassLoader);
      }
    }
  }

  private void handleRefresh(ActionEvent actionEvent) {
    refreshFileTree();
  }

  public void refreshFileTree() {
    fileTree.refresh();
    fileTree.getRoot().setExpanded(true);
  }

  public void expandTree() {
    expandTreeNodes(fileTree.getRoot());
  }

  public void expandTreeNodes(TreeItem<?> item) {
    if (item != null && !item.isLeaf()) {
      item.setExpanded(true);
      for (TreeItem<?> child : item.getChildren()) {
        expandTreeNodes(child);
      }
    }
  }

  public void fileAdded(File file) {
    fileTree.addTreeNode(file);
  }

  public File projectDir() {
    return fileTree.getRootDir();
  }



  public void view(List<List<Object>> matrix, String... title) {
    if (matrix == null) {
      Alerts.warnFx("View", "matrix is null, cannot View");
      return;
    }
    List<String> header = createAnonymousHeader(matrix.size());
    // Instanceof check does not work due to module restrictions
    if (matrix.getClass().getName().contains(".ListAdapter")) {
      // Due to erasure and whatever other strange reasons, in java 11 Nashorn return a List<ScriptObjectMirror> and still end up here
      Alerts.warnFx("Cannot view a ListAdapter directly", "Convert " + matrix.getClass().getName() + " to a java 2D array before viewing using Java.to(data,'java.lang.Object[][]')");
      return;
    }

    var t = transpose(matrix);
    StringColumn[] columns = new StringColumn[t.size()];
    for (int i = 0; i < columns.length; i++) {
      columns[i] = StringColumn.create(header.get(i), t.get(i).stream().map(String::valueOf).toArray(String[]::new));
    }
    Table table = Table.create().addColumns(columns);
    viewTable(table, title);
  }

  public void view(Object matrix, String... title) {
    if (matrix == null) {
      Alerts.warnFx("View", "matrix is null, cannot View");
      return;
    }
    ConsoleTextArea console = gui.getConsoleComponent().getConsole();
    //if (matrix instanceof NativeArray || matrix instanceof ScriptObjectMirror) {
    if (matrix instanceof ScriptObjectMirror) {
      Alerts.warnFx("Cannot View native javascript objects", "Use the View function or convert the matrix to a java 2d array before calling inout.View()");
      return;
    }
    if (matrix instanceof Object[][]) {
      view2dArray((Object[][])matrix, title);
    } else {
      console.appendWarningFx("Unknown matrix type " + matrix.getClass().getName());
      console.appendFx(String.valueOf(matrix), true);
    }
  }

  private void view2dArray(Object[][] matrix, String... title) {
    List<List<Object>> objList = new ArrayList<>();
    for (Object[] row : matrix) {
      objList.add(Arrays.asList(row));
    }
    List<String> header = createAnonymousHeader(matrix[0].length);
    var t = transpose(matrix);
    StringColumn[] columns = new StringColumn[t.length];
    for (int i = 0; i < columns.length; i++) {
      columns[i] = StringColumn.create(header.get(i), Arrays.stream(t[i]).map(String::valueOf).toArray(String[]::new));
    }
    Table table = Table.create().addColumns(columns);
    viewTable(table, title);
  }

  @NotNull
  private List<String> createAnonymousHeader(int size) {
    List<String> header = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      header.add("V" + i);
    }
    return header;
  }

  public void viewHtml(String html, String... title) {
    Platform.runLater(() -> {
      viewer.viewHtml(html, title);
      gui.getInoutComponent().getSelectionModel().select(viewer);
    });
  }

  public void viewHtmlWithBootstrap(String html, String... title) {
    Platform.runLater(() -> {
      viewer.viewHtmlWithBootstrap(html, title);
      getSelectionModel().select(viewer);
    });
  }

  public void viewTable(Table table, String... title) {
    Platform.runLater(() -> {
          viewer.viewTable(table, title);
          SingleSelectionModel<Tab> selectionModel = getSelectionModel();
          selectionModel.select(viewer);
        }
    );
  }

  public void setPackages(List<String> pkgs) {
   packages.setLoadedPackages(pkgs);
  }

  public TreeItem<FileItem> getRoot() {
    return fileTree.getRoot();
  }

  public Git getGit() {
    return fileTree.getGit();
  }

  public Label getBranchLabel() {
    return branchLabel;
  }

  public void setStatus(String status) {
    Platform.runLater(() -> statusField.setText(status));
  }

  public void clearStatus() {
    setStatus("");
  }

  public void setEnableGit(boolean enableGit) {
    boolean doRefresh = this.enableGit != enableGit;
    this.enableGit = enableGit;

    if (!enableGit) {
      branchLabel.setText("");
      statusField.setText("");
    }

    if (doRefresh) {
      refreshFileTree();
    }
  }

  public boolean isGitEnabled() {
    return enableGit;
  }

  public void cloneGitRepo(String url, File targetDir) throws GitAPIException {
    Git.cloneRepository()
        .setURI(url)
        .setDirectory(targetDir)
        .call();
    changeRootDir(targetDir);
  }

  public boolean hasPomFile() {
    return projectDir() != null && new File(projectDir(), "pom.xml").exists();
  }


  public PlotsTab getPlotsTab() {
    return plotsTab;
  }
}
