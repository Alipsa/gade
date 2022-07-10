package se.alipsa.gride.chart;

import tech.tablesaw.api.Table;


public class AreaChart extends Chart {

  public static AreaChart create(Table data) {
    if (data.columnCount() != 2) {
      throw new IllegalArgumentException("Table " + data.name() + " does not contain 2 columns.");
    }
    AreaChart chart = new AreaChart();
    chart.title = data.name();
    chart.series = new Table[] {data};
    return chart;
  }

  public static AreaChart create(String title, Table... series) {
    validateSeries(series);
    AreaChart chart = new AreaChart();
    chart.title = title;
    chart.series = series;
    return chart;
  }
}
