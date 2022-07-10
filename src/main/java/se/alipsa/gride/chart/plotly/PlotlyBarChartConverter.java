package se.alipsa.gride.chart.plotly;

import se.alipsa.gride.chart.BarChart;
import se.alipsa.gride.chart.Chart;
import se.alipsa.gride.chart.ChartType;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.api.HorizontalBarPlot;
import tech.tablesaw.plotly.api.VerticalBarPlot;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;

import static se.alipsa.gride.chart.DataType.isCategorical;

public class PlotlyBarChartConverter {

  public static Figure convert(BarChart chart) {
    Table first = chart.getSeries()[0];
    Layout.BarMode barMode = null;
    if (ChartType.GROUPED.equals(chart.getChartType())) {
      barMode = Layout.BarMode.GROUP;
    } else if (ChartType.STACKED.equals(chart.getChartType())) {
      barMode = Layout.BarMode.STACK;
    }
    var col0 = first.column(0);
    var col1 = first.column(1);
    // TODO merge all series into one table and then use col1->colN as columns names in the last parameter
    if (isCategorical(col0)) {
      return HorizontalBarPlot.create(
          first.name(),
          first,
          col0.name(),
          barMode,
          col1.name()
      );
    } else {
      return VerticalBarPlot.create(
          first.name(),
          first,
          col0.name(),
          barMode,
          col1.name()
      );
    }
  }
}
