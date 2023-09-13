package se.alipsa.gade.code.groovytab;

import javafx.scene.control.Button;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.gade.Constants;
import se.alipsa.gade.Gade;
import se.alipsa.gade.TaskListener;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.TextAreaTab;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.model.GroovyCodeHeader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    String imports = "";
    if (gui.getPrefs().getBoolean(ADD_IMPORTS, true)) {
      GroovyCodeHeader headers = groovyTextArea.getImportsAndDependencies();
      imports = headers.imports();
      runGroovy(headers.grabs() + "\n" + headers.deps());
    }
    runGroovy(imports + "\n" + getTextContent());
  }

  public void runGroovy(final String content) {
    ConsoleComponent consoleComponent = gui.getConsoleComponent();
    final String title = getTitle();
    consoleComponent.running();
    consoleComponent.runScriptAsync(content, title, this);
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
