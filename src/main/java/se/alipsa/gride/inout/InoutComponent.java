package se.alipsa.gride.inout;

import static se.alipsa.gride.menu.GlobalOptions.ENABLE_GIT;
import static se.alipsa.gride.menu.GlobalOptions.USE_MAVEN_CLASSLOADER;
import static se.alipsa.gride.utils.FileUtils.baseName;
import static se.alipsa.gride.utils.FileUtils.removeExt;
import static se.alipsa.gride.utils.TableUtils.transpose;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jetbrains.annotations.NotNull;
import se.alipsa.gride.Gride;
import se.alipsa.gride.chart.Chart;
import se.alipsa.gride.chart.Plot;
import se.alipsa.gride.console.ConsoleTextArea;
import se.alipsa.gride.environment.connections.ConnectionInfo;
import se.alipsa.gride.inout.plot.PlotsTab;
import se.alipsa.gride.inout.viewer.ViewTab;
import se.alipsa.gride.interaction.GuiInteraction;
import se.alipsa.gride.utils.*;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Page;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class InoutComponent extends TabPane implements InOut, GuiInteraction {

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
    File rootDir = gui.getInoutComponent().getRootDir();
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
    if (!dir.equals(getRootDir())) {
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

  public File getRootDir() {
    return fileTree.getRootDir();
  }

  public void plot(Chart chart, String... titleOpt) {
    String title = titleOpt.length > 0 ? titleOpt[0] : removeExt(gui.getCodeComponent().getActiveScriptName());
    display(Plot.jfx(chart), title);
  }

  public void plot(Figure figure, String... titleOpt) {
    String title = titleOpt.length > 0 ? titleOpt[0] : removeExt(gui.getCodeComponent().getActiveScriptName());
    Page page = Page.pageBuilder(figure, "target").build();
    String output = page.asJavascript();
    //viewHtml(output, title);
    Platform.runLater(() -> {
      WebView webView = new WebView();
      webView.getEngine().setJavaScriptEnabled(true);
      webView.getEngine().loadContent(output);
      display(webView, title);
    });
  }


  public void display(Node node, String... title) {
    Platform.runLater(() -> {
          plotsTab.showPlot(node, title);
          SingleSelectionModel<Tab> selectionModel = getSelectionModel();
          selectionModel.select(plotsTab);
        }
    );
  }

  public void display(Image img, String... title) {
    ImageView node = new ImageView(img);
    display(node, title);
  }

  public void display(String fileName, String... title) {
    URL url = FileUtils.getResourceUrl(fileName);
    log.info("Reading image from " + url);
    if (url == null) {
      Alerts.warn("Cannot display image", "Failed to find " + fileName);
      return;
    }
    File file = new File(fileName);
    if (file.exists()) {
      try {
        String contentType = TikaUtils.instance().detectContentType(file);
        if ("image/svg+xml".equals(contentType)) {
          Platform.runLater(() -> {
            final WebView browser = new WebView();
            browser.getEngine().load(url.toExternalForm());
            display(browser, title);
          });
          return;
        }
      } catch (IOException e) {
        ExceptionAlert.showAlert("Failed to detect image content type", e);
      }
    }
    Image img = new Image(url.toExternalForm());
    display(img, title);
  }

  public void view(Table table, String... title) {
    String tit = title.length > 0 ? title[0] : table.name();
    if (tit == null) {
      tit = gui.getCodeComponent().getActiveScriptName();
      int extIdx = tit.lastIndexOf('.');
      if (extIdx > 0) {
        tit = tit.substring(0, extIdx);
      }
    }
    showInViewer(table, tit);
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
    showInViewer(table, title);
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
    showInViewer(table, title);
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
      getSelectionModel().select(viewer);
    });
  }

  public void viewHtmlWithBootstrap(String html, String... title) {
    Platform.runLater(() -> {
      viewer.viewHtmlWithBootstrap(html, title);
      getSelectionModel().select(viewer);
    });
  }

  public void viewer(String html, String... title) {
    Platform.runLater(() -> {
      viewer.viewer(html, title);
      getSelectionModel().select(viewer);
    });
  }

  @Override
  public File projectDir() {
    return fileTree.getRootDir();
  }

  @Override
  public File scriptDir() {
    File file = gui.getCodeComponent().getActiveTab().getFile();
    if (file == null) {
      return projectDir();
    }
    return file.getParentFile();
  }
  /**
   * As this is called from the script engine which runs on a separate thread
   * any gui interaction must be performed in a Platform.runLater (not sure if this qualifies as gui interaction though)
   * TODO: If the error is not printed after extensive testing then remove the catch IllegalStateException block
   *
   * @return the file from the active tab or null if the active tab has never been saved
   */
  @Override
  public File scriptFile() {

    try {
      return gui.getCodeComponent().getActiveTab().getFile();
    } catch (IllegalStateException e) {
      log.info("Not on javafx thread", e);
      final FutureTask<File> query = new FutureTask<>(() -> gui.getCodeComponent().getActiveTab().getFile());
      Platform.runLater(query);
      try {
        return query.get();
      } catch (InterruptedException | ExecutionException e1) {
        Platform.runLater(() -> ExceptionAlert.showAlert("Failed to get file from active tab", e1));
        return null;
      }
    }
  }

  @Override
  public ConnectionInfo connection(String name) {
    return gui.getEnvironmentComponent().getConnections().stream()
        .filter(ci -> ci.getName().equals(name)).findAny().orElse(null);
  }

  public Stage getStage() {
    return gui.getStage();
  }

  public void showInViewer(Table table, String... title) {
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

  @Override
  public String toString() {
    return "The Gride InOutComponent";
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
    return getRootDir() != null && new File(getRootDir(), "pom.xml").exists();
  }

  @Override
  public String help() {
    return """
        ConnectionInfo connection(String name)
          Return a connections for the name defined in Gride.
                
        File scriptFile()
          return the file from the active tab or null if the active tab has never been saved
                
        File scriptDir()
          return the dir where the current script resides
          or the project dir if the active tab has never been saved
                
        File projectDir()
          return the project dir (the root of the file tree)
                
        void plot(Chart chart, String... titleOpt)
          Show the chart in the plots tab
        
        void plot(Figure figure, String... titleOpt)
          Show the figure in the plots tab
        """;
  }
}
