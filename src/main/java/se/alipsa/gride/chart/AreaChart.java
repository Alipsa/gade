package se.alipsa.gride.chart;

import tech.tablesaw.api.Table;

public class AreaChart extends Chart {

  public static AreaChart create(Table table) {
    AreaChart chart = new AreaChart();
    chart.title = table.name();
    if (table.columnCount() != 2) {
      throw new IllegalArgumentException("This table does not contain 2 columns, use one of the other create methods to specify which columns to render");
    }
    //table.col
    return null;
  }
}
