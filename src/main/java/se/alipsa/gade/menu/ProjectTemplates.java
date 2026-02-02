package se.alipsa.gade.menu;

import groovy.lang.GroovySystem;
import org.apache.commons.text.CaseUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jetbrains.annotations.NotNull;
import se.alipsa.gade.Gade;
import se.alipsa.gade.runtime.RuntimeConfig;
import se.alipsa.gade.runtime.RuntimeManager;
import se.alipsa.gade.runtime.RuntimeType;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public class ProjectTemplates {

  private final Gade gui;
  private final Runnable refreshRuntimesMenu;

  public ProjectTemplates(Gade gui, Runnable refreshRuntimesMenu) {
    this.gui = gui;
    this.refreshRuntimesMenu = refreshRuntimesMenu;
  }

  public void cloneProject() {
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

  public void createBuildFile() {
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

  public void showProjectWizard() {
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

  public void showLibraryWizard() {
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

  private String camelCasedPackageName(CreateProjectWizardResult res) {
    return CaseUtils.toCamelCase(res.projectName, true,
        ' ', '_', '-', ',', '.', '/', '\\');
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
    } else if (refreshRuntimesMenu != null) {
      refreshRuntimesMenu.run();
    }
  }
}
