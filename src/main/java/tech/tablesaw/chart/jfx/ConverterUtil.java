package tech.tablesaw.chart.jfx;

import javafx.scene.chart.*;
import tech.tablesaw.chart.Chart;

public class ConverterUtil {

  public static void populateVerticalSeries(XYChart<?,?> fxChart, Chart data) {
    var series = data.getValueSeries();
    var categories = data.getCategorySeries();
    for (var column : series) {
      XYChart.Series fxSeries = new XYChart.Series();
      fxSeries.setName(column.name());
      for (int i = 0; i < column.size(); i++) {
        fxSeries.getData().add(new XYChart.Data(categories.get(i), column.get(i)));
      }
      fxChart.getData().add(fxSeries);
    }
  }

  public static void populateHorizontalSeries(XYChart<?,?> fxChart, Chart data) {
    var series = data.getValueSeries();
    var categories = data.getCategorySeries();
    for (var column : series) {
      XYChart.Series fxSeries = new XYChart.Series();
      fxSeries.setName(column.name());
      for (int i = 0; i < column.size(); i++) {
        fxSeries.getData().add(new XYChart.Data(column.get(i), categories.get(i)));
      }
      fxChart.getData().add(fxSeries);
    }
  }


}
