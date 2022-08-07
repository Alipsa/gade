package se.alipsa.gade.code.gmdtab;

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
import se.alipsa.gade.Gade;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.groovy.gmd.Gmd;
import se.alipsa.groovy.gmd.HtmlDecorator;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static se.alipsa.gade.utils.DocUtil.saveHtmlAsPdf;

public class GmdUtil {

  private static final Logger log = LogManager.getLogger();
  static Gmd gmd = new Gmd();


  public static void viewGmd(Gade gui, String title, String textContent) {
    gui.getInoutComponent().viewHtml(convertGmdToHtml(textContent), title);
  }

  private static String convertGmdToHtml(String textContent) {
    return gmd.gmdToHtmlDoc(textContent);
  }

  /**
   * @param target the target pdf file
   * @param textContent the content to write
   */
  public static void saveGmdAsPdf(String textContent, File target) {
    saveHtmlAsPdf(convertGmdToHtml(textContent), target);
  }

  public static void saveGmdAsHtml(File target, String textContent) {
    try {
      String html = convertGmdToHtml(textContent);
      FileUtils.writeToFile(target, html);
    } catch (FileNotFoundException e) {
      ExceptionAlert.showAlert(e.getMessage(), e);
    }
  }
}
