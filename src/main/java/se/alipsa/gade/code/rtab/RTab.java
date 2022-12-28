package se.alipsa.gade.code.rtab;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.TextAreaTab;
import se.alipsa.gade.console.ConsoleComponent;

import java.io.File;

public class RTab extends TextAreaTab {

  private RTextArea rTextArea;

  private final ConsoleComponent console;


  private static Logger log = LogManager.getLogger(RTab.class);

  public RTab(String title, Gade gui) {
    super(gui, CodeType.R);
    this.console = gui.getConsoleComponent();

    setTitle(title);

    rTextArea = new RTextArea(this);
    VirtualizedScrollPane<RTextArea> vPane = new VirtualizedScrollPane<>(rTextArea);
    pane.setCenter(vPane);
  }

  @Override
  public File getFile() {
    return rTextArea.getFile();
  }

  @Override
  public void setFile(File file) {
    rTextArea.setFile(file);
  }

  @Override
  public String getTextContent() {
    return rTextArea.getTextContent();
  }

  @Override
  public String getAllTextContent() {
    return rTextArea.getAllTextContent();
  }

  @Override
  public void replaceContentText(int start, int end, String content) {
    rTextArea.replaceContentText(start, end, content);
  }

  @Override
  public void replaceContentText(String content, boolean isReadFromFile) {
    rTextArea.replaceContentText(content, isReadFromFile);
  }

  @Override
  public CodeTextArea getCodeArea() {
    return rTextArea;
  }
}
