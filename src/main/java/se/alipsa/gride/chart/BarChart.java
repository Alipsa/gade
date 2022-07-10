package se.alipsa.gride.chart;

import tech.tablesaw.api.Table;

public class BarChart extends Chart {

  protected ChartType chartType = ChartType.NONE;

  public ChartType getChartType() {
    return chartType;
  }

  public static BarChart create(String title, ChartType chartType, Table... series) {
    validateSeries(series);
    if (chartType == null) {
      throw new IllegalArgumentException("ChartType must be either GROUPED och STACKED");
    }
    BarChart chart = new BarChart();
    chart.title = title;
    chart.series = series;
    chart.chartType = chartType;
    return chart;
  }
}
