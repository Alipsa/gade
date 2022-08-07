package se.alipsa.gade.code.mdtab;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.TextAreaTab;

import java.io.File;

public class MdTab extends TextAreaTab  {

  private final MdTextArea mdTextArea;
  private Button viewButton;
  private Button htmlButton;
  private Button pdfButton;

  public MdTab(String title, Gade gui) {
    super(gui, CodeType.TXT);
    setTitle(title);
    mdTextArea = new MdTextArea(this);
    VirtualizedScrollPane<MdTextArea> txtPane = new VirtualizedScrollPane<>(mdTextArea);
    pane.setCenter(txtPane);

    viewButton = new Button();
    viewButton.setGraphic(new ImageView(IMG_VIEW));
    viewButton.setTooltip(new Tooltip("Render and view"));
    viewButton.setOnAction(a -> MdUtil.viewMd(gui, getTitle(), getTextContent()));
    buttonPane.getChildren().add(viewButton);

    htmlButton = new Button();
    htmlButton.setGraphic(new ImageView(IMG_PUBLISH));
    htmlButton.setStyle("-fx-border-color: darkgreen");
    htmlButton.setTooltip(new Tooltip("Export to html"));
    htmlButton.setOnAction(this::exportToHtml);
    buttonPane.getChildren().add(htmlButton);

    pdfButton = new Button();
    pdfButton.setGraphic(new ImageView(IMG_PUBLISH));
    pdfButton.setStyle("-fx-border-color: #70503B");
    pdfButton.setTooltip(new Tooltip("Export to pdf"));
    pdfButton.setOnAction(this::exportToPdf);
    buttonPane.getChildren().add(pdfButton);
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

  private void exportToHtml(ActionEvent actionEvent) {
    FileChooser fc = new FileChooser();
    fc.setTitle("Save HTML File");
    String initialFileName = getTitle().replace("*", "").replace(".md", "");
    if (initialFileName.endsWith(".")) {
      initialFileName = initialFileName + "html";
    } else {
      initialFileName = initialFileName + ".html";
    }
    fc.setInitialDirectory(gui.getInoutComponent().projectDir());
    fc.setInitialFileName(initialFileName);
    fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("HTML", "*.html"));
    File outFile = fc.showSaveDialog(gui.getStage());
    if (outFile == null) {
      return;
    }
    MdUtil.saveMdAsHtml(outFile, getTextContent());
  }

  private void exportToPdf(ActionEvent actionEvent) {
    FileChooser fc = new FileChooser();
    fc.setTitle("Save PDF File");
    String initialFileName = getTitle().replace("*", "").replace(".md", "");
    if (initialFileName.endsWith(".")) {
      initialFileName = initialFileName + "pdf";
    } else {
      initialFileName = initialFileName + ".pdf";
    }
    fc.setInitialDirectory(gui.getInoutComponent().projectDir());
    fc.setInitialFileName(initialFileName);
    fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
    File outFile = fc.showSaveDialog(gui.getStage());
    if (outFile == null) {
      return;
    }
    MdUtil.saveMdAsPdf(getTextContent(), outFile);
  }
}
