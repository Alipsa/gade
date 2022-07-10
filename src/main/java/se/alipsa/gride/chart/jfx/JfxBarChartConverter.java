package se.alipsa.gride.chart.jfx;

import javafx.scene.chart.*;
import se.alipsa.gride.chart.ChartType;

import static se.alipsa.gride.chart.jfx.ConverterUtil.createAxis;
import static se.alipsa.gride.chart.jfx.ConverterUtil.populateSeries;

public class JfxBarChartConverter {

  public static XYChart<?,?> convert(se.alipsa.gride.chart.BarChart chart) {
    var series = chart.getSeries();
    var firstSerie = series[0];
    var col0 = firstSerie.column(0);
    var col1 = firstSerie.column(1);

    Axis<?> xAxis = createAxis(col0);
    Axis<?> yAxis = createAxis(col1);
    XYChart<?,?> fxChart;
    if (ChartType.STACKED == chart.getChartType()) {
      fxChart = new StackedBarChart<>(xAxis, yAxis);
    } else {
      fxChart = new BarChart<>(xAxis, yAxis);
    }
    fxChart.setTitle(chart.getTitle());

    populateSeries(fxChart, series);
    return fxChart;
  }
}
