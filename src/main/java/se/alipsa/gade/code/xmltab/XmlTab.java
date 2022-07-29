package se.alipsa.gade.code.xmltab;

import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.TextAreaTab;

import java.io.File;

public class XmlTab extends TextAreaTab {

  private final XmlTextArea xmlTextArea;

  //private static final Logger log = LogManager.getLogger(XmlTab.class);

  public XmlTab(String title, Gade gui) {
    super(gui, CodeType.XML);
    setTitle(title);

    xmlTextArea = new XmlTextArea(this);
    VirtualizedScrollPane<CodeTextArea> xmlPane = new VirtualizedScrollPane<>(xmlTextArea);
    pane.setCenter(xmlPane);

    saveButton.setOnAction(a -> {
      gui.getMainMenu().saveContent(this);
    });
  }

  @Override
  public File getFile() {
    return xmlTextArea.getFile();
  }

  @Override
  public void setFile(File file) {
    xmlTextArea.setFile(file);
  }

  @Override
  public String getTextContent() {
    return xmlTextArea.getTextContent();
  }

  @Override
  public String getAllTextContent() {
    return xmlTextArea.getAllTextContent();
  }

  @Override
  public void replaceContentText(int start, int end, String content) {
    xmlTextArea.replaceContentText(start, end, content);
  }

  @Override
  public void replaceContentText(String content, boolean isReadFromFile) {
    xmlTextArea.replaceText(content);
    if(isReadFromFile) {
      contentSaved();
    }
  }

  @Override
  public CodeTextArea getCodeArea() {
    return xmlTextArea;
  }
}
