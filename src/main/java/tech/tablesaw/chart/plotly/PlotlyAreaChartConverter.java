package tech.tablesaw.chart.plotly;

import tech.tablesaw.chart.AreaChart;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.api.AreaPlot;
import tech.tablesaw.plotly.components.Figure;

public class PlotlyAreaChartConverter {

  public static Figure convert(AreaChart chart) {
    Table table = PlotlyConverterUtil.mergeColumns(chart);
    return AreaPlot.create(chart.getTitle(), table, table.column(0).name(), table.column(1).name());
  }

}
