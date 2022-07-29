package se.alipsa.gade.chart.jfx;

import javafx.scene.chart.*;

import static se.alipsa.gade.chart.jfx.ConverterUtil.populateVerticalSeries;

public class JfxAreaChartConverter {


  public static AreaChart<?,?> convert(se.alipsa.gade.chart.AreaChart chart) {
    Axis<?> xAxis = new CategoryAxis();
    Axis<?> yAxis = new NumberAxis();
    AreaChart<?,?> fxChart = new AreaChart<>(xAxis, yAxis);
    fxChart.setTitle(chart.getTitle());

    populateVerticalSeries(fxChart, chart);
    return fxChart;
  }




}
