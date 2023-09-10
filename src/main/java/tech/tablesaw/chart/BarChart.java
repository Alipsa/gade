package tech.tablesaw.chart;

import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

public class BarChart extends Chart {

  protected ChartType chartType = ChartType.BASIC;
  private ChartDirection direction;

  public ChartType getChartType() {
    return chartType;
  }

  public ChartDirection getDirection() {
    return direction;
  }

  public static BarChart create(String title, ChartType chartType, ChartDirection direction, Column<?> groupColumn, Column<?>... valueColumn) {
    BarChart chart = new BarChart();
    chart.title = title;
    chart.categorySeries = groupColumn;
    chart.valueSeries = valueColumn;
    chart.chartType = chartType;
    chart.direction = direction;
    return chart;
  }

  public static BarChart create(String title, ChartType chartType, Table data, String categoryColumnName, ChartDirection direction, String... valueColumn) {
    Column<?> groupColumn = data.column(categoryColumnName);
    Column<?>[] valueColumns = data.selectColumns(valueColumn).columnArray();
    BarChart chart = new BarChart();
    chart.title = title;
    chart.categorySeries = groupColumn;
    chart.valueSeries = valueColumns;
    chart.chartType = chartType;
    chart.direction = direction;
    return chart;
  }

  /**
   * Similar to Tablesaw ploty factory method i.e.
   *tech.tablesaw.plotly.api.HorizontalBarPlot.create(
   *     "Tornado Impact",
   *     summaryTable,
   *     "scale",
   *     tech.tablesaw.plotly.components.Layout.BarMode.STACK,
   *     "Sum [log injuries]",
   *     "Sum [Fatalities]",
   *   )
   */
  public static BarChart createHorizontal(String title, Table data, String categoryColumnName, ChartType chartType, String... valueColumn) {
    return create(title, chartType, data, categoryColumnName, ChartDirection.HORIZONTAL, valueColumn);
  }

  /**
   * Similar to Tablesaw ploty factory method i.e.
   *tech.tablesaw.plotly.api.VerticalBarPlot.create(
   *     "Tornado Impact",
   *     summaryTable,
   *     "scale",
   *     tech.tablesaw.plotly.components.Layout.BarMode.STACK,
   *     "Sum [log injuries]",
   *     "Sum [Fatalities]",
   *   )
   */
  public static BarChart createVertical(String title, Table data, String categoryColumnName, ChartType chartType, String... valueColumn) {
    return create(title, chartType, data, categoryColumnName, ChartDirection.VERTICAL, valueColumn);
  }
}
