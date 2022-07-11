package se.alipsa.gride.chart;

import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

public class BarChart extends Chart {

  protected ChartType chartType = ChartType.NONE;

  public ChartType getChartType() {
    return chartType;
  }

  public static BarChart create(String title, ChartType chartType, Table... series) {
    validateSeries(series);
    if (!(ChartType.GROUPED == chartType || ChartType.STACKED == chartType)) {
      throw new IllegalArgumentException("ChartType must be either GROUPED och STACKED");
    }
    BarChart chart = new BarChart();
    chart.title = title;
    chart.series = series;
    chart.chartType = chartType;
    return chart;
  }

  public static BarChart create(String title, ChartType chartType, Column<?> groupColumn, Column<?>... valueColumn) {
    Table[] series = new Table[valueColumn.length];
    for (int i = 0; i < valueColumn.length; i++) {
      var col = valueColumn[i];
      series[i] = Table.create(col.name(), groupColumn).addColumns(col);
    }
    return create(title, chartType, series);
  }
}
