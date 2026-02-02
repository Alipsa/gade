package se.alipsa.gade.code.groovytab;

import javafx.scene.control.Button;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.ExecutableTab;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.utils.ExceptionAlert;

import java.io.File;
import java.util.List;

import static se.alipsa.gade.menu.GlobalOptions.ADD_DEPENDENCIES;
import static se.alipsa.gade.menu.GlobalOptions.ADD_IMPORTS;

public class GroovyTab extends ExecutableTab {

  private final GroovyTextArea groovyTextArea;

  private static final Logger log = LogManager.getLogger(GroovyTab.class);

  public GroovyTab(String title, Gade gui, boolean... addSessionRestartButton) {
    super(gui, CodeType.GROOVY);
    setTitle(title);

    if (addSessionRestartButton.length <= 0 || addSessionRestartButton[0]) {
      Button resetButton = new Button("Restart session");
      resetButton.setOnAction(a -> gui.getConsoleComponent().restartGroovy());
      buttonPane.getChildren().add(resetButton);
    }

    groovyTextArea = new GroovyTextArea(this);
    VirtualizedScrollPane<GroovyTextArea> javaPane = new VirtualizedScrollPane<>(groovyTextArea);
    pane.setCenter(javaPane);
  }

  @Override
  protected void executeAction() {
    runGroovy();
  }

  @Override
  protected CodeTextArea getTextArea() {
    return groovyTextArea;
  }

  public void runGroovy() {
    boolean runImports = gui.getPrefs().getBoolean(ADD_IMPORTS, true);
    boolean runDeps = gui.getPrefs().getBoolean(ADD_DEPENDENCIES, true);
    //log.info("runImports = {}, runDeps = {}", runImports, runDeps);
    if (runDeps) {
      List<String> headers = groovyTextArea.getDependencies();
      String deps = String.join("\n", headers);
      if (deps.contains("@Grab")){
        // if we are using @Grab then we must have something else (e.g. an import statement) to be able to run it
        deps += "\nimport java.lang.Object";
      }
      try {
        //log.info("Running {}", deps);
        var result = gui.getConsoleComponent().runScriptSilent(deps);
        //log.info("Result was {}", result);
      } catch (Exception e) {
        log.warn("Failed to run: {}", deps);
        ExceptionAlert.showAlert("Failed to run dependencies", e);
        return;
      }
    }
    String code = getTextContent();
    if (runImports) {
      List<String> headers = groovyTextArea.getImports();
      String imports = String.join("\n", headers);
      code = code.replace(imports, "");
      code = imports + "\n" + code;
    }
    // Comment out shebangs
    code = code.replace("#!/usr/bin/env groovy", "// #!/usr/bin/env groovy");
    runGroovy(code);
  }

  public void runGroovy(final String content, boolean addImportsIfPreferred) {
    String code;
    if (addImportsIfPreferred && gui.getPrefs().getBoolean(ADD_IMPORTS, true)) {
      List<String> headers = groovyTextArea.getImports();
      String imports = String.join("\n", headers);
      code = imports + "\n" + content;
    } else {
      code = content;
    }
    runGroovy(code);
  }

  public void runGroovy(final String content) {
    ConsoleComponent consoleComponent = gui.getConsoleComponent();
    final String title = getTitle();
    consoleComponent.running();
    try {
      File sourceFile = getFile();
      if (sourceFile == null) {
        consoleComponent.runScriptAsync(content, title, this);
      } else {
        consoleComponent.runScriptAsync(content, title, this, sourceFile);
      }
    } catch (Exception e) {
      ExceptionAlert.showAlert("Failed to run script", e);
    }
  }
}
