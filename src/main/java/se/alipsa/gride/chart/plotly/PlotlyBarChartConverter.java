package se.alipsa.gride.chart.plotly;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gride.chart.BarChart;
import se.alipsa.gride.chart.ChartType;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.api.HorizontalBarPlot;
import tech.tablesaw.plotly.api.VerticalBarPlot;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;

import static se.alipsa.gride.chart.DataType.isCategorical;

public class PlotlyBarChartConverter {

  private static final Logger log = LogManager.getLogger();

public static Figure convert(BarChart chart) {
    Table[] series = chart.getSeries();
    log.info("Series data is");
    for (Table t : series) {
      log.info(t.columnNames());
    }
    Table first = series[0];
    int numSeries = series.length;
    Layout.BarMode barMode = null;
    if (ChartType.GROUPED.equals(chart.getChartType())) {
      barMode = Layout.BarMode.GROUP;
    } else if (ChartType.STACKED.equals(chart.getChartType())) {
      barMode = Layout.BarMode.STACK;
    }
    var categoryColumn = first.column(0);
    Table merged = Table.create(chart.getTitle(), categoryColumn);
    String[] valueNames = new String[numSeries];
    for (int i = 0; i < numSeries; i++) {
      var serie = series[i];
      var valueColumn = serie.column(1);
      log.info("adding column {} from series {}", valueColumn.name(), i);
      merged.addColumns(valueColumn);
      valueNames[i] = valueColumn.name();
    }
    // TODO: this will not work
    if (isCategorical(categoryColumn)) {
      return HorizontalBarPlot.create(
          merged.name(),
          merged,
          categoryColumn.name(),
          barMode,
          valueNames
      );
    } else {
      return VerticalBarPlot.create(
          merged.name(),
          merged,
          categoryColumn.name(),
          barMode,
          valueNames
      );
    }
  }
}
