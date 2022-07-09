package se.alipsa.gride.chart.jfx;

import javafx.scene.chart.*;
import se.alipsa.gride.chart.DataType;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.columns.Column;

import static tech.tablesaw.api.ColumnType.STRING;

public class AreaChartConverter {


  public static AreaChart<?,?> convert(se.alipsa.gride.chart.AreaChart chart) {
    var series = chart.getSeries();
    var firstSerie = series[0];
    var col0 = firstSerie.column(0);
    var col1 = firstSerie.column(1);

    Axis<?> xAxis = createAxis(col0);
    Axis<?> yAxis = createAxis(col1);
    AreaChart<?,?> fxChart = new AreaChart<>(xAxis, yAxis);
    fxChart.setTitle(chart.getTitle());

    for (var table : series) {
      XYChart.Series fxSeries = new XYChart.Series();
      fxSeries.setName(table.name());
      table.stream().iterator().forEachRemaining(r ->
          fxSeries.getData().add(new XYChart.Data(r.getObject(0), r.getObject(1)))
      );
      fxChart.getData().add(fxSeries);
    }
    return fxChart;
  }

  private static Axis<?> createAxis(Column<?> col0) {
    if (DataType.CHARACTER.equals(DataType.dataType(col0.type()))) {
      return new CategoryAxis();
    } else {
      return new NumberAxis();
    }
  }

}
