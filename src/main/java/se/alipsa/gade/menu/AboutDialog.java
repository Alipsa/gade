package se.alipsa.gade.menu;

import groovy.lang.GroovySystem;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.FileUtils;

public class AboutDialog {
  public static void displayAbout() {
    Properties props = new Properties();
    String version = "unknown";
    String releaseTag = "unknown";
    String buildDate = "unknown";
    String matrixCoreVersion = "unknown";
    String matrixStatsVersion = "unknown";
    String matrixDatasetsVersion = "unknown";
    String matrixSpreadsheetVersion = "unknown";
    String matrixJsonVersion = "unknown";
    String matrixCsvVersion = "unknown";
    String matrixSqlVersion = "unknown";
    String matrixChartsVersion = "unknown";
    String matrixParquetVersion = "unknown";
    String matrixBigQueryVersion = "unknown";
    String matrixXchartVersion = "unknown";
    String matrixTablesawVersion = "unknown";
    String matrixGsheetsVersion = "unknown";
    String matrixAvroVersion = "unknown";
    String gmdCoreVersion = "unknown";
    try (InputStream is = Objects.requireNonNull(FileUtils.getResourceUrl("version.properties")).openStream()) {
      props.load(is);
      version = props.getProperty("version");
      releaseTag = props.getProperty("releaseTag");
      buildDate = props.getProperty("buildDate");
      matrixCoreVersion = props.getProperty("matrixCoreVersion");
      matrixStatsVersion = props.getProperty("matrixStatsVersion");
      matrixDatasetsVersion = props.getProperty("matrixDatasetsVersion");
      matrixSpreadsheetVersion = props.getProperty("matrixSpreadsheetVersion");
      matrixJsonVersion = props.getProperty("matrixJsonVersion");
      matrixCsvVersion = props.getProperty("matrixCsvVersion");
      matrixSqlVersion = props.getProperty("matrixSqlVersion");
      matrixChartsVersion = props.getProperty("matrixChartsVersion");
      matrixParquetVersion = props.getProperty("matrixParquetVersion");
      matrixBigQueryVersion = props.getProperty("matrixBigQueryVersion");
      matrixXchartVersion = props.getProperty("matrixXchartVersion");
      matrixTablesawVersion = props.getProperty("matrixTablesawVersion");
      matrixGsheetsVersion = props.getProperty("matrixGsheetsVersion");
      matrixAvroVersion = props.getProperty("matrixAvroVersion");
      gmdCoreVersion = props.getProperty("gmdCoreVersion");
    } catch (IOException e) {
      ExceptionAlert.showAlert("Failed to load properties file", e);
    }
    NashornScriptEngineFactory nashornScriptEngineFactory = new NashornScriptEngineFactory();
    StringBuilder content = new StringBuilder();
    content.append("\n Gade Version: ")
        .append(version)
        .append("\n Gade Release tag: ")
        .append(releaseTag)
        .append("\n Gade Build date: ")
        .append(buildDate)
        .append("\n Java Runtime Version: ")
        .append(System.getProperty("java.runtime.version"))
        .append(" (").append(System.getProperty("os.arch")).append(")")
        .append(")")
        .append("\n JavaFx Version: ").append(System.getProperty("javafx.runtime.version"))
        .append("\n Groovy version: ").append(GroovySystem.getVersion())
        .append("\n Nashorn version: ").append(nashornScriptEngineFactory.getEngineVersion())
        .append(" (").append(nashornScriptEngineFactory.getLanguageName())
        .append(" ").append(nashornScriptEngineFactory.getLanguageVersion()).append(")")
        .append("\n Matrix-core version: ").append(matrixCoreVersion)
        .append("\n Matrix-stats version: ").append(matrixStatsVersion)
        //.append("\n Matrix-spreadsheet version: ").append(matrixSpreadsheetVersion)
        .append("\n Matrix-sql version: ").append(matrixSqlVersion)
        //.append("\n Matrix-csv version: ").append(matrixCsvVersion)
        //.append("\n Matrix-json version: ").append(matrixJsonVersion)
        .append("\n Matrix-xchart version: ").append(matrixXchartVersion)
        //.append("\n Matrix-datasets version: ").append(matrixDatasetsVersion)
        //.append("\n Matrix-avro version: ").append(matrixAvroVersion)
        //.append("\n Matrix-parquet version: ").append(matrixParquetVersion)
        .append("\n Matrix-bigquery version: ").append(matrixBigQueryVersion)
        .append("\n Matrix-charts version: ").append(matrixChartsVersion)
        //.append("\n Matrix-tablesaw version: ").append(matrixTablesawVersion)
        //.append("\n Matrix-gsheets version: ").append(matrixGsheetsVersion)
        .append("\n Gmd Core version: ").append(gmdCoreVersion);

    content.append("\n\n See https://github.com/Alipsa/gade/ for more info or to report issues.");
    Alerts.showInfoAlert("About Gade", content, 640, 450);
  }
}
