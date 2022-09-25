package tech.tablesaw.chart.plotly;

import tech.tablesaw.api.Table;
import tech.tablesaw.chart.PieChart;
import tech.tablesaw.plotly.api.PiePlot;
import tech.tablesaw.plotly.components.Figure;

public class PlotlyPieChartConverter {
  public static Figure convert(PieChart chart) {
    Table merged = PlotlyConverterUtil.mergeColumns(chart);
    String[] valueNames = merged.rejectColumns(0).columnNames().toArray(new String[0]);
    return PiePlot.create(chart.getTitle(), merged, chart.getCategorySeries().name(), valueNames[0]);
  }
}
