package tech.tablesaw.chart.jfx;

import javafx.scene.chart.*;

public class JfxPieChartConverter {

  public static PieChart convert(tech.tablesaw.chart.PieChart chart) {

    PieChart fxChart = new PieChart();

    var categories = chart.getCategorySeries();
    var values = chart.getValueSeries()[0];
    var data = fxChart.getData();
    for (int i = 0; i < categories.size(); i++) {
      data.add(new PieChart.Data(String.valueOf(categories.get(i)), Double.parseDouble(values.getString(i))));
    }

    fxChart.setTitle(chart.getTitle());
    return fxChart;
  }
}
