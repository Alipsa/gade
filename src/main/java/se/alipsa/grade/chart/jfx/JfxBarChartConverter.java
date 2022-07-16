package se.alipsa.grade.chart.jfx;

import javafx.scene.chart.*;
import se.alipsa.grade.chart.ChartDirection;
import se.alipsa.grade.chart.ChartType;

import static se.alipsa.grade.chart.jfx.ConverterUtil.*;

public class JfxBarChartConverter {

  public static XYChart<?,?> convert(se.alipsa.grade.chart.BarChart chart) {

    Axis<?> xAxis = new CategoryAxis();
    Axis<?> yAxis = new NumberAxis();

    XYChart<?,?> fxChart;
    if (ChartType.STACKED == chart.getChartType()) {
      if (ChartDirection.HORIZONTAL.equals(chart.getDirection())) {
        //xAxis.setTickLabelRotation(90); TODO: make this a styling option
        fxChart = new StackedBarChart<>(yAxis, xAxis);
        populateHorizontalSeries(fxChart, chart);
      } else {
        fxChart = new StackedBarChart<>(xAxis, yAxis);
        populateVerticalSeries(fxChart, chart);
      }
    } else {
      if (ChartDirection.HORIZONTAL.equals(chart.getDirection())) {
        //xAxis.setTickLabelRotation(90); // TODO: make this a styling option
        fxChart = new BarChart<>(yAxis, xAxis);
        populateHorizontalSeries(fxChart, chart);
      } else {
        fxChart = new BarChart<>(xAxis, yAxis);
        populateVerticalSeries(fxChart, chart);
      }
    }
    fxChart.setTitle(chart.getTitle());


    return fxChart;
  }
}
