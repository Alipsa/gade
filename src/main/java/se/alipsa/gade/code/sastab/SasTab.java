package se.alipsa.gade.code.sastab;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.TextAreaTab;

import java.io.File;

public class SasTab extends TextAreaTab {

  private SasTextArea sasTextArea;

  private static Logger log = LogManager.getLogger(SasTab.class);

  public SasTab(String title, Gade gui) {
    super(gui, CodeType.SAS);

    setTitle(title);

    sasTextArea = new SasTextArea(this);
    VirtualizedScrollPane<SasTextArea> vPane = new VirtualizedScrollPane<>(sasTextArea);
    pane.setCenter(vPane);
  }

  @Override
  public File getFile() {
    return sasTextArea.getFile();
  }

  @Override
  public void setFile(File file) {
    sasTextArea.setFile(file);
  }

  @Override
  public String getTextContent() {
    return sasTextArea.getTextContent();
  }

  @Override
  public String getAllTextContent() {
    return sasTextArea.getAllTextContent();
  }

  @Override
  public void replaceContentText(int start, int end, String content) {
    sasTextArea.replaceContentText(start, end, content);
  }

  @Override
  public void replaceContentText(String content, boolean isReadFromFile) {
    sasTextArea.replaceContentText(content, isReadFromFile);
  }

  @Override
  public CodeTextArea getCodeArea() {
    return sasTextArea;
  }
}
