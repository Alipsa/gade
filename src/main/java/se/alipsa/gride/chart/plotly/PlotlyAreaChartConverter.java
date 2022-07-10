package se.alipsa.gride.chart.plotly;

import se.alipsa.gride.chart.AreaChart;
import se.alipsa.gride.chart.Chart;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.api.AreaPlot;
import tech.tablesaw.plotly.components.Figure;

public class PlotlyAreaChartConverter {

  public static Figure convert(AreaChart chart) {
    Table first = chart.getSeries()[0];
    return AreaPlot.create(chart.getTitle(), first, first.column(0).name(), first.column(1).name());
  }
}
