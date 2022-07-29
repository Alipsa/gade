package se.alipsa.gade.chart.plotly;

import se.alipsa.gade.chart.AreaChart;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.api.AreaPlot;
import tech.tablesaw.plotly.components.Figure;

import static se.alipsa.gade.chart.plotly.PlotlyConverterUtil.mergeColumns;

public class PlotlyAreaChartConverter {

  public static Figure convert(AreaChart chart) {
    Table table = mergeColumns(chart);
    return AreaPlot.create(chart.getTitle(), table, table.column(0).name(), table.column(1).name());
  }

}
