package se.alipsa.gride.chart.jfx;

import javafx.scene.chart.*;
import se.alipsa.gride.chart.DataType;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

public class ConverterUtil {

  public static Axis<?> createAxis(Column<?> col0) {
    if (DataType.CHARACTER.equals(DataType.dataType(col0.type()))) {
      return new CategoryAxis();
    } else {
      return new NumberAxis();
    }
  }

  public static void populateSeries(XYChart<?,?> fxChart, Table[] series) {
    for (var table : series) {
      XYChart.Series fxSeries = new XYChart.Series();
      fxSeries.setName(table.name());
      table.stream().iterator().forEachRemaining(r ->
          fxSeries.getData().add(new XYChart.Data(r.getObject(0), r.getObject(1)))
      );
      fxChart.getData().add(fxSeries);
    }
  }
}
