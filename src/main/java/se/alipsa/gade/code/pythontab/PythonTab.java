package se.alipsa.gade.code.pythontab;

import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.TextAreaTab;

import java.io.File;

public class PythonTab extends TextAreaTab {

  private final PythonTextArea pythonTextArea;

  public PythonTab(String title, Gade gui) {
    super(gui, CodeType.PYTHON);

    setTitle(title);

    pythonTextArea = new PythonTextArea(this);
    VirtualizedScrollPane<PythonTextArea> vPane = new VirtualizedScrollPane<>(pythonTextArea);
    pane.setCenter(vPane);
  }

  @Override
  public File getFile() {
    return pythonTextArea.getFile();
  }

  @Override
  public void setFile(File file) {
    pythonTextArea.setFile(file);
  }

  @Override
  public String getTextContent() {
    return pythonTextArea.getTextContent();
  }

  @Override
  public String getAllTextContent() {
    return pythonTextArea.getAllTextContent();
  }

  @Override
  public void replaceContentText(int start, int end, String content) {
    pythonTextArea.replaceContentText(start, end, content);
  }

  @Override
  public void replaceContentText(String content, boolean isReadFromFile) {
    pythonTextArea.replaceContentText(content, isReadFromFile);
  }

  @Override
  public CodeTextArea getCodeArea() {
    return pythonTextArea;
  }
}
