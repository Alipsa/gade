package se.alipsa.gride.chart;

import javafx.scene.web.WebView;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

public class Plot {

  public static void pdf(Chart chart, File file) throws IOException {
    try(OutputStream os = Files.newOutputStream(file.toPath())) {
      pdf(chart, os);
    }
  }

  public static void pdf(Chart chart, OutputStream os) {
    throw new RuntimeException("Not yet implemented");
  }

  public static void svg(Chart chart, File file) throws IOException {
    try(OutputStream os = Files.newOutputStream(file.toPath())) {
      pdf(chart, os);
    }
  }

  public static void svg(Chart chart, OutputStream os) {
    throw new RuntimeException("Not yet implemented");
  }

  public static void png(Chart chart, File file) throws IOException {
    try(OutputStream os = Files.newOutputStream(file.toPath())) {
      pdf(chart, os);
    }
  }

  public static void png(Chart chart, OutputStream os) {
    throw new RuntimeException("Not yet implemented");
  }

  public static javafx.scene.chart.Chart jfx(Chart chart) {
    throw new RuntimeException("Not yet implemented");
  }

  /**
   * Uses the plotly module to create html which will be loaded
   * into a WebView. This is useful if the jfx method cannot handle the chart.
   * @param chart the chart to render
   * @return a javafx WebView containing the chart
   */
  public static WebView webView(Chart chart) {
    throw new RuntimeException("Not yet implemented");
  }

  /**
   * You can use this to show it using tech.tablesaw.plotly.Plot.show(jsPlot(chart)) or
   * inout.viewHtml(jsPlot(chart).asJavaScript("divName"))
   * @param chart
   * @return
   */
  public static tech.tablesaw.plotly.components.Figure jsPlot(Chart chart) {
    throw new RuntimeException("Not yet implemented");
  }
}
