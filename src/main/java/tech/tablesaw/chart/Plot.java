package tech.tablesaw.chart;

import tech.tablesaw.chart.jfx.JfxAreaChartConverter;
import tech.tablesaw.chart.jfx.JfxBarChartConverter;
import tech.tablesaw.chart.jfx.JfxPieChartConverter;
import tech.tablesaw.chart.plotly.PlotlyAreaChartConverter;
import tech.tablesaw.chart.plotly.PlotlyBarChartConverter;
import tech.tablesaw.chart.plotly.PlotlyPieChartConverter;
import tech.tablesaw.plotly.components.Page;

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
    if (chart instanceof AreaChart) {
      return JfxAreaChartConverter.convert((AreaChart) chart);
    } else if (chart instanceof BarChart) {
      return JfxBarChartConverter.convert((BarChart) chart);
    } else if (chart instanceof PieChart) {
      return JfxPieChartConverter.convert((PieChart) chart);
    }
    throw new RuntimeException(chart.getClass().getSimpleName() + " conversion is not yet implemented");
  }

  /**
   * Uses the plotly module to create html which can be loaded
   * into a WebView or browser. This is useful if the jfx method cannot handle the chart.
   * @param chart the chart to render
   * @return a String containing the html for the chart
   */
  public static String html(Chart chart) {
    Page page = Page.pageBuilder(jsPlot(chart), "target").build();
    return page.asJavascript();
  }

  /**
   * You can use this to show it using tech.tablesaw.plotly.Plot.show(jsPlot(chart)) or
   * inout.viewHtml(jsPlot(chart).asJavaScript("divName"))
   * @param chart the chart to render
   * @return a Figure that can be converted into various outputs (String, html file etc.)
   */
  public static tech.tablesaw.plotly.components.Figure jsPlot(Chart chart) {
    if (chart instanceof AreaChart) {
      return PlotlyAreaChartConverter.convert((AreaChart) chart);
    } else if (chart instanceof BarChart) {
      return PlotlyBarChartConverter.convert((BarChart) chart);
    } else if (chart instanceof PieChart) {
      return PlotlyPieChartConverter.convert((PieChart) chart);
    }
    throw new RuntimeException(chart.getClass().getSimpleName() + " conversion is not yet implemented");
  }
}
