package tech.tablesaw.chart;

import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;


public class AreaChart extends Chart {

  public static AreaChart create(Table data) {
    if (data.columnCount() != 2) {
      throw new IllegalArgumentException("Table " + data.name() + " does not contain 2 columns.");
    }
    AreaChart chart = new AreaChart();
    chart.title = data.name();
    chart.categorySeries = data.column(0);
    chart.valueSeries = data.rejectColumns(0).columnArray();
    return chart;
  }

  public static AreaChart create(String title, Column<?> groupColumn, Column<?>... valueColumn) {
    AreaChart chart = new AreaChart();
    chart.title = title;
    chart.categorySeries = groupColumn;
    chart.valueSeries = valueColumn;
    return chart;
  }

  /**
   * AreaPlot.create(
   *         "Boston Robberies by month: Jan 1966-Oct 1975", robberies, "Record", "Robberies")
   */
  public static AreaChart create(String title, Table data, String xCol, String yCol) {
    var xColumn = data.column(xCol);
    var yColumn = data.column(yCol);
    return create(title, xColumn, yColumn);
  }

  /**
   * TODO: figure out how groupCol works
   */
  public static AreaChart create(String title, Table data, String xCol, String yCol, String groupCol) {
    throw new RuntimeException("Not yet implemented");
  }
}
