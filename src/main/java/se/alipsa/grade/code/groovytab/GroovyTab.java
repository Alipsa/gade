package se.alipsa.grade.code.groovytab;

import javafx.scene.control.Button;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.grade.Grade;
import se.alipsa.grade.TaskListener;
import se.alipsa.grade.code.CodeTextArea;
import se.alipsa.grade.code.CodeType;
import se.alipsa.grade.code.TextAreaTab;
import se.alipsa.grade.console.ConsoleComponent;
import se.alipsa.grade.console.ConsoleTextArea;

import java.io.File;

import static se.alipsa.grade.menu.GlobalOptions.ADD_IMPORTS;

public class GroovyTab extends TextAreaTab implements TaskListener {

  private final GroovyTextArea groovyTextArea;

  private static final Logger log = LogManager.getLogger(GroovyTab.class);
  private final Button runButton;

  public GroovyTab(String title, Grade gui) {
    super(gui, CodeType.GROOVY);
    setTitle(title);
    runButton = new Button("Run");
    runButton.setOnAction(a -> runGroovy());
    buttonPane.getChildren().add(runButton);

    Button resetButton = new Button("Restart session");
    resetButton.setOnAction(a -> gui.getConsoleComponent().restartGroovy());
    buttonPane.getChildren().add(resetButton);

    groovyTextArea = new GroovyTextArea(this);
    VirtualizedScrollPane<GroovyTextArea> javaPane = new VirtualizedScrollPane<>(groovyTextArea);
    pane.setCenter(javaPane);
  }

  public void runGroovy() {
    String code = "";
    if (gui.getPrefs().getBoolean(ADD_IMPORTS, true)) {
      code = groovyTextArea.getImports();
    }
    runGroovy(code + getTextContent());
  }

  public void runGroovy(final String content) {
    ConsoleComponent consoleComponent = gui.getConsoleComponent();
    final ConsoleTextArea console = consoleComponent.getConsole();
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
