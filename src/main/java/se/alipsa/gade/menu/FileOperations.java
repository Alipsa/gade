package se.alipsa.gade.menu;

import javafx.animation.PauseTransition;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.TextAreaTab;
import se.alipsa.gade.runtime.RuntimeConfig;
import se.alipsa.gade.runtime.RuntimeType;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.gade.utils.git.GitUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;

public class FileOperations {

  private static final Logger log = LogManager.getLogger(FileOperations.class);

  private final Gade gui;
  private final Runnable restartEngine;
  private final PauseTransition runtimeReloadDebounce = new PauseTransition(Duration.millis(300));
  private String runtimeReloadReason;

  public FileOperations(Gade gui, Runnable restartEngine) {
    this.gui = gui;
    this.restartEngine = restartEngine;
    runtimeReloadDebounce.setOnFinished(e -> {
      if (runtimeReloadReason == null) {
        return;
      }
      String reason = runtimeReloadReason;
      runtimeReloadReason = null;
      log.debug("Restarting runtime due to {}", reason);
      restartEngine.run();
    });
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
      if (codeArea.getTreeItem() != null && git != null) {
        String path = GitUtils.asRelativePath(codeArea.getFile(), gui.getInoutComponent().projectDir());
        GitUtils.colorNode(git, path, codeArea.getTreeItem());
      }
    } catch (FileNotFoundException e) {
      ExceptionAlert.showAlert("Failed to save file " + file, e);
    }
  }

  public void saveContentAs(TextAreaTab codeArea) {
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
}
