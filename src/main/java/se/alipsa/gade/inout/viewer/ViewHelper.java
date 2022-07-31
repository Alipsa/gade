package se.alipsa.gade.inout.viewer;

import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import org.w3c.dom.Document;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.TextAreaTab;
import se.alipsa.gade.code.xmltab.XmlTextArea;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.GuiUtils;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;

public class ViewHelper {

  public static void createContextMenu(WebView browser, String content, boolean... useLoadOpt) {
    boolean useLoad = useLoadOpt.length > 0 && useLoadOpt[0];
    ContextMenu contextMenu = new ContextMenu();
    WebEngine webEngine = browser.getEngine();

    MenuItem reloadMI = new MenuItem("Reload");
    reloadMI.setOnAction(e -> webEngine.reload());

    MenuItem originalPageMI = new MenuItem("Original page");
    // history only updates for external urls, so we add original back as a fallback
    // e.g when going from a local file to an external link
    originalPageMI.setOnAction(e -> {
      if (useLoad) {
        webEngine.load(content);
      } else {
        webEngine.loadContent(content);
      }
    });

    MenuItem goBackMI = new MenuItem("Go back");
    goBackMI.setOnAction(e -> goBack(webEngine));

    MenuItem goForwardMI = new MenuItem("Go forward");
    goForwardMI.setOnAction(a -> goForward(webEngine));

    MenuItem viewSourceMI = new MenuItem("View source");

    viewSourceMI.setOnAction(a -> viewSource(webEngine, null));

    contextMenu.getItems().addAll(reloadMI, originalPageMI, goBackMI, goForwardMI, viewSourceMI);
    browser.setOnMousePressed(e -> {
      if (e.getButton() == MouseButton.SECONDARY) {
        contextMenu.show(browser, e.getScreenX(), e.getScreenY());
      } else {
        contextMenu.hide();
      }
    });
  }

  private static void goBack(WebEngine webEngine) {
    final WebHistory history = webEngine.getHistory();
    ObservableList<WebHistory.Entry> entryList = history.getEntries();
    int currentIndex = history.getCurrentIndex();
    int backOffset= entryList.size() > 1 && currentIndex > 0 ? -1 : 0;
    history.go(backOffset);
  }

  private static void goForward(WebEngine webEngine) {
    final WebHistory history = webEngine.getHistory();
    ObservableList<WebHistory.Entry> entryList = history.getEntries();
    int currentIndex = history.getCurrentIndex();
    history.go(entryList.size() > 1 && currentIndex < entryList.size() - 1 ? 1 : 0);
  }

  public static void viewSource(WebEngine webEngine, TextAreaTab parent) {
    Document doc = webEngine.getDocument();
    try (StringWriter writer = new StringWriter()){
      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
      transformer.setOutputProperty(OutputKeys.METHOD, "xml");
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

      transformer.transform(new DOMSource(doc), new StreamResult(writer));
      Alert alert = new Alert(Alert.AlertType.INFORMATION);
      alert.setTitle(webEngine.getTitle());
      alert.setHeaderText(null);
      XmlTextArea xmlTextArea = new XmlTextArea(parent);
      xmlTextArea.replaceContentText(0,0, writer.toString());
      alert.getDialogPane().setContent(xmlTextArea);
      alert.setResizable(true);
      alert.getDialogPane().setPrefSize(800, 600);
      GuiUtils.addStyle(Gade.instance(), alert);
      alert.showAndWait();
      //Alerts.info(webEngine.getTitle(), writer.toString());
    } catch (TransformerException | IOException e) {
      ExceptionAlert.showAlert("Failed to read DOM", e);
    }
  }
}
