package se.alipsa.gade.code.javatab;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.TextAreaTab;

import java.io.File;

public class JavaTab extends TextAreaTab {

  private final JavaTextArea javaTextArea;

  private static final Logger log = LogManager.getLogger(JavaTab.class);

  public JavaTab(String title, Gade gui) {
    super(gui, CodeType.JAVA);
    setTitle(title);
    javaTextArea = new JavaTextArea(this);
    VirtualizedScrollPane<JavaTextArea> javaPane = new VirtualizedScrollPane<>(javaTextArea);
    pane.setCenter(javaPane);
  }

  @Override
  public File getFile() {
    return javaTextArea.getFile();
  }

  @Override
  public void setFile(File file) {
    javaTextArea.setFile(file);
  }

  @Override
  public String getTextContent() {
    return javaTextArea.getTextContent();
  }

  @Override
  public String getAllTextContent() {
    return javaTextArea.getAllTextContent();
  }

  @Override
  public void replaceContentText(int start, int end, String content) {
    javaTextArea.replaceContentText(start, end, content);
  }

  @Override
  public void replaceContentText(String content, boolean isReadFromFile)  {
    javaTextArea.replaceContentText(content, isReadFromFile);
  }

  @Override
  public CodeTextArea getCodeArea() {
    return javaTextArea;
  }
}
