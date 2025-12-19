package se.alipsa.gade.inout;

import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.code.CodeComponent;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.TextAreaTab;
import se.alipsa.gade.code.munin.MuninTab;
import se.alipsa.gade.model.MuninReport;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.TikaUtils;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public class FileOpener {

  private CodeComponent codeComponent;

  private static final Logger log = LogManager.getLogger(FileOpener.class);

  public FileOpener(CodeComponent codeComponent) {
    this.codeComponent = codeComponent;
  }

  public TextAreaTab openFile(File file, boolean... openExternalIfUnknownType) {

    final boolean allowOpenExternal = openExternalIfUnknownType.length <= 0 || openExternalIfUnknownType[0];

    String type = guessContentType(file);
    log.info("File ContentType for {} detected as {}", file.getName(), type);
    if (file.isFile()) {
      String fileNameLower = file.getName().toLowerCase();
      if (strEndsWith(fileNameLower, ".java")) {
        return codeComponent.addTab(file, CodeType.JAVA);
      }
      if ("pom.xml".equals(fileNameLower)) {
        return codeComponent.addTab(file, CodeType.MAVEN);
      }
      if (strEndsWith(fileNameLower, ".gradle")) {
        return codeComponent.addTab(file, CodeType.GRADLE);
      }
      if (strEquals(type, "text/x-groovy") || strEndsWith(fileNameLower, ".groovy", ".gvy", ".gy", ".gsh")) {
        return codeComponent.addTab(file, CodeType.GROOVY);
      }
      if (strEquals(type, "text/x-sql", "application/sql") || strEndsWith(fileNameLower, "sql")) {
        return codeComponent.addTab(file, CodeType.SQL);
      }
      if (strEndsWith(fileNameLower, ".js")
          || strEquals(type, "application/javascript", "application/ecmascript", "text/javascript", "text/ecmascript", "application/json")) {
        return codeComponent.addTab(file, CodeType.JAVA_SCRIPT);
      }
      if (strEndsWith(fileNameLower, ".gmd")) {
        return codeComponent.addTab(file, CodeType.GMD);
      }
      if (strEndsWith(fileNameLower, ".md") || strEndsWith(fileNameLower, ".rmd")) {
        return codeComponent.addTab(file, CodeType.MD);
      }
      if (strEndsWith(fileNameLower, ".md") || strEndsWith(fileNameLower, MuninReport.FILE_EXTENSION)) {
        if (MuninTab.isSupported(file)) {
          return codeComponent.addTab(MuninTab.fromFile(file));
        } else {
          codeComponent.addTab(file, CodeType.XML);
        }
      }
      if (strEquals(type, "application/x-sas") || strEndsWith(fileNameLower, ".sas")) {
        return codeComponent.addTab(file, CodeType.SAS);
      }
      if (strEndsWith(fileNameLower, ".r", ".s") || strEquals(type, "text/x-rsrc")) {
        return codeComponent.addTab(file, CodeType.R);
      }
      if ( strEquals(type, "application/xml", "text/xml", "text/html")
          || strEndsWith(type, "+xml")
          // in case an xml declaration was omitted or empty file:
          || strEndsWith(fileNameLower,".xml")
          || strEndsWith(fileNameLower,".html")){
        return codeComponent.addTab(file, CodeType.XML);
      }
      if (strStartsWith(type, "text")
                 || strEquals(type, "application/x-bat",
          "application/x-sh")
                 || "namespace".equals(fileNameLower)
                 || "description".equals(fileNameLower)
                 || strEndsWith(fileNameLower, ".txt", ".csv", ".gitignore", ".properties", "props")) {
        return codeComponent.addTab(file, CodeType.TXT);
      }
      if (allowOpenExternal && isDesktopSupported()) {
        log.info("Try to open {} in associated application", file.getName());
        openApplicationExternal(file);
      } else {
        Alerts.info("Unknown file type",
            "Unknown file type, not sure what to do with " + file.getName());
      }
    }
    return null;
  }

  private boolean isDesktopSupported() {
    try {
      return Desktop.isDesktopSupported();
    } catch (Exception e) {
      return false;
    }
  }

  private String guessContentType(File file) {
    final String unknown = "unknown";
    if (!file.exists() || file.length() == 0) {
      return unknown;
    }
    String type;
    try {
      //type = Files.probeContentType(file.toPath());
      type = TikaUtils.instance().detectContentType(file);
    } catch (IOException e) {
      e.printStackTrace();
      return unknown;
    }
    if (type != null) {
      return type;
    } else {
      return unknown;
    }
  }

  private void openApplicationExternal(File file) {
    Task<Void> task = new Task<>() {
      @Override
      protected Void call() throws Exception {
        Desktop.getDesktop().open(file);
        return null;
      }
    };

    task.setOnFailed(e -> ExceptionAlert.showAlert("Failed to open " + file, task.getException()));
    Thread appthread = new Thread(task);
    appthread.start();
  }

  private boolean strStartsWith(String fileNameLower, String... strOpt) {
    for (String start : strOpt) {
      if (fileNameLower.startsWith(start.toLowerCase())) {
        return true;
      }
    }
    return false;
  }

  private boolean strEndsWith(String fileNameLower, String... strOpt) {
    for (String end : strOpt) {
      if (fileNameLower.endsWith(end.toLowerCase())) {
        return true;
      }
    }
    return false;
  }

  private boolean strEquals(String fileNameLower, String... strOpt) {
    for (String str : strOpt) {
      if (fileNameLower.equalsIgnoreCase(str)) {
        return true;
      }
    }
    return false;
  }
}
