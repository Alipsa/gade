package se.alipsa.gade.utils;

import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Entities;
import org.w3c.dom.Document;
import se.alipsa.gmd.core.Gmd;
import se.alipsa.gmd.core.HtmlDecorator;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class DocUtil {

  private static final Logger log = LogManager.getLogger(DocUtil.class);
  private static Transformer transformer;

  static Gmd gmd = new Gmd();

  public static void saveHtmlAsPdf(String html, File target) {

    // We load the html into a web view so that the highlight javascript properly add classes to code parts
    // then we extract the DOM from the web view and use that to produce the PDF
    WebView webview = new WebView();
    final WebEngine webEngine = webview.getEngine();
    webEngine.setJavaScriptEnabled(true);
    webEngine.setUserStyleSheetLocation(HtmlDecorator.BOOTSTRAP_CSS);
    webEngine.getLoadWorker().stateProperty().addListener(
        (ov, oldState, newState) -> {
          if (newState == Worker.State.SUCCEEDED) {
            Document doc = webEngine.getDocument();

            try(OutputStream os = Files.newOutputStream(target.toPath()))  {
              String viewContent = toString(doc);

              // The raw DOM document does not work, we have to parse it again with jsoup to get
              // something that the PdfRendererBuilder (used in gmd) understands
              org.jsoup.nodes.Document doc2 = Jsoup.parse(viewContent);
              doc2.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
                  .escapeMode(Entities.EscapeMode.extended)
                  .charset(StandardCharsets.UTF_8)
                  .prettyPrint(false);
              Document doc3 = new W3CDom().fromJsoup(doc2);
              gmd.htmlToPdf(doc3, os);
              if (log.isDebugEnabled()) {
                FileUtils.writeToFile(new File(target.getParent(), target.getName() + ".html"), toString(doc3));
              }
            } catch (Exception e) {
              ExceptionAlert.showAlert("Failed to create PDF", e);
            }
          }
        });
    webEngine.loadContent(html);
  }

  @NotNull
  public static String toString(Document doc) throws TransformerException {
    if (transformer == null) {
      transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
      transformer.setOutputProperty(OutputKeys.METHOD, "html");
      transformer.setOutputProperty(OutputKeys.INDENT, "no");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    }
    StringWriter sw = new StringWriter();
    transformer.transform(new DOMSource(doc), new StreamResult(sw));
    return sw.toString();
  }
}
