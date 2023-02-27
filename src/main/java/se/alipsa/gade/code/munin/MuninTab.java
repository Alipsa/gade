package se.alipsa.gade.code.munin;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.gade.Constants;
import se.alipsa.gade.Gade;
import se.alipsa.gade.TaskListener;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.TextAreaTab;
import se.alipsa.gade.code.gmdtab.GmdTextArea;
import se.alipsa.gade.code.groovytab.GroovyTextArea;
import se.alipsa.gade.code.rtab.RTextArea;
import se.alipsa.gade.model.MuninConnection;
import se.alipsa.gade.model.MuninReport;
import se.alipsa.gade.model.ReportType;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.git.GitUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import static se.alipsa.gade.Constants.*;

public abstract class MuninTab extends TextAreaTab implements TaskListener {

  private final CodeTextArea codeTextArea;
  private final MiscTab miscTab;
  private MuninReport muninReport;
  private final TabPane tabPane = new TabPane();

  protected Button viewButton;
  protected Button publishButton;

  private static final Logger log = LogManager.getLogger(MuninTab.class);

  private static final XmlMapper mapper = new XmlMapper();

  static {
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
  }
  public static boolean isSupported(File file) {
    try {
      MuninReport report = mapper.readValue(file, MuninReport.class);
      return report.getReportType() == ReportType.GMD || report.getReportType() == ReportType.GROOVY;
    } catch (IOException e) {
      return false;
    }
  }

  public static MuninTab fromFile(File file) {

    try {
      MuninReport report = mapper.readValue(file, MuninReport.class);
      if (ReportType.GMD.equals(report.getReportType())) {
        return new MuninGmdTab(Gade.instance(), report);
      } else if (ReportType.GROOVY.equals(report.getReportType())) {
        return new MuninGroovyTab(Gade.instance(), report);
      } else {
        Alerts.warn("Unknown report type", "Dont know how to process " + report.getReportType());
        throw new IllegalArgumentException("Unknown report type " + report.getReportType());
      }
    } catch (IOException e) {
      ExceptionAlert.showAlert("Error reading mr file", e);
    }
    return null;
  }

  public MuninTab(Gade gui, MuninReport report) {
    super(gui, mapToCodeType(report.getReportType()));
    muninReport = report;
    codeTextArea = getCodeType() == CodeType.GMD ? new GmdTextArea(this) : new GroovyTextArea(this);
    miscTab = new MiscTab(this);
    setTitle(report.getReportName());

    saveButton.setOnAction(a -> saveContent());

    viewButton = new Button();
    viewButton.setGraphic(new ImageView(IMG_VIEW));
    viewButton.setTooltip(new Tooltip("Render and view"));
    viewButton.setOnAction(this::viewAction);
    buttonPane.getChildren().add(viewButton);

    publishButton = new Button();
    publishButton.setGraphic(new ImageView(IMG_PUBLISH));
    publishButton.setTooltip(new Tooltip("Publish to server"));
    publishButton.setOnAction(this::publishReport);
    buttonPane.getChildren().add(publishButton);

    VirtualizedScrollPane<CodeTextArea> vPane = new VirtualizedScrollPane<>(codeTextArea);
    tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    Tab codeTab = new Tab("code");
    codeTab.setContent(vPane);
    tabPane.getTabs().add(codeTab);
    tabPane.getTabs().add(miscTab);
    pane.setCenter(tabPane);
    //gui.getEnvironmentComponent().addContextFunctionsUpdateListener(codeTextArea);
    //setOnClosed(e -> gui.getEnvironmentComponent().removeContextFunctionsUpdateListener(codeTextArea));
  }

  private static CodeType mapToCodeType(ReportType reportType) {
    return ReportType.GMD.equals(reportType) ? CodeType.GMD : CodeType.GROOVY;
  }

  private void saveContent() {
    muninReport = updateAndGetMuninReport();
    File file = getFile();
    if (file == null) {
      file = gui.getMainMenu().promptForFile("Munin report file",
          MuninReport.FILE_EXTENSION,
          muninReport.getReportName() +  MuninReport.FILE_EXTENSION);
      if (file == null) {
        return;
      }
    }
    try {
      saveFile(muninReport, file);
      Git git = gui.getInoutComponent().getGit();
      if(getTreeItem() != null && git != null) {
        String path = GitUtils.asRelativePath(getFile(), gui.getInoutComponent().projectDir());
        GitUtils.colorNode(git, path, getTreeItem());
      }
    } catch (IOException e) {
      ExceptionAlert.showAlert("Failed to save file " + file, e);
    }
  }

  protected void saveFile(MuninReport report, File file) throws IOException {
    boolean fileExisted = file.exists();
    mapper.writeValue(file, report);
    setTitle(file.getName());
    if (!fileExisted) {
      gui.getInoutComponent().fileAdded(file);
    }
    gui.getCodeComponent().fileSaved(file);
    contentSaved();
  }

  private void publishReport(ActionEvent actionEvent) {
    muninReport = updateAndGetMuninReport();
    String reportName = muninReport.getReportName();
    if (reportName == null || reportName.trim().length() == 0
        || DEFAULT_GROOVY_REPORT_NAME.equals(reportName) || DEFAULT_GMD_REPORT_NAME.equals(reportName)) {
      Alerts.warn("Missing report information", "You must specify a report name before it can be published");
      tabPane.getSelectionModel().select(miscTab);
      return;
    }

    MuninConnection muninConnection = getOrPromptForMuninConnection();

    PublishDialog dialog = new PublishDialog(gui, muninConnection, this);
    dialog.showAndWait();
  }

  abstract void viewAction(ActionEvent actionEvent);

  @Override
  public File getFile() {
    return codeTextArea.getFile();
  }

  @Override
  public void setFile(File file) {
    codeTextArea.setFile(file);
  }

  @Override
  public String getTextContent() {
    return codeTextArea.getTextContent();
  }

  @Override
  public String getAllTextContent() {
    return codeTextArea.getAllTextContent();
  }

  @Override
  public void replaceContentText(int start, int end, String content) {
    codeTextArea.replaceContentText(start, end, content);
  }

  @Override
  public void replaceContentText(String content, boolean isReadFromFile) {
    codeTextArea.replaceContentText(content, isReadFromFile);
  }

  @Override
  public void taskStarted() {
    viewButton.setDisable(true);
  }

  @Override
  public void taskEnded() {
    viewButton.setDisable(false);
  }

  @Override
  public CodeTextArea getCodeArea() {
    return codeTextArea;
  }

  public MuninReport getMuninReport() {
    return muninReport;
  }

  public MuninReport updateAndGetMuninReport() {
    muninReport.setDefinition(codeTextArea.getAllTextContent());
    muninReport.setReportName(miscTab.getReportName());
    muninReport.setDescription(miscTab.getDescription());
    muninReport.setReportGroup(miscTab.getReportGroup());
    muninReport.setReportType(miscTab.getReportType());
    muninReport.setInputContent(miscTab.getInputContent());
    return muninReport;
  }

  public MiscTab getMiscTab() {
    return miscTab;
  }

  protected MuninConnection getMuninConnection() {
    return (MuninConnection)gui.getSessionObject(Constants.SESSION_MUNIN_CONNECTION);
  }

  protected MuninConnection getOrPromptForMuninConnection() {
    MuninConnection muninConnection = getMuninConnection();
    if (muninConnection == null) {
      final FutureTask<MuninConnection> query = new FutureTask<>(() ->  gui.getMainMenu().configureMuninConnection());
      Platform.runLater(query);
      try {
        muninConnection = query.get();
      } catch (InterruptedException | ExecutionException e) {
        ExceptionAlert.showAlert("Failed to get munin connection", e);
      }
    }
    return muninConnection;
  }
}
