package se.alipsa.grade.chart.plotly;

import se.alipsa.grade.chart.Chart;
import tech.tablesaw.api.Table;

public class PlotlyConverterUtil {

  public static Table mergeColumns(Chart chart) {
    return Table.create(
        chart.getTitle(),
        chart.getCategorySeries()
    ).addColumns(chart.getValueSeries());
  }
}
