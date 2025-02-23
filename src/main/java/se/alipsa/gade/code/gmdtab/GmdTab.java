package se.alipsa.gade.code.gmdtab;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.gade.Gade;
import se.alipsa.gade.TaskListener;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.TextAreaTab;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.groovy.gmd.GmdException;

import java.io.File;

public class GmdTab extends TextAreaTab implements TaskListener {

  private final GmdTextArea gmdTextArea;
  Button viewButton;
  Button htmlButton;
  Button pdfButton;

  public GmdTab(String title, Gade gui) {
    super(gui, CodeType.GMD);
    setTitle(title);

    gmdTextArea = new GmdTextArea(this);
    VirtualizedScrollPane<GmdTextArea> txtPane = new VirtualizedScrollPane<>(gmdTextArea);
    pane.setCenter(txtPane);

    viewButton = new Button();
    viewButton.setGraphic(new ImageView(IMG_VIEW));
    viewButton.setTooltip(new Tooltip("Render and view"));
    viewButton.setOnAction(this::viewMdr);
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

  private void viewMdr(ActionEvent actionEvent) {
    try {
      GmdUtil.viewGmd(gui, getTitle(), getTextContent());
    } catch (GmdException e) {
      ExceptionAlert.showAlert("Failed to view gmd", e);
    }
  }

  private void exportToPdf(ActionEvent actionEvent) {
    try {
      FileChooser fc = new FileChooser();
      fc.setTitle("Save PDF File");
      String initialFileName = getTitle().replace("*", "").replace(".gmd", "");
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
      gui.setWaitCursor();
      GmdUtil.saveGmdAsPdf(getTextContent(), outFile);
      gui.setNormalCursor();
    } catch (GmdException e) {
      ExceptionAlert.showAlert("Failed to save gmd as pdf", e);
    }
  }

  private void exportToHtml(ActionEvent actionEvent) {
    try {
      FileChooser fc = new FileChooser();
      fc.setTitle("Save HTML File");
      String initialFileName = getTitle().replace("*", "").replace(".gmd", "");
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
      GmdUtil.saveGmdAsHtml(outFile, getTextContent());
    } catch (GmdException e) {
      ExceptionAlert.showAlert("Failed to save gmd as html", e);
    }
  }

  @Override
  public CodeTextArea getCodeArea() {
    return gmdTextArea;
  }

  @Override
  public File getFile() {
    return gmdTextArea.getFile();
  }

  @Override
  public void setFile(File file) {
    gmdTextArea.setFile(file);
  }

  @Override
  public String getTextContent() {
    return gmdTextArea.getTextContent();
  }

  @Override
  public String getAllTextContent() {
    return gmdTextArea.getAllTextContent();
  }

  @Override
  public void replaceContentText(int start, int end, String content) {
    gmdTextArea.replaceContentText(start, end, content);
  }

  @Override
  public void replaceContentText(String content, boolean isReadFromFile) {
    gmdTextArea.replaceContentText(content, isReadFromFile);
  }

  @Override
  public void taskStarted() {
    viewButton.setDisable(true);
    htmlButton.setDisable(true);
    pdfButton.setDisable(true);
  }

  @Override
  public void taskEnded() {
    viewButton.setDisable(false);
    htmlButton.setDisable(false);
    pdfButton.setDisable(false);
  }
}
