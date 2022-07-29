package se.alipsa.gade.code.mdtab;

import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.TextAreaTab;

import java.io.File;

public class MdTab extends TextAreaTab  {

  private final MdTextArea mdTextArea;

  public MdTab(String title, Gade gui) {
    super(gui, CodeType.TXT);
    setTitle(title);
    mdTextArea = new MdTextArea(this);
    VirtualizedScrollPane<MdTextArea> txtPane = new VirtualizedScrollPane<>(mdTextArea);
    pane.setCenter(txtPane);
  }

  @Override
  public CodeTextArea getCodeArea() {
    return mdTextArea;
  }

  @Override
  public File getFile() {
    return mdTextArea.getFile();
  }

  @Override
  public void setFile(File file) {
    mdTextArea.setFile(file);
  }

  @Override
  public String getTextContent() {
    return mdTextArea.getTextContent();
  }

  @Override
  public String getAllTextContent() {
    return mdTextArea.getAllTextContent();
  }

  @Override
  public void replaceContentText(int start, int end, String content) {
    mdTextArea.replaceContentText(start, end, content);
  }

  @Override
  public void replaceContentText(String content, boolean isReadFromFile) {
    mdTextArea.replaceContentText(content, isReadFromFile);
  }
}
