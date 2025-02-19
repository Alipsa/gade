package se.alipsa.gade.code.mdtab;

import java.io.FileWriter;
import java.io.OutputStreamWriter;
import se.alipsa.gade.Gade;
import se.alipsa.gade.utils.DocUtil;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.groovy.gmd.Gmd;
import se.alipsa.groovy.gmd.GmdException;

import javax.script.ScriptException;
import java.io.File;
import java.io.FileNotFoundException;

public class MdUtil {
  static Gmd gmd = new Gmd();

  public static void viewMd(Gade gui, String title, String textContent) {
    try {
      String html = gmd.gmdToHtmlDoc(textContent);
      gui.getInoutComponent().viewHtml(html, title);
    } catch (GmdException e) {
      ExceptionAlert.showAlert("Failed to view md", e);
    }
  }
  public static void saveMdAsHtml(File outFile, String textContent) {
    try (FileWriter fw = new FileWriter(outFile)){
      fw.write(gmd.gmdToHtmlDoc(textContent));
    } catch (Exception e) {
      ExceptionAlert.showAlert(e.getMessage(), e);
    }
  }

  public static void saveMdAsPdf(String textContent, File outFile) {
    try {
      gmd.processHtmlAndSaveAsPdf(gmd.gmdToHtmlDoc(textContent), outFile);
    } catch (GmdException e) {
      ExceptionAlert.showAlert("Failed to save md as pdf", e);
    }
  }
}
