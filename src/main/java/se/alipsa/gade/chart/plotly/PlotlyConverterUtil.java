package se.alipsa.gade.chart.plotly;

import se.alipsa.gade.chart.Chart;
import tech.tablesaw.api.Table;

public class PlotlyConverterUtil {

  public static Table mergeColumns(Chart chart) {
    return Table.create(
        chart.getTitle(),
        chart.getCategorySeries()
    ).addColumns(chart.getValueSeries());
  }
}
