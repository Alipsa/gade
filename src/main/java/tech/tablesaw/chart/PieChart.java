package tech.tablesaw.chart;

import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

public class PieChart extends Chart {

  public static PieChart create(String title, Column<?> groupCol, Column<?> numberCol){
    PieChart chart = new PieChart();
    chart.categorySeries = groupCol;
    chart.valueSeries = new Column[] {numberCol};
    chart.title = title;
    return chart;
  }

  public static PieChart create(Table table, String groupColName, String numberColName){
    return create(table.name(), table, groupColName, numberColName);
  }

  public static PieChart create(String title, Table table, String groupColName, String numberColName){
    return create(title, table.column(groupColName), table.column(numberColName));
  }
}
