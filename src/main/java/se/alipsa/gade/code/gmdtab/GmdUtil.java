package se.alipsa.gade.code.gmdtab;

import com.openhtmltopdf.mathmlsupport.MathMLDrawer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Entities;
import org.w3c.dom.Document;
import se.alipsa.gade.Gade;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.gmd.core.Gmd;
import se.alipsa.gmd.core.GmdException;
import se.alipsa.gmd.core.HtmlDecorator;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class GmdUtil {

  private static WebView webView; // Keep a static reference to avoid aggressive cleanup by GC
  private static final Logger log = LogManager.getLogger(GmdUtil.class);

  private static final String HIGHLIGHT_JS_CSS_PATH = "highlightJs/default.css";

  //"META-INF/resources/webjars/bootstrap/5.2.3/css/bootstrap.css";
  private static final String BOOTSTRAP_CSS_PATH = HtmlDecorator.BOOTSTRAP_CSS_PATH;
  public static final String BOOTSTRAP_CSS = resourceUrlExternalForm(BOOTSTRAP_CSS_PATH);
  static Gmd gmd = new Gmd();


  private static String resourceUrlExternalForm(String resource) {
    URL url = FileUtils.getResourceUrl(resource);
    return url == null ? "" : url.toExternalForm();
  }

  public static void viewGmd(Gade gui, String title, String textContent) throws GmdException {
    gui.getInoutComponent().viewHtml(convertGmdToHtml(textContent), title);
  }

  public static void viewGmdWithBootstrap(Gade gui, String title, String textContent) throws GmdException {
    gui.getInoutComponent().viewHtmlWithBootstrap(gmd.gmdToHtml(textContent), title);
  }

  private static String convertGmdToHtml(String textContent) throws GmdException {
    return gmd.gmdToHtmlDoc(textContent);
  }

  /**
   * @param target the target pdf file
   * @param textContent the content to write
   */
  public static void saveGmdAsPdf(String textContent, File target) throws GmdException {

    //gmd.gmdToPdf(textContent, target); //this is much slower due to threading and Grab
    //Gade.instance().getConsoleComponent().addOutput("", target + " saved", true, true);
    Gade.instance().setWaitCursor();
    final ConsoleComponent console = Gade.instance().getConsoleComponent();
    console.addOutput("saveGmdAsPdf", "converting gmd to html", false, true);
    String html = gmd.gmdToHtmlDoc(textContent);

    // Use CountDownLatch to wait for async WebEngine to finish loading
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> errorRef = new AtomicReference<>();

    webView = new WebView();
    webView.getEngine().setOnError(handler ->
        ExceptionAlert.showAlert("Error processing gmd content", handler.getException())
    );
    WebEngine webEngine = webView.getEngine();
    webEngine.setJavaScriptEnabled(true);
    webEngine.setUserStyleSheetLocation(HtmlDecorator.BOOTSTRAP_CSS);
    webEngine.getLoadWorker().stateProperty().addListener(( ov,  oldState, newState) ->  {
      log.debug("WebEngine loading state: {}", newState);
      if (newState == Worker.State.SUCCEEDED) {
        try {
          Document doc = webEngine.getDocument();
          Transformer transformer = TransformerFactory.newInstance().newTransformer();
          transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
          transformer.setOutputProperty(OutputKeys.METHOD, "html");
          transformer.setOutputProperty(OutputKeys.INDENT, "no");
          transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

          StringWriter sw = new StringWriter();
          transformer.transform(new DOMSource(doc), new StreamResult(sw));
          String viewContent = sw.toString();
          // the raw DOM document will not work so we have to parse it again with jsoup to get
          // something that the PdfRendererBuilder (used in gmd) understands
          org.jsoup.nodes.Document doc2 = Jsoup.parse(viewContent);
          doc2.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
              .escapeMode(Entities.EscapeMode.extended)
              .charset(StandardCharsets.UTF_8)
              .prettyPrint(false);
          Document doc3 = new W3CDom().fromJsoup(doc2);
          try (OutputStream os = new FileOutputStream(target)) {
            PdfRendererBuilder builder = new PdfRendererBuilder()
                .useSVGDrawer(new BatikSVGDrawer())
                .useMathMLDrawer(new MathMLDrawer())
                .withW3cDocument(doc3, new File(".").toURI().toString())
                .toStream(os);
            builder.run();
            os.flush(); // Ensure all bytes are written before closing
            long fileSize = new File(target.getAbsolutePath()).length();
            console.addOutput("",
                target + " saved (size: " + fileSize + " bytes)",
                true,
                true
            );
            log.info("PDF saved successfully: {} ({} bytes)", target, fileSize);
          }
        } catch (Throwable t) {
          log.error("Error generating PDF", t);
          errorRef.set(t);
          ExceptionAlert.showAlert("Error processing gmd content", t);
        } finally {
          Gade.instance().setNormalCursor();
          latch.countDown(); // Signal completion
        }
      } else if (newState == Worker.State.FAILED) {
        log.error("WebEngine failed to load content");
        errorRef.set(new GmdException("WebEngine failed to load HTML content"));
        Gade.instance().setNormalCursor();
        latch.countDown(); // Signal completion even on failure
      }
    });
    webEngine.loadContent(html);

    // Wait for PDF generation to complete (max 30 seconds)
    try {
      boolean completed = latch.await(30, TimeUnit.SECONDS);
      if (!completed) {
        Gade.instance().setNormalCursor();
        throw new GmdException("PDF generation timed out after 30 seconds");
      }
      // Rethrow any exception that occurred during PDF generation
      Throwable error = errorRef.get();
      if (error != null) {
        if (error instanceof GmdException) {
          throw (GmdException) error;
        }
        throw new GmdException("Failed to generate PDF: " + error.getMessage(), error);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      Gade.instance().setNormalCursor();
      throw new GmdException("PDF generation was interrupted", e);
    }
  }

  public static void saveGmdAsHtml(File target, String textContent) throws GmdException {
    try {
      String html = convertGmdToHtml(textContent);
      FileUtils.writeToFile(target, html);
    } catch (FileNotFoundException e) {
      ExceptionAlert.showAlert(e.getMessage(), e);
    }
  }

}
