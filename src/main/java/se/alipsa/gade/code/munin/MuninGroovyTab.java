package se.alipsa.gade.code.munin;

import com.fasterxml.jackson.core.JsonProcessingException;
import javafx.concurrent.Task;
import javafx.concurrent.Worker.State;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.console.GroovyTask;
import se.alipsa.gade.inout.viewer.ViewHelper;
import se.alipsa.gade.model.MuninReport;
import se.alipsa.gade.model.ReportType;
import se.alipsa.gade.utils.ExceptionAlert;

import java.util.Collections;
import java.util.Map;

import static se.alipsa.gade.code.gmdtab.GmdUtil.BOOTSTRAP_CSS;

public class MuninGroovyTab extends MuninTab {

  private static final Logger log = LogManager.getLogger(MuninGroovyTab.class);

  public MuninGroovyTab(Gade gui, MuninReport report) {
    super(gui, report);
    //getMiscTab().setReportType(ReportType.UNMANAGED);
    if (report.getDefinition() != null) {
      replaceContentText(0,0, report.getDefinition());
    }
  }

  @Override
  void viewAction(ActionEvent actionEvent) {
    ConsoleComponent consoleComponent = gui.getConsoleComponent();
    if (getMuninReport().getInputContent() != null && getMuninReport().getInputContent().trim().length() > 0) {
      //System.out.println("Report has parameters");
      Map<String, Object> inputParams = promptForInputParams();
      if (inputParams != null) {
        for (Map.Entry<String, Object> entry : inputParams.entrySet()) {
          consoleComponent.addVariableToSession(entry.getKey(), entry.getValue());
        }
      }
    }
    gui.setWaitCursor();

    MuninTask task = new MuninTask(this) {
      @Override
      public String execute() throws Exception {
        try {
          String muninBaseUrl;
          if (getMuninConnection() == null) {
            muninBaseUrl = "not configured";
          } else {
            muninBaseUrl = getOrPromptForMuninConnection().target();
          }
          Object result = consoleComponent.runScript(getTextContent(), Collections.singletonMap("muninBaseUrl", muninBaseUrl));
          if (result == null) {
            return null;
          }
          return String.valueOf(result);

        } catch (RuntimeException e) {
          throw new Exception(e);
        }
      }
    };
    task.setOnSucceeded(e -> {
      taskEnded();
      String result = task.getValue();
      if (result != null) {
        gui.getInoutComponent().viewHtmlWithBootstrap(result, getTitle());
        gui.getConsoleComponent().updateEnvironment();
      }
      gui.setNormalCursor();
    });
    task.setOnFailed(e -> {
      taskEnded();
      gui.setNormalCursor();
      Throwable throwable = task.getException();
      Throwable ex = throwable.getCause();
      if (ex == null) {
        ex = throwable;
      }
      ExceptionAlert.showAlert(ex.getMessage(), ex);
    });

    gui.getConsoleComponent().startTaskWhenOthersAreFinished(task, "muninReport");
  }

  private final Console console = new Console();

  private Map<String, Object> promptForInputParams() {

    Stage stage = new Stage();
    WebView browser = new WebView();
    WebEngine webEngine = browser.getEngine();
    webEngine.setUserStyleSheetLocation(BOOTSTRAP_CSS);
    ReportInputResponse reportInput = new ReportInputResponse(stage);
    browser.setContextMenuEnabled(false);
    createContextMenu(browser);
    webEngine.getLoadWorker().stateProperty().addListener(
        (ov, oldState, newState) -> {
          if (newState == State.SUCCEEDED) {
            JSObject win =
                (JSObject) webEngine.executeScript("window");
            win.setMember("app", reportInput);
            win.setMember("console", console);
          }
        }
    );

    String form = "<div class='container pt-2'><form><div class='form-group'>" + getMuninReport().getInputContent() + "</div><button type='submit'>Submit</button></form></div>\n" +
        "<script>\n" +
        "function submitForm(event) {\n" +
        "  event.preventDefault();\n" +
        "  const data = new FormData(event.target);\n" +
        "  const value = Object.fromEntries(data.entries());\n" +
        "  const json = JSON.stringify(value);\n" +
        "  // console.log('Form submitted'); \n" +
        "  app.addParams(json);\n" +
        "}\n" +
        "const form = document.querySelector('form');\n" +
        "form.addEventListener('submit', submitForm);\n" +
        "</script>";
    webEngine.loadContent(form);
    //Scene dialog = new Scene(browser, 640, 480);
    Scene dialog = new Scene(browser);
    stage.initModality(Modality.APPLICATION_MODAL);
    stage.initOwner(gui.getStage());
    stage.setTitle(getMuninReport().getReportName() + ", input parameters");
    //GuiUtils.addStyle(gui, dialog.getStylesheets());
    stage.setScene(dialog);
    stage.sizeToScene();
    stage.showAndWait();

    try {
      //System.out.println("promptForInputParams: params = " + reportInput.asMap());
      return reportInput.asMap();
    } catch (JsonProcessingException e) {
      ExceptionAlert.showAlert("Failed to retrieve params from input form", e);
      return null;
    }
  }

  private void createContextMenu(WebView browser) {
    ContextMenu contextMenu = new ContextMenu();
    WebEngine webEngine = browser.getEngine();
    MenuItem viewSourceMI = new MenuItem("View source");
    viewSourceMI.setOnAction(a -> ViewHelper.viewSource(webEngine, this));
    contextMenu.getItems().add(viewSourceMI);
    browser.setOnMousePressed(e -> {
      if (e.getButton() == MouseButton.SECONDARY) {
        contextMenu.show(browser, e.getScreenX(), e.getScreenY());
      } else {
        contextMenu.hide();
      }
    });
  }

  public static class Console {
    public void log(String text) {
      MuninGroovyTab.log.debug(text);
    }
  }
}
