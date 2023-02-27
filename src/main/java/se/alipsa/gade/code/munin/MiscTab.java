package se.alipsa.gade.code.munin;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.code.xmltab.XmlTextArea;
import se.alipsa.gade.model.MuninReport;
import se.alipsa.gade.model.ReportType;

public class MiscTab extends Tab {

  private static final Logger log = LogManager.getLogger();
  private final TextField reportNameTF;
  private final TextArea descriptionTA;
  private final TextField groupTF;
  private final ComboBox<ReportType> typeCB;
  private final XmlTextArea inputTA;

  public MiscTab(MuninTab parentTab) {
    MuninReport muninReport = parentTab.getMuninReport();
    setText("config");
    VBox vBox = new VBox();
    vBox.setSpacing(5);
    vBox.setPadding(new Insets(5));

    HBox nameHbox = new HBox(5);
    reportNameTF = new TextField(muninReport.getReportName());
    nameHbox.getChildren().addAll(new Label("Report name:"), reportNameTF);
    reportNameTF.textProperty().addListener((observable, oldValue, newValue) -> {
      parentTab.setTitle(newValue);
      parentTab.contentChanged();
    });
    vBox.getChildren().add(nameHbox);

    descriptionTA = new TextArea(muninReport.getDescription());
    descriptionTA.setPrefRowCount(2);
    descriptionTA.textProperty().addListener((observable, oldValue, newValue) -> parentTab.contentChanged());
    vBox.getChildren().addAll(new Label("Description"), descriptionTA);

    HBox groupAndTypeBox = new HBox(5);
    vBox.getChildren().add(groupAndTypeBox);
    groupTF = new TextField(muninReport.getReportGroup());
    groupTF.textProperty().addListener((observable, oldValue, newValue) -> parentTab.contentChanged());
    groupAndTypeBox.getChildren().addAll(new Label("Group"), groupTF);
    typeCB = new ComboBox<>();
    typeCB.getItems().addAll(ReportType.values());
    log.info("Setting report type to {}", muninReport.getReportType());
    //typeCB.getSelectionModel().select(muninReport.getReportType());
    typeCB.setValue(muninReport.getReportType());
    typeCB.setOnAction(ae -> parentTab.contentChanged());

    groupAndTypeBox.getChildren().addAll(new Label("Type"), typeCB);

    inputTA = new XmlTextArea(parentTab);
    if (muninReport.getInputContent() != null) {
      inputTA.replaceContentText(0, 0, muninReport.getInputContent());
    }
    inputTA.textProperty().addListener((observable, oldValue, newValue) -> parentTab.contentChanged());
    VBox.setVgrow(inputTA, Priority.ALWAYS);
    vBox.getChildren().addAll(new Label("Input parameters"), inputTA);

    setContent(vBox);
  }

  public String getReportName() {
    return reportNameTF.getText();
  }

  public String getDescription() {
    return descriptionTA.getText();
  }

  public String getReportGroup() {
    return groupTF.getText();
  }

  public ReportType getReportType() {
    return typeCB.getValue();
  }

  public String getInputContent() {
    return inputTA.getAllTextContent();
  }

  public void setReportType(ReportType type) {
    typeCB.getSelectionModel().select(type);
  }
}
