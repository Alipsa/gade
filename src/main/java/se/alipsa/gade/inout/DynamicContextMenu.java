package se.alipsa.gade.inout;

import static se.alipsa.gade.Constants.REPORT_BUG;
import static se.alipsa.gade.utils.git.GitUtils.asRelativePath;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.DiffCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import se.alipsa.gade.Gade;
import se.alipsa.gade.inout.git.AddRemoteDialog;
import se.alipsa.gade.inout.git.ConfigResult;
import se.alipsa.gade.inout.git.CredentialsDialog;
import se.alipsa.gade.inout.git.GitConfigureDialog;
import se.alipsa.gade.inout.git.GitStatusDialog;
import se.alipsa.gade.utils.*;
import se.alipsa.gade.utils.git.GitUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DynamicContextMenu extends ContextMenu {

   private Git git;
   private final Gade gui;
   private final FileTree fileTree;
   private TreeItem<FileItem> currentNode;
   private File currentFile;
   private final Map<String, CredentialsProvider> credentialsProviders;

   private final MenuItem createDirMI;
   private final MenuItem createFileMI;
   private final MenuItem expandAllMI;
   private final MenuItem deleteMI;
   private MenuItem gitAddMI;
   private final MenuItem renameMI;

   MenuItem gitInitMI = new MenuItem("Initialize root as git repo");

   private Logger log = LogManager.getLogger();

   public DynamicContextMenu(FileTree fileTree, Gade gui, InoutComponent inoutComponent) {
      this.fileTree = fileTree;
      this.gui = gui;
      credentialsProviders = new HashMap<>();

      MenuItem copyMI = new MenuItem("copy name");
      copyMI.setOnAction(e -> fileTree.copySelectionToClipboard());
      getItems().add(copyMI);

      createDirMI = new MenuItem("create dir");
      createDirMI.setOnAction(e -> {
         File currentFile = currentNode.getValue().getFile();
         File newDir = promptForFile(currentFile, "Create and add dir", "Enter the dir name:");
         if (newDir == null) {
            return;
         }
         try {
            Files.createDirectory(newDir.toPath());
            fileTree.addTreeNode(newDir);
         } catch (IOException e1) {
            ExceptionAlert.showAlert("Failed to create directory", e1);
         }
      });
      getItems().add(createDirMI);

      createFileMI = new MenuItem("create file");
      createFileMI.setOnAction(e -> {
         File newFile = promptForFile(currentFile, "Create and add file", "Enter the file name:");
         if (newFile == null) {
            return;
         }
         try {
            Files.createFile(newFile.toPath());
            if (newFile.getName().endsWith(".java")) {
               addJavaContent(newFile);
            }
            TreeItem<FileItem> node = fileTree.addTreeNode(newFile);
            fileTree.openFileTab(newFile);
            GitUtils.colorNode(git, GitUtils.asRelativePath(newFile, fileTree.getRootDir()), node);
         } catch (IOException e1) {
            ExceptionAlert.showAlert("Failed to create file", e1);
         }
      });
      getItems().add(createFileMI);

      expandAllMI = new MenuItem("expand all");
      expandAllMI.setOnAction(e -> fileTree.expandAllChildren(currentNode));
      getItems().add(expandAllMI);

      renameMI = new MenuItem("rename");
      renameMI.setOnAction(e -> {
         String fileType = " file ";
         if (currentFile.isDirectory()) {
            fileType = " directory ";
         }
         TextInputDialog dialog = new TextInputDialog(currentFile.getName());
         GuiUtils.addStyle(gui, dialog);
         dialog.setTitle("Rename " + currentFile.getName());
         Optional<String> toName = dialog.showAndWait();
         if (toName.isEmpty()) {
            return;
         }

         // File (or directory) with new name
         File file2 = new File(currentFile.getParent(), toName.get());

         if (file2.exists()) {
            Alerts.warn("File already exists", "Cannot rename" + fileType + currentFile.getName() + " as " + file2.getAbsolutePath() + "already exists!");
            return;
         }

         // Rename file (or directory)
         boolean success = currentFile.renameTo(file2);

         if (!success) {
            Alerts.warn("Rename failed", "Failed to rename" + fileType + currentFile.getName() + " to " + file2.getAbsolutePath());
            return;
         }
         // If we renamed a file that is currently opened in a tab then
         // change that tab to reflect the rename
         if (file2.isFile()) {
            var activeTab = gui.getCodeComponent().getActiveTab();
            gui.getCodeComponent().getTabs().forEach(tab -> {
               if (activeTab.getFile().equals(currentFile)) {
                  activeTab.setFile(file2);
                  activeTab.setTitle(file2.getName());
               }
            });
         }
         currentFile = file2;
         log.info("Renamed file to {}", currentFile);
         currentNode.setValue(null); // need to set null first, otherwise it will not refresh
         currentNode.setValue(new FileItem(currentFile));

      });
      getItems().add(renameMI);


      deleteMI = new MenuItem("delete");
      deleteMI.setOnAction(e -> {
         String fileType = "file";
         try {
            if (currentFile.isDirectory()) {
               fileType = "directory";
            }
            Files.delete(currentFile.toPath());
            currentNode.getParent().getChildren().remove(currentNode);
         } catch (DirectoryNotEmptyException ex) {
            ExceptionAlert.showAlert("Directory is not empty, cannot delete ", ex);
         } catch (IOException ex) {
            ExceptionAlert.showAlert("Failed to delete " + fileType, ex);
         }
      });
      getItems().add(deleteMI);

      if (inoutComponent.isGitEnabled()) {
         createGitMenu(fileTree);
      }
   }

   private void createGitMenu(FileTree fileTree) {
      Menu gitMenu = new Menu("Git");

      boolean gitInitialized = false;

      if (new File(fileTree.getRootDir(), ".git").exists()) {
         try {
            git = Git.open(fileTree.getRootDir());
            gitInitMI.setVisible(false);
            gitInitialized = true;
         } catch (IOException e) {
            log.error("Failed to open git repository at {}", fileTree.getRootDir(), e);
         }
      } else {
         gitInitMI.setVisible(true);
         gitInitMI.setOnAction(this::gitInit);
         gitMenu.getItems().add(gitInitMI);
      }

      if (gitInitialized) {
         gitAddMI = new MenuItem("add");
         gitAddMI.setOnAction(this::gitAdd);
         gitMenu.getItems().add(gitAddMI);

         MenuItem gitDeleteMI = new MenuItem("delete");
         gitDeleteMI.setOnAction(this::gitRm);
         gitMenu.getItems().add(gitDeleteMI);

         MenuItem gitStatusMI = new MenuItem("status");
         gitStatusMI.setOnAction(this::gitStatus);
         gitMenu.getItems().add(gitStatusMI);

         MenuItem gitDiffMI = new MenuItem("diff");
         gitDiffMI.setOnAction(this::gitDiff);
         gitMenu.getItems().add(gitDiffMI);

         MenuItem gitResetMI = new MenuItem("reset");
         gitResetMI.setOnAction(this::gitReset);
         gitMenu.getItems().add(gitResetMI);

         // All sub menu
         Menu gitAllMenu = new Menu("All");
         gitMenu.getItems().add(gitAllMenu);

         MenuItem gitAddAllMI = new MenuItem("add all");
         gitAddAllMI.setOnAction(this::gitAddAll);
         gitAllMenu.getItems().add(gitAddAllMI);

         MenuItem gitCommitMI = new MenuItem("commit");
         gitCommitMI.setOnAction(this::gitCommit);
         gitAllMenu.getItems().add(gitCommitMI);

         MenuItem gitStatusAllMI = new MenuItem("status all");
         gitStatusAllMI.setOnAction(this::gitStatusAll);
         gitAllMenu.getItems().add(gitStatusAllMI);

         MenuItem gitLogMI = new MenuItem("show Log");
         gitLogMI.setOnAction(this::gitLog);
         gitAllMenu.getItems().add(gitLogMI);

         MenuItem gitConfigMI = new MenuItem("configure");
         gitConfigMI.setOnAction(this::gitConfig);
         gitAllMenu.getItems().add(gitConfigMI);

         // Branches sub menu
         Menu gitBranchMenu = new Menu("Branches");
         gitMenu.getItems().add(gitBranchMenu);

         MenuItem gitBranchListMI = new MenuItem("list branches");
         gitBranchListMI.setOnAction(this::gitBranchList);
         gitBranchMenu.getItems().add(gitBranchListMI);

         MenuItem gitBranchCheckoutMI = new MenuItem("checkout");
         gitBranchCheckoutMI.setOnAction(this::gitBranchCheckout);
         gitBranchMenu.getItems().add(gitBranchCheckoutMI);

         MenuItem gitBranchMergeMI = new MenuItem("merge");
         gitBranchMergeMI.setOnAction(this::gitBranchMerge);
         gitBranchMenu.getItems().add(gitBranchMergeMI);

         // Remote sub menu
         boolean haveRemote = !StringUtils.isBlank(getRemoteGitUrl());
         Menu gitRemoteMenu = new Menu("Remote");
         gitMenu.getItems().add(gitRemoteMenu);

         MenuItem gitAddRemoteMI = new MenuItem("add remote");
         gitAddRemoteMI.setOnAction(this::gitAddRemote);
         gitRemoteMenu.getItems().add(gitAddRemoteMI);

         if (haveRemote) {
            MenuItem gitListRemotesMI = new MenuItem("list remotes");
            gitListRemotesMI.setOnAction(this::gitListRemotes);
            gitRemoteMenu.getItems().add(gitListRemotesMI);

            MenuItem gitFetchMI = new MenuItem("fetch");
            gitFetchMI.setOnAction(this::gitFetch);
            gitRemoteMenu.getItems().add(gitFetchMI);

            MenuItem gitPushMI = new MenuItem("push");
            gitPushMI.setOnAction(this::gitPush);
            gitRemoteMenu.getItems().add(gitPushMI);

            MenuItem gitPullMI = new MenuItem("pull");
            gitPullMI.setOnAction(this::gitPull);
            gitRemoteMenu.getItems().add(gitPullMI);
         }

      }
      getItems().add(gitMenu);
   }

   private void addJavaContent(File newFile) {
      if (newFile == null) {
         return;
      }
      String absolutePath = newFile.getAbsolutePath();
      String fileName = newFile.getName();
      int fromIndex = absolutePath.lastIndexOf("java/") + 5;
      if (fromIndex == 4) {
         fromIndex = absolutePath.lastIndexOf("src/") + 3;
      }
      if (fromIndex == 2) {
         fromIndex = absolutePath.indexOf(GitUtils.asRelativePath(newFile, fileTree.getRootDir()));
      }
      String packageName = absolutePath.substring(fromIndex, absolutePath.lastIndexOf(fileName)-1)
         .replace('/', '.');

      StringBuilder str = new StringBuilder("package ").append(packageName).append(";\n\n")
         .append("public class ").append(fileName, 0, fileName.indexOf(".java")).append(" {\n\n}");
      try {
         FileUtils.writeToFile(newFile, str.toString());
      } catch (FileNotFoundException e) {
         log.warn("Failed to create java boilerplate content", e);
      }
   }

   private void gitBranchMerge(ActionEvent actionEvent) {
      try {
         if (!git.status().call().isClean()) {
            Alerts.info("Repository is not clean",
               "You have uncommitted files that must be committed before you can merge");
            return;
         }
      } catch (GitAPIException e) {
         log.warn("Failed to check status before merging with another branch", e);
         ExceptionAlert.showAlert("Failed to check status before merging with another branch", e);
         return;
      }
      String currentBranch;
      try {
         currentBranch = git.getRepository().getBranch();
      } catch (IOException e) {
         log.warn("Failed to get current branch", e);
         ExceptionAlert.showAlert("Failed to get current branch", e);
         return;
      }

      TextInputDialog dialog = new TextInputDialog("");
      dialog.setTitle("Merge branch");
      dialog.setHeaderText("Merge another branch into current branch (" + currentBranch + ")");
      dialog.setContentText("Branch to merge from:");
      GuiUtils.addStyle(gui, dialog);

      Optional<String> result = dialog.showAndWait();
      if (result.isPresent()) {
         String branchName = result.get();
         try {
            // retrieve the objectId of the latest commit on branch
            ObjectId latestCommit = git.getRepository().resolve(branchName);
            MergeResult mergeResult = git.merge().include(latestCommit).call();
            if (mergeResult.getMergeStatus().equals(MergeResult.MergeStatus.CONFLICTING)) {
               StringBuilder str = new StringBuilder();
               mergeResult.getConflicts().forEach((k, v) -> str.append(k).append(": ").append(Arrays.deepToString(v)).append("\n"));
               Alerts.warn("Merge Conflicts detected", str.toString());
            } else if (mergeResult.getMergeStatus().isSuccessful()) {
               StringBuilder mergeContent = new StringBuilder("The following commits was merged:\n");
               for (ObjectId objectId : mergeResult.getMergedCommits()) {
                  mergeContent.append(objectId).append("\n");
               }
               Alerts.info("Merge success", mergeContent.toString());
            } else {
               StringBuilder str = new StringBuilder();
               mergeResult.getFailingPaths().forEach((k, v) -> str.append(k).append(": ").append(v.toString()).append("\n"));
               Alerts.warn("Merge failed", str.toString());
            }
            fileTree.refresh();
         } catch (Exception e) {
            log.warn("Failed to merge branch", e);
            ExceptionAlert.showAlert("Failed to merge branch", e);
         }
      }
   }

   private void gitBranchCheckout(ActionEvent actionEvent) {
      try {
         if (!git.status().call().isClean()) {
            Alerts.info("Repository is not clean",
               "You have uncommitted files that must be committed before you can checkout");
            return;
         }
      } catch (GitAPIException e) {
         log.warn("Failed to check status before checkout branch", e);
         ExceptionAlert.showAlert("Failed to check status before checkout branch", e);
      }
      TextInputDialog dialog = new TextInputDialog("");
      dialog.setTitle("Checkout branch");
      dialog.setHeaderText("Checkout branch");
      dialog.setContentText("Branch name:");
      GuiUtils.addStyle(gui, dialog);

      Optional<String> result = dialog.showAndWait();
      if (result.isPresent()) {
         String branchName = result.get();
         try {
            List<Ref> branchList = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
            Ref branchExists = branchList.stream().filter(p -> p.getName().replace("refs/heads/", "").equals(branchName))
               .findAny().orElse(null);
            boolean createBranch = branchExists == null;
            git.checkout()
               .setCreateBranch(createBranch)
               .setName(branchName).call();
            fileTree.refresh();
         } catch (GitAPIException e) {
            log.warn("Failed to checkout branch", e);
            ExceptionAlert.showAlert("Failed to checkout branch", e);
         }
      }
   }

   private void gitBranchList(ActionEvent actionEvent) {
      try {
         List<Ref> branchRefs = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
         StringBuilder str = new StringBuilder();
         for (Ref ref : branchRefs) {
            str.append(ref.getName().replace("refs/heads/", "")).append(": ").append(ref.getObjectId().name()).append("\n");
         }
         Alerts.info("Branches", str.toString());
      } catch (GitAPIException e) {
         log.warn("Failed to get branches", e);
         ExceptionAlert.showAlert("Failed to get branches", e);
      }
   }

   private void gitLog(ActionEvent actionEvent) {
      try {
         Iterable<RevCommit> log = git.log().call();
         StringBuilder str = new StringBuilder();
         for (RevCommit rc : log) {
            str.append(LocalDateTime.ofEpochSecond(rc.getCommitTime(), 0, ZoneOffset.UTC))
               .append(", ")
               .append(rc)
               .append(": ").append(rc.getFullMessage())
               .append("\n");
         }
         Alerts.info("Git log", str.toString());
      } catch (GitAPIException e) {
         log.warn("Failed to get log", e);
         ExceptionAlert.showAlert("Failed to get log", e);
      }
   }

   private void gitReset(ActionEvent actionEvent) {
      try {
         git.reset().addPath(getRelativePath()).call();
         fileTree.refresh();
      } catch (GitAPIException e) {
         log.warn("Failed to reset", e);
         ExceptionAlert.showAlert("Failed to reset", e);
      }
   }

   private void gitAddAll(ActionEvent actionEvent) {
      try {
         StatusCommand statusCommand = git.status();
         Status status = statusCommand.call();
         for (String path : status.getUncommittedChanges()) {
            git.add().addFilepattern(path).call();
            log.info("Adding changed file " + path);
         }
         for (String path : status.getUntracked()) {
            git.add().addFilepattern(path).call();
            log.info("Adding untracked file " + path);
         }
         fileTree.refresh();
      } catch (GitAPIException e) {
         log.warn("Failed to add all", e);
         ExceptionAlert.showAlert("Failed to add all", e);
      }
   }

   private void gitDiff(ActionEvent actionEvent) {
      try (StringWriter writer = new StringWriter();
           OutputStream out = WriterOutputStream.builder()
               .setCharset(StandardCharsets.UTF_8).setWriter(writer).get()) {

         DiffCommand diffCommand = git.diff();
         diffCommand.setOutputStream(out);
         String path = getRelativePath();
         diffCommand.setPathFilter(PathFilter.create(path));
         List<DiffEntry> diffs = diffCommand.call();
         StringBuilder str = new StringBuilder();
         if (diffs.size() > 0) {
            diffs.forEach(e -> str.append(e.toString()).append("\n"));
            str.append(writer);
         } else {
            str.append("No differences detected for ").append(path);
         }
         Alerts.info("Diff against repo for " + path, str.toString());
      } catch (GitAPIException | IOException e) {
         log.warn("Failed to diff", e);
         ExceptionAlert.showAlert("Failed to execute diff", e);
      }
   }

   private String getRelativePath() {
      return asRelativePath(currentFile, fileTree.getRootDir());
   }

   private void gitStatus(ActionEvent actionEvent) {
      try {
         StatusCommand statusCommand = git.status();
         String path = getRelativePath();
         statusCommand.addPath(path);
         Status status = statusCommand.call();
         GitStatusDialog statusDialog = new GitStatusDialog(status, path);
         statusDialog.show();
      } catch (GitAPIException e) {
         log.warn("Failed to get status", e);
         ExceptionAlert.showAlert("Failed to get status", e);
      }
   }

   private void gitStatusAll(ActionEvent actionEvent) {
      try {
         StatusCommand statusCommand = git.status();
         Status status = statusCommand.call();
         StringBuilder str = new StringBuilder("<h2>Git Status</h2>");

         final String delim = "<br />";

         Set<String> added = status.getAdded();
         if (added.size() > 0) {
            str.append("<h3>Added:</h3> ").append(String.join(delim, added)).append("<br/>");
         }
         Set<String> changed = status.getChanged();
         if (changed.size() > 0) {
            str.append("<h3>Changed:</h3> ").append(String.join(delim, changed)).append("<br/>");
         }
         Set<String> conflicting = status.getConflicting();
         if (conflicting.size() > 0) {
            str.append("<h3>Conflicting:</h3> ").append(String.join(delim, conflicting)).append("<br/>");
         }
         Set<String> missing = status.getMissing();
         if (missing.size() > 0) {
            str.append("<h3>Missing:</h3> ").append(String.join(delim, missing)).append("<br/>");
         }
         Set<String> modified = status.getModified();
         if (modified.size() > 0) {
            str.append("<h3>Modified:</h3> ").append(String.join(delim, modified)).append("<br/>");
         }
         Set<String> removed = status.getRemoved();
         if (removed.size() > 0) {
            str.append("<h3>Removed:</h3> ").append(String.join(delim, removed)).append("<br/>");
         }
         Set<String> uncomittedChanges = status.getUncommittedChanges();
         if (uncomittedChanges.size() > 0) {
            str.append("<h3>Uncommited changes:</h3> ").append(String.join(delim, uncomittedChanges)).append("<br/>");
         }
         Set<String> untracked = status.getUntracked();
         if (untracked.size() > 0) {
            str.append("<h3>Untracked:</h3> ").append(String.join(delim, untracked)).append("<br/>");
         }
         str.append("<h3>hasUncommittedChanges:</h3> ").append(status.hasUncommittedChanges()).append("<br/>");
         str.append("<h3>isClean:</h3> ").append(status.isClean()).append("<br/>");
         Alerts.infoStyled("Status", str.toString());
      } catch (GitAPIException e) {
         log.warn("Failed to get status", e);
         ExceptionAlert.showAlert("Failed to get status", e);
      }
   }

   private void gitFetch(ActionEvent actionEvent) {
      gui.setWaitCursor();
      String url = getRemoteGitUrl();
      gui.getInoutComponent().setStatus("Fetching from " + url);

      Task<FetchResult> task = new Task<>() {
         @Override
         public FetchResult call() throws Exception {
            try {
               return GitUtils.fetch(fileTree.getRootDir());
            } catch (RuntimeException e) {
               // RuntimeExceptions (such as EvalExceptions is not caught so need to wrap all in an exception
               // this way we can get to the original one by extracting the cause from the thrown exception
               System.out.println("Exception caught, rethrowing as wrapped Exception");
               throw new Exception(e);
            }
         }
      };

      task.setOnSucceeded(e -> {
         gui.setNormalCursor();
         FetchResult result = task.getValue();
         Alerts.info("Git fetch", result.toString());
         gui.getInoutComponent().clearStatus();
      });
      task.setOnFailed(e -> {
         gui.setNormalCursor();

         Throwable throwable = task.getException();
         Throwable ex = throwable.getCause();
         if (ex == null) {
            ex = throwable;
         }
         if (ex instanceof TransportException || ex instanceof org.eclipse.jgit.errors.TransportException) {
            handleTransportException(ex, "fetch");
         } else {
            log.warn("Failed to fetch", ex);
            ExceptionAlert.showAlert("Failed to fetch", ex);
         }
         gui.getInoutComponent().clearStatus();
      });
      Thread runningThread = new Thread(task);
      runningThread.setDaemon(false);
      runningThread.start();
   }

   private void gitPull(ActionEvent actionEvent) {
      gui.setWaitCursor();
      String url = getRemoteGitUrl();
      gui.getInoutComponent().setStatus("Pulling from " + url);

      Task<PullResult> task = new Task<>() {
         @Override
         public PullResult call() throws Exception {
            try {
               PullResult pullResult = git.pull()
                   .setCredentialsProvider(getCredentialsProvider(url))
                   .call();
               log.info(pullResult.toString());
               return pullResult;
            } catch (RuntimeException e) {
               // RuntimeExceptions (such as EvalExceptions is not caught so need to wrap all in an exception
               // this way we can get to the original one by extracting the cause from the thrown exception
               System.out.println("Exception caught, rethrowing as wrapped Exception");
               throw new Exception(e);
            }
         }
      };

      task.setOnSucceeded(e -> {
         gui.setNormalCursor();
         PullResult pullResult = task.getValue();
         Alerts.info("Git pull", pullResult.toString());
         gui.getInoutComponent().clearStatus();
      });
      task.setOnFailed(e -> {
         gui.setNormalCursor();

         Throwable throwable = task.getException();
         Throwable ex = throwable.getCause();
         if (ex == null) {
            ex = throwable;
         }
         if (ex instanceof TransportException || ex instanceof org.eclipse.jgit.errors.TransportException) {
            handleTransportException(ex, "pull");
         } else {
            log.warn("Failed to pull", ex);
            ExceptionAlert.showAlert("Failed to pull", ex);
         }
         gui.getInoutComponent().clearStatus();
      });
      Thread runningThread = new Thread(task);
      runningThread.setDaemon(false);
      runningThread.start();
   }

   private CredentialsProvider getCredentialsProvider(String url) throws IOException, URISyntaxException {
      CredentialsProvider credentialsProvider = credentialsProviders.get(url);
      if (credentialsProvider == null) {
         credentialsProvider = GitUtils.getStoredCredentials(url);
      }
      if (credentialsProvider == null) {
         File gitCredentials = GitUtils.getCredentialsFile();
         if (gitCredentials.exists()) {
            Alerts.warnFx("No credentials provider found", "add " + url + " to " + gitCredentials);
         } else {
            Alerts.warnFx("No credentials provider found", gitCredentials + " is missing");
         }
      }
      return credentialsProvider;
   }

   private void gitListRemotes(ActionEvent actionEvent) {
      try {
         List<RemoteConfig> remoteConfigs = git.remoteList().call();
         StringBuilder sb = new StringBuilder();
         for (RemoteConfig rc : remoteConfigs) {
            for (URIish uri : rc.getURIs()) {
               sb.append(rc.getName()).append("  ").append(uri).append("  ").append("(fetch and push)\n");
            }
            for (URIish uri : rc.getPushURIs()) {
               sb.append(rc.getName()).append("  ").append(uri).append("  ").append("(push-only)\n");
            }
         }
         if (sb.length() == 0) {
            sb.append("No remotes found");
         }
         Alerts.info("Git remotes", sb.toString());
      } catch (GitAPIException e) {
         ExceptionAlert.showAlert("Failed to get list of remotes", e);
      }
   }

   private void gitAddRemote(ActionEvent actionEvent) {
      AddRemoteDialog ard = new AddRemoteDialog();
      Optional<Map<AddRemoteDialog.KEY, String>> result = ard.showAndWait();
      if (result.isPresent()) {
         String name = result.get().get(AddRemoteDialog.KEY.NAME);
         URIish uri;
         try {
            uri = new URIish(result.get().get(AddRemoteDialog.KEY.URI));
         } catch (URISyntaxException e) {
            ExceptionAlert.showAlert("Invalid uri", e);
            return;
         }
         try {
            git.remoteAdd()
               .setName(name)
               .setUri(uri).call();
         } catch (GitAPIException e) {
            log.warn("Failed to add remote", e);
            ExceptionAlert.showAlert("Failed to add remote", e);
         }
      }
   }

   private void gitPush(ActionEvent actionEvent) {
      gui.setWaitCursor();
      String url = getRemoteGitUrl();
      gui.getInoutComponent().setStatus("Pushing to " + url);
      Task<StringBuilder> task = new Task<>() {
         @Override
         public StringBuilder call() throws Exception {
            try {
               Iterable<PushResult> result = git.push().setCredentialsProvider(getCredentialsProvider(url)).call();
               log.info("Git push was successful: {}", result);
               StringBuilder str = new StringBuilder();
               for (PushResult pushResult : result) {
                  pushResult.getRemoteUpdates().forEach(u ->
                      str.append(u.toString()).append("\n"));
               }
               return str;
            } catch (RuntimeException e) {
               // RuntimeExceptions (such as EvalExceptions is not caught so need to wrap all in an exception
               // this way we can get to the original one by extracting the cause from the thrown exception
               System.out.println("Exception caught, rethrowing as wrapped Exception");
               throw new Exception(e);
            }
         }
      };

      task.setOnSucceeded(e -> {
         gui.setNormalCursor();
         StringBuilder pushResult = task.getValue();
         gui.setNormalCursor();
         Alerts.info("Git push", "Git push was successful!\n" + pushResult.toString());
         gui.getInoutComponent().clearStatus();
      });

      task.setOnFailed(e -> {
         gui.setNormalCursor();

         Throwable throwable = task.getException();
         Throwable ex = throwable.getCause();
         if (ex == null) {
            ex = throwable;
         }
         if (ex instanceof TransportException || ex instanceof org.eclipse.jgit.errors.TransportException) {
            handleTransportException(ex, "push");
         } else {
            log.warn("Failed to push", ex);
            ExceptionAlert.showAlert("Failed to push", ex);
         }
         gui.getInoutComponent().clearStatus();
      });
      Thread runningThread = new Thread(task);
      runningThread.setDaemon(false);
      runningThread.start();
   }

   private void handleTransportException(Throwable e, String operation) {
      gui.setNormalCursor();
      log.info("Error when doing {} from/to remote: {}", operation, e.toString());
      List<Class<? extends Throwable>> causes = new ArrayList<>();
      Throwable cause = e.getCause();
      while (cause != null) {
         log.debug("Cause is {}", cause.toString());
         causes.add(cause.getClass());
         cause = cause.getCause();
      }
      String url = getRemoteGitUrl();
      if (causes.contains(javax.net.ssl.SSLHandshakeException.class)) {
         handleSslValidationProblem(e, operation);
      } else if (e.getMessage().contains("Authentication is required")) {

         CredentialsDialog credentialsDialog = new CredentialsDialog("Authentication is required");
         Optional<Map<CredentialsDialog.KEY, String>> res = credentialsDialog.showAndWait();
         if (res.isPresent()) {
            Map<CredentialsDialog.KEY, String> creds = res.get();
            String userName = creds.get(CredentialsDialog.KEY.NAME);
            String password = creds.get(CredentialsDialog.KEY.PASSWORD);
            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(userName, password);
            boolean store = Boolean.parseBoolean(creds.get(CredentialsDialog.KEY.STORE_CREDENTIALS));
            if (store) {
               try {
                  GitUtils.storeCredentials(url, userName, password);
               } catch (Exception ex) {
                  ExceptionAlert.showAlert("Failed to store credentials", ex);
               }
            } else {
               credentialsProviders.put(url, credentialsProvider);
            }
            //Alerts.info("Credentials set", "Credentials set, please try again!");
         } else {
            return;
         }
      } else if (e.getMessage().contains("not authorized")) {
         credentialsProviders.remove(url);
         try {
            GitUtils.removeCredentials(url);
         } catch (Exception ex) {
            log.warn("Failed to remove stored credentials", ex);
         }
         Alerts.warn("Incorrect credentials, all stored credentials have been reset", e.getMessage());
      }
      else {
         ExceptionAlert.showAlert("An unrecognized remote exception occurred. " + REPORT_BUG, e);
         return;
      }
      if ("push".equals(operation)) {
         gitPush(null);
      } else if ("pull".equals(operation)) {
         gitPull(null);
      } else if ("fetch".equals(operation)) {
            gitFetch(null);
      } else {
         Alerts.warn(
            "Unknown operation when calling handleTransportException",
            operation + " is an unknown operation. " + REPORT_BUG
         );
      }

   }

   private void handleSslValidationProblem(Throwable e, String operation) {
      Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
      alert.setTitle("Failed to " + operation);
      alert.setContentText(e.toString() + "\n\nDo you want to disable ssl verification?");
      alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
      Optional<ButtonType> result = alert.showAndWait();
      result.ifPresent(type -> {
         log.info("promptForDisablingSslValidation, choice was {}", result.get());
         if (ButtonType.YES == type) {
            String url = getRemoteGitUrl();
            log.info("disabling sslVerify for {}...", url);

            try {
               StoredConfig config = git.getRepository().getConfig();
               config.setBoolean("http", url, "sslVerify", false);
               config.save();
               //Alerts.info("sslVerify set to false", "OK, try again!");
            } catch (Exception ex) {
               ExceptionAlert.showAlert("Failed to save config", ex);
            }
         }
      });
   }

   private String getRemoteGitUrl() {
      return git.getRepository().getConfig().getString("remote", "origin", "url");
   }

   private void gitRm(ActionEvent actionEvent) {
      String currentPath = getRelativePath();
      log.info("Deleting {}", currentPath);
      try {
         DirCache dc = git.rm().addFilepattern(currentPath).setCached(true).call();
         // Should we do something more with dc?
         log.info("Removed {} from git dir cache", currentPath);
      } catch (GitAPIException e) {
         log.warn("Failed to delete " + currentPath, e);
         ExceptionAlert.showAlert("Failed to delete " + currentPath, e);
      }
   }

   private void gitCommit(ActionEvent actionEvent) {
      TextInputDialog td = new TextInputDialog();
      GuiUtils.addStyle(gui, td);
      td.setHeaderText("Enter commit message");
      final Optional<String> result = td.showAndWait();
      if (result.isPresent()) {
         String msg = td.getEditor().getText();
         if (StringUtils.isBlank(msg)) {
            Alerts.info("Empty message", "Commit message cannot be empty");
            return;
         }
         gui.setWaitCursor();
         Platform.runLater(() -> {
            try {
               CommitCommand commit = git.commit();
               RevCommit revCommit = commit.setMessage(msg).call();
               log.info("Commited result: {}", revCommit);
               fileTree.refresh();
               gui.setNormalCursor();
            } catch (GitAPIException e) {
               log.warn("Failed to commit ", e);
               gui.setNormalCursor();
               ExceptionAlert.showAlert("Failed to commit ", e);
            }
         });
      }
   }

   private void gitAdd(ActionEvent actionEvent) {
      String currentPath = getRelativePath();
      try {
         DirCache dc = git.add().addFilepattern(currentPath).call();
         log.info("Added {} to git dir cache, node is {}", currentPath, currentNode.getValue().getText());
         GitUtils.colorNode(git, currentPath, currentNode);
      } catch (GitAPIException e) {
         log.warn("Failed to add " + currentPath, e);
         ExceptionAlert.showAlert("Failed to add " + currentPath, e);
      }
   }

   private void gitInit(ActionEvent actionEvent) {
      try {
         git = Git.init().setDirectory(fileTree.getRootDir()).call();
         StoredConfig config = git.getRepository().getConfig();
         // Use input as the default
         config.setEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
            ConfigConstants.CONFIG_KEY_AUTOCRLF, CoreConfig.AutoCRLF.INPUT);
         config.save();
         String gitIgnoreTemplate = "templates/.gitignore";
         File gitIgnore = new File(fileTree.getRootDir(), ".gitignore");
         if (gitIgnore.exists()) {
            String content = FileUtils.readContent(gitIgnore);
            if (!content.contains("/target")) {
               FileUtils.writeToFile(gitIgnore, content + "\n" + FileUtils.readContent(gitIgnoreTemplate));
            }
         } else {
            FileUtils.copy(gitIgnoreTemplate, fileTree.getRootDir());
         }
         fileTree.refresh();
      } catch (GitAPIException | IOException e) {
         log.warn("Failed to initialize git in " + fileTree.getRootDir().getAbsolutePath(), e);
         ExceptionAlert.showAlert("Failed to initialize git in " + fileTree.getRootDir().getAbsolutePath(), e);
      }
   }


   private void gitConfig(ActionEvent actionEvent) {
      GitConfigureDialog dialog = new GitConfigureDialog(git);
      Optional<ConfigResult> result = dialog.showAndWait();
      if (result.isPresent()) {
         ConfigResult res = result.get();
         StoredConfig config = git.getRepository().getConfig();
         config.setEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
            ConfigConstants.CONFIG_KEY_AUTOCRLF, res.autoCRLF);

         log.info("Storing config {}", res);
         try {
            config.save();
         } catch (Exception e) {
            ExceptionAlert.showAlert("Failed to store configuration", e);
         }
      }
   }

   private File promptForFile(File currentFile, String title, String content) {
      TextInputDialog dialog = new TextInputDialog();
      dialog.setTitle(title);
      dialog.setContentText(content);
      GuiUtils.addStyle(gui, dialog);
      Optional<String> result = dialog.showAndWait();
      if (result.isEmpty()) {
         return null;
      }
      String file = result.get();
      File newFile;
      if (currentFile.isDirectory()) {
         newFile = new File(currentFile, file);
      } else {
         newFile = new File(currentFile.getParentFile(), file);
      }
      return newFile;
   }

   public void setContext(TreeItem<FileItem> item) {
      currentNode = item;
      currentFile = currentNode.getValue().getFile();
      showHideContent(item);
   }

   private void showHideContent(TreeItem<FileItem> selected) {
      if (selected !=  null && selected.getValue().getFile().isFile()) {
         createDirMI.setDisable(true);
         createFileMI.setDisable(true);
         expandAllMI.setDisable(true);
      } else {
         createDirMI.setDisable(false);
         createFileMI.setDisable(false);
         expandAllMI.setDisable(false);
      }
   }

   public void hideFileItems() {
      createDirMI.setVisible(true);
      createFileMI.setVisible(true);
      expandAllMI.setVisible(true);
      gitInitMI.setVisible(true);
   }

   public void hideDirItems() {
      createDirMI.setVisible(false);
      createFileMI.setVisible(false);
      expandAllMI.setVisible(false);
      gitInitMI.setVisible(false);
   }
}
