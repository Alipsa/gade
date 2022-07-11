package se.alipsa.gride.chart.plotly;

import se.alipsa.gride.chart.Chart;
import tech.tablesaw.api.Table;

public class PlotlyConverterUtil {

  public static Table mergeColumns(Chart chart) {
    return Table.create(
        chart.getTitle(),
        chart.getCategorySeries()
    ).addColumns(chart.getValueSeries());
  }
}
