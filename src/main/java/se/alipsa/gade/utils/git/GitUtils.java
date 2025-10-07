package se.alipsa.gade.utils.git;

import static se.alipsa.gade.Constants.GitStatus.GIT_ADDED;
import static se.alipsa.gade.Constants.GitStatus.GIT_CHANGED;
import static se.alipsa.gade.Constants.GitStatus.GIT_CONFLICT;
import static se.alipsa.gade.Constants.GitStatus.GIT_IGNORED;
import static se.alipsa.gade.Constants.GitStatus.GIT_MODIFIED;
import static se.alipsa.gade.Constants.GitStatus.GIT_NONE;
import static se.alipsa.gade.Constants.GitStatus.GIT_UNCOMITTED_CHANGE;
import static se.alipsa.gade.Constants.GitStatus.GIT_UNTRACKED;
import static se.alipsa.gade.utils.StringUtils.isBlank;

import javafx.scene.control.TreeItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import se.alipsa.gade.inout.FileItem;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public class GitUtils {

   private static final Logger log = LogManager.getLogger(GitUtils.class);
   private static final String CREDENTIALS_FILENAME = ".git-credentials";

   public static String asRelativePath(File currentFile, File rootDir) {
      String root = rootDir.getAbsolutePath();
      String nodePath = currentFile.getAbsolutePath();
      String path = nodePath.replace(root, "").replace('\\', '/');
      if (path.length() <= 1) {
         return ".";
      }
      if (path.startsWith("/")) {
         return path.substring(1);
      }
      return path;
   }

   public static void colorNode(Git git, String path, TreeItem<FileItem> treeItem) {
      if (git == null || treeItem == null) {
         return;
      }
      try {
         Status status = git.status().addPath(path).call();
         if (status.getConflicting().size() > 0) {
            treeItem.getValue().setStyle(GIT_CONFLICT.getStyle());
            return;
         }
         if (status.getAdded().size() > 0) {
            treeItem.getValue().setStyle(GIT_ADDED.getStyle());
            return;
         }
         if (status.getChanged().size() > 0) {
            treeItem.getValue().setStyle(GIT_CHANGED.getStyle());
            return;
         }
         if (status.getModified().size() > 0) {
            treeItem.getValue().setStyle(GIT_MODIFIED.getStyle());
            return;
         }
         if (status.getUncommittedChanges().size() > 0) {
            treeItem.getValue().setStyle(GIT_UNCOMITTED_CHANGE.getStyle());
            return;
         }
         if (status.getUntracked().size() > 0) {
            treeItem.getValue().setStyle(GIT_UNTRACKED.getStyle());
            return;
         }
         if (status.getIgnoredNotInIndex().size() > 0) {
            treeItem.getValue().setStyle(GIT_IGNORED.getStyle());
            return;
         }
         treeItem.getValue().setStyle(GIT_NONE.getStyle());
      } catch (GitAPIException e) {
         log.warn("Failed to get git status to color node " + path, e);
         ExceptionAlert.showAlert("Failed to get git status to color node " + path, e);
      }
   }

   public static void storeCredentials(String url, String userName, String password) throws URISyntaxException, IOException {
      File gitCredentials = getCredentialsFile();
      URIish remoteUri = new URIish(url)
         .setUser(userName)
         .setPass(password);
      log.info("Storing {} to {}", remoteUri.toString(), gitCredentials.getAbsolutePath());
      FileUtils.appendToOrCreateFile(gitCredentials, " \n" + remoteUri.toPrivateString());
   }

   public static CredentialsProvider getStoredCredentials(String url) throws IOException, URISyntaxException {
      var userPwd = findUserNamePassword(url);
      log.info("Credentials for {} are {}", url, userPwd);
      return userPwd == null ? null : new UsernamePasswordCredentialsProvider(userPwd.get("username"), userPwd.get("password"));
   }

   public static Map<String,String> findUserNamePassword(String url) throws IOException, URISyntaxException {
      File gitCredentials = getCredentialsFile();
      if (!gitCredentials.exists()) {
         log.info("No credentials file ({}) found", CREDENTIALS_FILENAME);
         return null;
      }
      if (isBlank(url)) {
         return null;
      }
      URIish remoteUri = new URIish(url);
      List<String> lines = Files.readAllLines(gitCredentials.toPath());
      for (String line : lines) {
         if (line == null || line.trim().equals("") || line.trim().startsWith("#")) {
            continue;
         }
         URIish uri = new URIish(line);
         String uriPath = uri.getPath();
         String remotePath = remoteUri.getPath();
         // if uri.getPath() == null it should always match, see https://git-scm.com/docs/gitcredentials
         if (uriPath == null) {
            uriPath = "";
            remotePath = "";
         }
         // Convert null to "null" so equals is safe
         String scheme = String.valueOf(uri.getScheme());
         String remoteScheme = String.valueOf(remoteUri.getScheme());

         if (scheme.equals(remoteScheme)
             && uri.getHost().equals(remoteUri.getHost())
             && uriPath.equals(remotePath)
             && uri.getUser().equals(remoteUri.getUser())) {
            return Map.of("username", uri.getUser(), "password", uri.getPass());
         }
      }
      return null;
   }

   /**
    * Format of the ~/.git-credentials file:
    * https://username:token@github.com/
    * git:certpassword@github.com:nordnet-private/odds_ucbatch.git
    *
    * @return
    */
   public static File getCredentialsFile() {
      return new File(FileUtils.getUserHome(), CREDENTIALS_FILENAME);
   }

  public static void removeCredentials(String url) throws URISyntaxException, IOException {
     File gitCredentials = getCredentialsFile();
     if (!gitCredentials.exists()) {
        log.info("No credentials file ({}) found", CREDENTIALS_FILENAME);
        return;
     }
     if (isBlank(url)) {
        return;
     }
     URIish remoteUri = new URIish(url);
     List<String> lines = Files.readAllLines(gitCredentials.toPath());
     String lineToRemove = null;
     for (String line : lines) {
        if (line == null || line.trim().equals("") || line.trim().startsWith("#")) {
           continue;
        }
        URIish uri = new URIish(line);
        String uriPath = uri.getPath();
        String remotePath = remoteUri.getPath();
        // if uri.getPath() == null it should always match, see https://git-scm.com/docs/gitcredentials
        if (uriPath == null) {
           uriPath = "";
           remotePath = "";
        }

        String scheme = String.valueOf(uri.getScheme());
        String remoteScheme = String.valueOf(remoteUri.getScheme());

        if (scheme.equals(remoteScheme)
            && uri.getHost().equals(remoteUri.getHost())
            && uriPath.equals(remotePath)) {

           lineToRemove = line;
        }
     }
     lines.remove(lineToRemove);
     FileUtils.writeToFile(gitCredentials, lines);
  }

  public static FetchResult fetch(File dir) throws IOException, URISyntaxException, GitAPIException {
     try(Git git = Git.open(dir)) {

        String url = git.getRepository().getConfig().getString("remote", "origin", "url");
        log.info("remote origin Url is " + url);
        log.info("Getting credentials");
        SshTransportConfigCallback sshTransportConfigCallback = new SshTransportConfigCallback(url);
        CredentialsProvider credentialsProvider = getStoredCredentials(url);
        log.info("Fetching...");
        FetchCommand fetchCommand = git.fetch()
                .setTransportConfigCallback(sshTransportConfigCallback);
        if (credentialsProvider != null) {
           fetchCommand.setCredentialsProvider(credentialsProvider);
        }
        return fetchCommand.call();
     }
  }


}
