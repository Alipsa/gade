package se.alipsa.gade.inout;

import static se.alipsa.gade.menu.GlobalOptions.*;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
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
import se.alipsa.gade.Gade;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.inout.plot.PlotsTab;
import se.alipsa.gade.inout.viewer.ViewTab;
import se.alipsa.gade.utils.*;
import se.alipsa.matrix.core.Matrix;

import java.io.File;
import java.util.*;

public class InoutComponent extends TabPane  {

  private final FileTree fileTree;
  private final PlotsTab plotsTab;
  private final PackagesTab packages;
  private final ViewTab viewer;
  private final HelpTab helpTab;
  private final Gade gui;
  private final Label branchLabel;
  private final TextField statusField;
  private boolean enableGit;

  private MutableDataSet flexmarkOptions;
  private Parser markdownParser;
  private HtmlRenderer htmlRenderer;

  private static final Logger log = LogManager.getLogger(InoutComponent.class);

  public InoutComponent(Gade gui) {

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
      if (gui.getPrefs().getBoolean(USE_GRADLE_CLASSLOADER, false)) {
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



  public void view(List<List> matrix, String... title) {
    if (matrix == null) {
      Alerts.warnFx("View", "matrix is null, cannot View");
      return;
    }
    // Instanceof check does not work due to module restrictions
    if (matrix.getClass().getName().contains(".ListAdapter")) {
      // Due to erasure and whatever other strange reasons, in java 11 Nashorn return a List<ScriptObjectMirror> and still end up here
      Alerts.warnFx("Cannot view a ListAdapter directly", "Convert " + matrix.getClass().getName() + " to a java 2D array before viewing using Java.to(data,'java.lang.Object[][]')");
      return;
    }

    Matrix table = Matrix.builder().rows(matrix).build();

    /*
    var t = transposeAny(matrix);
    List<String> header = createAnonymousHeader(t.size());
    StringColumn[] columns = new StringColumn[t.size()];
    for (int i = 0; i < columns.length; i++) {
      columns[i] = StringColumn.create(header.get(i), t.get(i).stream().map(String::valueOf).toArray(String[]::new));
    }
    Table table = Table.create().addColumns(columns);
     */
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
    } else if (matrix instanceof Matrix tableMatrix) {
      viewTable(tableMatrix, title);
    } else {
      console.appendWarningFx("Unknown matrix type " + matrix.getClass().getName());
      console.appendFx(String.valueOf(matrix), true);
    }
  }

  private void view2dArray(Object[][] matrix, String... title) {
    List<List> objList = new ArrayList<>();
    for (Object[] row : matrix) {
      objList.add(Arrays.asList(row));
    }
    Matrix table = Matrix.builder().rows(objList).build();
    viewTable(table, title);
  }

  public void viewHtml(String html, String... title) {
    //System.out.println("viewHtml:\n" + html);
    Platform.runLater(() -> {
      try {
        viewer.viewHtml(html, title);
        gui.getInoutComponent().getSelectionModel().select(viewer);
      } catch (Throwable e) {
        ExceptionAlert.showAlert("Failed to view html", e);
      }
    });
  }

  public void viewHtmlWithBootstrap(String html, String... title) {
    Platform.runLater(() -> {
      viewer.viewHtmlWithBootstrap(html, title);
      getSelectionModel().select(viewer);
    });
  }

  public void viewHelp(String title, String text) {
    Platform.runLater(() -> {
      helpTab.showText(text, title);
      getSelectionModel().select(helpTab);
    });
  }

  public void viewTable(Matrix table, String... title) {
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

  public void viewMarkdown(String markdown, String... title) {
    viewHtml(getHtmlRenderer().render(getMarkdownParser().parse(markdown)), title);
  }

  private Parser getMarkdownParser() {
    if (markdownParser == null) {
      markdownParser = Parser.builder(getFlexmarkOptions()).build();
    }
    return markdownParser;
  }

  private HtmlRenderer getHtmlRenderer() {
    if (htmlRenderer == null) {
      htmlRenderer = HtmlRenderer.builder(getFlexmarkOptions()).build();
    }
    return htmlRenderer;
  }

  MutableDataSet getFlexmarkOptions() {
    if (flexmarkOptions == null) flexmarkOptions = new MutableDataSet();
    flexmarkOptions.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
    return flexmarkOptions;
  }


}
