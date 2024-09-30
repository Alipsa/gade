package se.alipsa.gade.code.groovytab;

import javafx.scene.control.Button;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.gade.Gade;
import se.alipsa.gade.TaskListener;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.TextAreaTab;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.utils.ExceptionAlert;

import java.io.File;
import java.util.List;

import static se.alipsa.gade.menu.GlobalOptions.ADD_DEPENDENCIES;
import static se.alipsa.gade.menu.GlobalOptions.ADD_IMPORTS;

public class GroovyTab extends TextAreaTab implements TaskListener {

  private final GroovyTextArea groovyTextArea;

  private static final Logger log = LogManager.getLogger(GroovyTab.class);
  protected final Button runButton;

  public GroovyTab(String title, Gade gui, boolean... addSessionRestartButton) {
    super(gui, CodeType.GROOVY);
    setTitle(title);
    runButton = new Button("Run");
    runButton.setOnAction(a -> runGroovy());
    buttonPane.getChildren().add(runButton);

    if (addSessionRestartButton.length <= 0 || addSessionRestartButton[0]) {
      Button resetButton = new Button("Restart session");
      resetButton.setOnAction(a -> gui.getConsoleComponent().restartGroovy());
      buttonPane.getChildren().add(resetButton);
    }

    groovyTextArea = new GroovyTextArea(this);
    VirtualizedScrollPane<GroovyTextArea> javaPane = new VirtualizedScrollPane<>(groovyTextArea);
    pane.setCenter(javaPane);
  }

  public void runGroovy() {
    boolean runImports = gui.getPrefs().getBoolean(ADD_IMPORTS, true);
    boolean runDeps = gui.getPrefs().getBoolean(ADD_DEPENDENCIES, true);
    log.info("runImports = {}, runDeps = {}", runImports, runDeps);
    if (runDeps) {
      List<String> headers = groovyTextArea.getDependencies();
      String deps = String.join("\n", headers);
      if (headers.contains("@Grab")){
        deps += "\nimport java.lang.Object";
      }
      try {
        log.info("Running {}", deps);
        var result = gui.getConsoleComponent().runScriptSilent(deps);
        log.info("Result was {}", result);
      } catch (Exception e) {
        log.warn("Failed to run: {}", deps);
        ExceptionAlert.showAlert("Failed to run dependencies", e);
        return;
      }
      //gui.dynamicClassLoader.setShouldRecompile(true);
    }
    String code = getTextContent();
    if (runImports) {
      List<String> headers = groovyTextArea.getImports();
      String imports = String.join("\n", headers);
      code = imports + "\n" + code;
    }
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
      consoleComponent.runScriptAsync(content, title, this);
    } catch (Exception e) {
      ExceptionAlert.showAlert("Failed to run script", e);
    }
  }

  @Override
  public File getFile() {
    return groovyTextArea.getFile();
  }

  @Override
  public void setFile(File file) {
    groovyTextArea.setFile(file);
  }

  @Override
  public String getTextContent() {
    return groovyTextArea.getTextContent();
  }

  @Override
  public String getAllTextContent() {
    return groovyTextArea.getAllTextContent();
  }

  @Override
  public void replaceContentText(int start, int end, String content) {
    groovyTextArea.replaceContentText(start, end, content);
  }

  @Override
  public void replaceContentText(String content, boolean isReadFromFile) {
    groovyTextArea.replaceContentText(content, isReadFromFile);
  }

  @Override
  public CodeTextArea getCodeArea() {
    return groovyTextArea;
  }

  @Override
  public void taskStarted() {
    runButton.setDisable(true);
  }

  @Override
  public void taskEnded() {
    runButton.setDisable(false);
  }
}
