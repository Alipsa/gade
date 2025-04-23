package se.alipsa.gade.code.mdtab;

import java.io.FileWriter;
import se.alipsa.gade.Gade;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gmd.core.Gmd;
import se.alipsa.gmd.core.GmdException;

import java.io.File;

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
      //gmd.processHtmlAndSaveAsPdf(gmd.gmdToHtmlDoc(textContent), outFile);
      gmd.gmdToPdf(textContent, outFile);
    } catch (GmdException e) {
      ExceptionAlert.showAlert("Failed to save md as pdf", e);
    }
  }
}
