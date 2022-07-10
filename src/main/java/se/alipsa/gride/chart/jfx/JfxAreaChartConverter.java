package se.alipsa.gride.chart.jfx;

import javafx.scene.chart.*;
import tech.tablesaw.api.Table;

import static se.alipsa.gride.chart.jfx.ConverterUtil.createAxis;
import static se.alipsa.gride.chart.jfx.ConverterUtil.populateSeries;

public class JfxAreaChartConverter {


  public static AreaChart<?,?> convert(se.alipsa.gride.chart.AreaChart chart) {
    var series = chart.getSeries();
    var firstSerie = series[0];
    var col0 = firstSerie.column(0);
    var col1 = firstSerie.column(1);

    Axis<?> xAxis = createAxis(col0);
    Axis<?> yAxis = createAxis(col1);
    AreaChart<?,?> fxChart = new AreaChart<>(xAxis, yAxis);
    fxChart.setTitle(chart.getTitle());

    populateSeries(fxChart, series);
    return fxChart;
  }




}
