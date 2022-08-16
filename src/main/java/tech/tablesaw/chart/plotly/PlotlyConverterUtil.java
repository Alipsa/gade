package tech.tablesaw.chart.plotly;

import tech.tablesaw.chart.Chart;
import tech.tablesaw.api.Table;

public class PlotlyConverterUtil {

  public static Table mergeColumns(Chart chart) {
    return Table.create(
        chart.getTitle(),
        chart.getCategorySeries()
    ).addColumns(chart.getValueSeries());
  }
}
