package se.alipsa.gade.inout;

import static se.alipsa.gade.Constants.GitStatus.GIT_ADDED;
import static se.alipsa.gade.Constants.GitStatus.GIT_CHANGED;
import static se.alipsa.gade.Constants.GitStatus.GIT_CONFLICT;
import static se.alipsa.gade.Constants.GitStatus.GIT_IGNORED;
import static se.alipsa.gade.Constants.GitStatus.GIT_MODIFIED;
import static se.alipsa.gade.Constants.GitStatus.GIT_UNCOMITTED_CHANGE;
import static se.alipsa.gade.Constants.GitStatus.GIT_UNTRACKED;
import static se.alipsa.gade.Constants.KEY_CODE_COPY;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeItem.TreeModificationEvent;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.CodeComponent;
import se.alipsa.gade.code.TextAreaTab;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.gade.utils.git.GitUtils;

import java.io.File;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;

public class FileTree extends TreeView<FileItem> {

  private final String folderUrl = Objects.requireNonNull(FileUtils.getResourceUrl("image/folder.png")).toExternalForm();
  private final String fileUrl = Objects.requireNonNull(FileUtils.getResourceUrl("image/file.png")).toExternalForm();
  private final String WORKING_DIR_PREF = "FileTree.WorkingDir";
  private final TreeItemComparator treeItemComparator = new TreeItemComparator();
  private final Gade gui;
  private static final Logger log = LogManager.getLogger(FileTree.class);
  private final FileOpener fileOpener;
  private DynamicContextMenu menu;
  private Git git;
  private final InoutComponent inoutComponent;

  FileTree(Gade gui, InoutComponent inoutComponent) {
    this.gui = gui;
    CodeComponent codeComponent = gui.getCodeComponent();
    this.inoutComponent = inoutComponent;
    fileOpener = new FileOpener(codeComponent);
    this.getStyleClass().add("fileTree");

    File current = new File(getWorkingDirPref());
    boolean workDirExist = current.exists();
    if (!workDirExist) {
      if (current.getParentFile().exists()) {
        current = current.getParentFile();
      } else {
        current = new File(".");
      }

      //log.warn("{} does not exist, setting working dir to {}", prefPath, current.getAbsolutePath());
      log.warn("{} does not exist, no working dir set", current.getAbsolutePath());
    }
    if(workDirExist) {
      log.info("Setting working dir to {}", current);
      setWorkingDir(current);
      setRoot(createTree(current));
      if (inoutComponent.isGitEnabled()) {
        gitColorTree(getRoot());
      }
      sortTree(getRoot());
      getRoot().setExpanded(true);
    }
    setCellFactory(treeView -> new TreeCell<>() {

      @Override
      protected void updateItem(FileItem item, boolean empty) {
        if (item != null) {
          setText(item.getText());
          setStyle(item.getStyle());
          setGraphic(getTreeItem().getGraphic());
          if (item.getFile().equals(getRoot().getValue().getFile())) {
            setTooltip(new Tooltip(item.getFile().getAbsolutePath()));
          }
        } else {
          setText("");
          setGraphic(null);
        }
        super.updateItem(item, empty);
      }
    });

    setOnKeyPressed(event -> {
      if (KEY_CODE_COPY.match(event)) {
        copySelectionToClipboard();
      }
    });

    setOnMouseClicked(this::handleClick);
    menu = new DynamicContextMenu(this, gui, inoutComponent);
    //setContextMenu(createContextMenu());
    addEventHandler(MouseEvent.MOUSE_RELEASED, e->{
      if (e.getButton() == MouseButton.SECONDARY) {
        TreeItem<FileItem> selected = getSelectionModel().getSelectedItem();
        //item is selected - this prevents fail when clicking on empty space
        if (selected != null) {
          //open context menu on current screen position
          openContextMenu(selected, e.getScreenX(), e.getScreenY());
        }
      } else {
        //any other click cause hiding menu
        menu.hide();
      }
    });
  }

  private void openContextMenu(TreeItem<FileItem> item, double x, double y) {
    menu.setContext(item);
    if (item.getValue().getFile().isFile()) {
      menu.hideDirItems();
    } else {
      menu.hideFileItems();
    }
    menu.show(this, x, y);
  }


  File getRootDir() {
    if (getRoot() == null) {
      return null;
    }
    return getRoot().getValue().getFile();
  }

  private String getWorkingDirPref() {
    return gui.getPrefs().get(WORKING_DIR_PREF, ".");
  }

  private void setWorkingDirPref(File dir) {
    gui.getPrefs().put(WORKING_DIR_PREF, dir.getAbsolutePath());
    setWorkingDir(dir);
  }

  private void setWorkingDir(File dir) {
    gui.getConsoleComponent().setWorkingDir(dir);
    System.setProperty("user.dir", dir.getAbsolutePath());
    Platform.runLater(() -> gui.setTitle(dir.getName()));
  }

  private TreeItem<FileItem> createTree(File file) {
    TreeItem<FileItem> item = new TreeItem<>(new FileItem(file));
    File[] children = file.listFiles();
    if (children != null) {
      for (File child : children) {
        item.getChildren().add(createTree(child));
      }
      item.setGraphic(new ImageView(folderUrl));
    } else {
      setLeafProperties(item);
    }
    return item;
  }

  private void gitColorTree(TreeItem<FileItem> root) {
    File rootDir = root.getValue().getFile();
    if (rootDir != null && rootDir.exists() && Objects.requireNonNull(rootDir.list((dir, name) -> name.equalsIgnoreCase(".git"))).length > 0) {
      log.debug("adding git coloring...");
    } else {
      log.debug("not a git repository, skipping git coloring");
      return;
    }
    Platform.runLater(() -> {
      try {
        git = Git.open(rootDir);
        String branch = git.getRepository().getBranch();
        inoutComponent.getBranchLabel().setText("Branch: " + branch);
        Status status;
        try {
          status = git.status().call();
        } catch (GitAPIException e) {
          ExceptionAlert.showAlert("Failed to get git status", e);
          return;
        }
        walkAndColor(getRoot(), status);
      } catch (Exception e) {
        log.error("Failed to set git colors", e);
        ExceptionAlert.showAlert("Failed to set git colors", e);
      }
    });
  }

  private void walkAndColor(TreeItem<FileItem> node,  Status status) {
      for(TreeItem<FileItem> child: node.getChildren()){

        FileItem item = child.getValue();
        File file = item.getFile();
        String path = GitUtils.asRelativePath(file, getRootDir());

        if(status.getConflicting().contains(path)) {
          item.setStyle(GIT_CONFLICT.getStyle());
          continue;
        }
        if(status.getAdded().contains(path)) {
          item.setStyle(GIT_ADDED.getStyle());
          continue;
        }
        if(status.getChanged().contains(path)) {
          item.setStyle(GIT_CHANGED.getStyle());
          continue;
        }
        if(status.getModified().contains(path)) {
          item.setStyle(GIT_MODIFIED.getStyle());
          continue;
        }
        if(status.getUncommittedChanges().contains(path)) {
          item.setStyle(GIT_UNCOMITTED_CHANGE.getStyle());
          break;
        }
        if(status.getUntracked().contains(path)) {
          item.setStyle(GIT_UNTRACKED.getStyle());
          continue;
        }
        if(status.getIgnoredNotInIndex().contains(path)) {
          item.setStyle(GIT_IGNORED.getStyle());
          continue;
        }
        walkAndColor(child, status);
      }
  }

  private void setLeafProperties(TreeItem<FileItem> item) {
    item.setGraphic(new ImageView(fileUrl));
    ChangeListener<String> fillListener = (obs, oldName, newName) -> {
      TreeModificationEvent<FileItem> event = new TreeModificationEvent<>(TreeItem.valueChangedEvent(), item);
      //log.info("item {} changed color", item.getValue());
      Event.fireEvent(item, event);
    };
    FileItem fileItem = item.getValue();
    fileItem.addListener(fillListener);
  }

  private void handleClick(MouseEvent event) {
    if (event.getClickCount() == 2) {
      TreeItem<FileItem> item = getSelectionModel().getSelectedItem();
      if (item == null) {
        return;
      }
      File file = item.getValue().getFile();
      if (file.isDirectory()) {
        return;
      }
      TextAreaTab tab = gui.getCodeComponent().getTab(file);
      if (tab == null) {
        tab = openFileTab(file);
        if (tab != null) {
          tab.setTreeItem(item);
        }
      }
      gui.getCodeComponent().activateTab(tab);
    }
  }

  public TextAreaTab openFileTab(File file) {
    return fileOpener.openFile(file);
  }

  public void expandAllChildren(TreeItem<?> item){
    if(item != null && !item.isLeaf()){
      item.setExpanded(true);
      for(TreeItem<?> child:item.getChildren()){
        expandAllChildren(child);
      }
    }
  }

  TreeItem<FileItem> addTreeNode(File file) {
    TreeItem<FileItem> item = findTreeViewItem(this.getRoot(), file.getParentFile());
    if (item == null) {
      log.info("File saved outside of current working dir");
      return null;
    }
    TreeItem<FileItem> fileItem = new TreeItem<>(new FileItem(file));
    addTreeNode(item, fileItem);
    return fileItem;
  }

  private void addTreeNode(TreeItem<FileItem> dirItem, TreeItem<FileItem> fileItem) {
    if (fileItem.getValue().getFile().isDirectory()) {
      fileItem.setGraphic(new ImageView(folderUrl));
    } else {
      setLeafProperties(fileItem);
    }
    dirItem.getChildren().add(fileItem);
    dirItem.getChildren().sort(treeItemComparator);
    dirItem.setExpanded(true);
  }

  void refresh(File dir) {
    if (dir == null) {
      Alerts.warn("Dir is missing (null)", "Cannot refresh file tree when dir specified is missing");
      return;
    }
    if (dir.isFile()) {
      dir = dir.getParentFile();
    }
    setRoot(createTree(dir));
    sortTree(getRoot());
    getRoot().setExpanded(true);
    if (inoutComponent.isGitEnabled()) {
      gitColorTree(getRoot());
    }
    setWorkingDirPref(dir);
    menu = new DynamicContextMenu(this, gui, inoutComponent);
  }

  public void refresh() {
    File current = getRoot().getValue().getFile();
    setRoot(createTree(current));
    sortTree(getRoot());
    getRoot().setExpanded(true);
    if (inoutComponent.isGitEnabled()) {
      gitColorTree(getRoot());
    }
    menu = new DynamicContextMenu(this, gui, inoutComponent);
  }

  private TreeItem<FileItem> findTreeViewItem(TreeItem<FileItem> item, File value) {
    if (item != null && item.getValue().getFile().equals(value)) {
      return item;
    }
    if (item != null) {
      for (TreeItem<FileItem> child : item.getChildren()) {
        TreeItem<FileItem> s = findTreeViewItem(child, value);
        if (s != null) {
          return s;
        }
      }
    }
    return null;
  }

  private void sortTree(TreeItem<FileItem> item) {
    ObservableList<TreeItem<FileItem>> children = item.getChildren();
    children.sort(treeItemComparator);
    for (TreeItem<FileItem> child : item.getChildren()) {
      sortTree(child);
    }
  }

  private static class TreeItemComparator implements Comparator<TreeItem<FileItem>>, Serializable {

    private static final long serialVersionUID = -7749561517249799967L;

    @Override
    public int compare(TreeItem<FileItem> fileTreeItem, TreeItem<FileItem> t1) {
      return fileTreeItem.getValue().getFile().getName().compareTo(t1.getValue().getFile().getName());
    }
  }

  void copySelectionToClipboard() {
    TreeItem<FileItem> treeItem = getSelectionModel().getSelectedItem();
    final ClipboardContent clipboardContent = new ClipboardContent();
    String value = treeItem.getValue().getFile().getName();
    clipboardContent.putString(value);
    Clipboard.getSystemClipboard().setContent(clipboardContent);
  }

  public Git getGit() {
    return git;
  }
}
