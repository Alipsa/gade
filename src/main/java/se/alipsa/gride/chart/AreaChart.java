package se.alipsa.gride.chart;

import javafx.collections.ObservableList;
import javafx.scene.chart.Axis;
import javafx.scene.chart.Chart;
import tech.tablesaw.api.Table;

public class AreaChart<X,Y> extends javafx.scene.chart.AreaChart<X,Y>{


  public AreaChart(Axis<X> xAxis, Axis<Y> yAxis) {
    super(xAxis, yAxis);
  }

  public AreaChart(Axis<X> xAxis, Axis<Y> yAxis, ObservableList<Series<X,Y>> data) {
    super(xAxis, yAxis, data);
  }

  public static Chart create(Table table) {
    String title = table.name();
    if (table.columnCount() != 2) {
      throw new IllegalArgumentException("This table does not contain 2 columns, use one of the other create methods to specify which columns to render");
    }
    //table.col
    return null;
  }
}
