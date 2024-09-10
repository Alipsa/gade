package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static se.alipsa.groovy.matrix.ListConverter.toLocalDates;

import javafx.embed.swing.JFXPanel;
import javafx.scene.Node;
import javafx.scene.chart.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.utils.DeepCopier;
import se.alipsa.groovy.charts.ChartType;
import se.alipsa.groovy.charts.Plot;
import se.alipsa.groovy.matrix.Matrix;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeepCopyTest {

  @BeforeAll
  public static void init() {
    new JFXPanel();
  }

  @Test
  public void testDeepCopyAreaChart() {
    final NumberAxis xAxis = new NumberAxis(1, 31, 1);
    final NumberAxis yAxis = new NumberAxis();
    final AreaChart<Number, Number> ac =
        new AreaChart<>(xAxis, yAxis);
    ac.setTitle("Temperature Monitoring (in Degrees C)");

    XYChart.Series seriesApril = new XYChart.Series();
    seriesApril.setName("April");
    seriesApril.getData().add(new XYChart.Data(1, 4));
    seriesApril.getData().add(new XYChart.Data(3, 10));
    seriesApril.getData().add(new XYChart.Data(6, 15));
    seriesApril.getData().add(new XYChart.Data(9, 8));
    seriesApril.getData().add(new XYChart.Data(12, 5));
    seriesApril.getData().add(new XYChart.Data(15, 18));
    seriesApril.getData().add(new XYChart.Data(18, 15));
    seriesApril.getData().add(new XYChart.Data(21, 13));
    seriesApril.getData().add(new XYChart.Data(24, 19));
    seriesApril.getData().add(new XYChart.Data(27, 21));
    seriesApril.getData().add(new XYChart.Data(30, 21));

    XYChart.Series seriesMay = new XYChart.Series();
    seriesMay.setName("May");
    seriesMay.getData().add(new XYChart.Data(1, 20));
    seriesMay.getData().add(new XYChart.Data(3, 15));
    seriesMay.getData().add(new XYChart.Data(6, 13));
    seriesMay.getData().add(new XYChart.Data(9, 12));
    seriesMay.getData().add(new XYChart.Data(12, 14));
    seriesMay.getData().add(new XYChart.Data(15, 18));
    seriesMay.getData().add(new XYChart.Data(18, 25));
    seriesMay.getData().add(new XYChart.Data(21, 25));
    seriesMay.getData().add(new XYChart.Data(24, 23));
    seriesMay.getData().add(new XYChart.Data(27, 26));
    seriesMay.getData().add(new XYChart.Data(31, 26));
    ac.getData().addAll(seriesApril, seriesMay);

    AreaChart copy = DeepCopier.deepCopy(ac);
    assertEquals(ac.getStyle(), copy.getStyle(), "Style");
    assertEquals(ac.getTitle(), copy.getTitle(), "Title");
    assertEquals(ac.getData().size(), copy.getData().size(), "Data size");
    for (int i = 0; i < ac.getData().size(); i++) {
      var acSeries = ac.getData().get(i);
      XYChart.Series copySeries = (XYChart.Series) copy.getData().get(i);
      assertEquals(acSeries.getName(), copySeries.getName());
      for (int j = 0; j < acSeries.getData().size(); j++) {
        var acData = acSeries.getData().get(j);
        var copyData = (XYChart.Data) copySeries.getData().get(j);
        assertEquals(acData.getXValue(), copyData.getXValue(), "XYChart.Data for index " + j);
        assertEquals(acData.getYValue(), copyData.getYValue());
      }
    }
  }

  @Test
  public void testCopyBarChart() {
    final String austria = "Austria";
    final String brazil = "Brazil";
    final String france = "France";
    final String italy = "Italy";
    final String usa = "USA";
    final CategoryAxis xAxis = new CategoryAxis();
    final NumberAxis yAxis = new NumberAxis();
    final BarChart<String, Number> bc =
        new BarChart<String, Number>(xAxis, yAxis);
    bc.setTitle("Country Summary");
    xAxis.setLabel("Country");
    yAxis.setLabel("Value");

    XYChart.Series series1 = new XYChart.Series();
    series1.setName("2003");
    series1.getData().add(new XYChart.Data(austria, 25601.34));
    series1.getData().add(new XYChart.Data(brazil, 20148.82));
    series1.getData().add(new XYChart.Data(france, 10000));
    series1.getData().add(new XYChart.Data(italy, 35407.15));
    series1.getData().add(new XYChart.Data(usa, 12000));

    XYChart.Series series2 = new XYChart.Series();
    series2.setName("2004");
    series2.getData().add(new XYChart.Data(austria, 57401.85));
    series2.getData().add(new XYChart.Data(brazil, 41941.19));
    series2.getData().add(new XYChart.Data(france, 45263.37));
    series2.getData().add(new XYChart.Data(italy, 117320.16));
    series2.getData().add(new XYChart.Data(usa, 14845.27));

    XYChart.Series series3 = new XYChart.Series();
    series3.setName("2005");
    series3.getData().add(new XYChart.Data(austria, 45000.65));
    series3.getData().add(new XYChart.Data(brazil, 44835.76));
    series3.getData().add(new XYChart.Data(france, 18722.18));
    series3.getData().add(new XYChart.Data(italy, 17557.31));
    series3.getData().add(new XYChart.Data(usa, 92633.68));

    bc.getData().addAll(series1, series2, series3);

    BarChart copy = DeepCopier.deepCopy(bc);
    assertEquals(bc.getStyle(), copy.getStyle(), "Style");
    assertEquals(bc.getTitle(), copy.getTitle(), "Title");
    assertEquals(bc.getData().size(), copy.getData().size(), "Data size");
    for (int i = 0; i < bc.getData().size(); i++) {
      var acSeries = bc.getData().get(i);
      XYChart.Series copySeries = (XYChart.Series) copy.getData().get(i);
      assertEquals(acSeries.getName(), copySeries.getName());
      for (int j = 0; j < acSeries.getData().size(); j++) {
        var acData = acSeries.getData().get(j);
        var copyData = (XYChart.Data) copySeries.getData().get(j);
        assertEquals(acData.getXValue(), copyData.getXValue(), "XYChart.Data for index " + j);
        assertEquals(acData.getYValue(), copyData.getYValue());
      }
    }
    assertEquals(((NumberAxis) bc.getYAxis()).getUpperBound(), ((NumberAxis) copy.getYAxis()).getUpperBound(), "Y axis Upper bound");
    assertEquals(((NumberAxis) bc.getYAxis()).getLowerBound(), ((NumberAxis) copy.getYAxis()).getLowerBound(), "Y axis Lower bound");
    assertEquals(((NumberAxis) bc.getYAxis()).getTickUnit(), ((NumberAxis) copy.getYAxis()).getTickUnit(), "Y axis Tick Unit");
  }

  @Test
  public void testCopyBarchart2() {

    var empData = Matrix.builder().columns(Map.of(
            "emp_id", Arrays.asList(1, 2, 3, 4, 5),
            "emp_name", Arrays.asList("Rick", "Dan", "Michelle", "Ryan", "Gary"),
            "salary", Arrays.asList(623.3, 515.2, 611.0, 729.0, 843.25),
            "start_date", toLocalDates("2012-01-01", "2013-09-23", "2014-11-15", "2014-05-11", "2015-03-27")))
        .dataTypes(int.class, String.class, Number.class, LocalDate.class)
        .build();

    var chart = se.alipsa.groovy.charts.BarChart.createVertical("Salaries", empData, "emp_name", ChartType.BASIC, "salary");
    Node jChart = Plot.jfx(chart);
    var c = DeepCopier.deepCopy(jChart);
    var jfxChart = (BarChart<?, ?>) jChart;
    var copy = (BarChart<?, ?>) c;
    assertEquals(jfxChart.getStyle(), copy.getStyle(), "Style");
    assertEquals(jfxChart.getTitle(), copy.getTitle(), "Title");
    assertEquals(jfxChart.getData().size(), copy.getData().size(), "Data size");
    assertEquals(jfxChart.getXAxis().getClass(), copy.getXAxis().getClass(), "X axis class");
    assertEquals(jfxChart.getYAxis().getClass(), copy.getYAxis().getClass(), "Y axis class");
    assertEquals(((NumberAxis) jfxChart.getYAxis()).getUpperBound(), ((NumberAxis) copy.getYAxis()).getUpperBound(), "Upper bound");
    for (int i = 0; i < jfxChart.getData().size(); i++) {
      var acSeries = jfxChart.getData().get(i);
      XYChart.Series copySeries = copy.getData().get(i);
      assertEquals(acSeries.getName(), copySeries.getName());
      for (int j = 0; j < acSeries.getData().size(); j++) {
        var acData = acSeries.getData().get(j);
        var copyData = (XYChart.Data) copySeries.getData().get(j);
        assertEquals(acData.getXValue(), copyData.getXValue(), "XYChart.Data for index " + j);
        assertEquals(acData.getYValue(), copyData.getYValue());
      }
    }
    assertEquals(((NumberAxis) jfxChart.getYAxis()).getUpperBound(), ((NumberAxis) copy.getYAxis()).getUpperBound(), "Y axis Upper bound");
    assertEquals(((NumberAxis) jfxChart.getYAxis()).getLowerBound(), ((NumberAxis) copy.getYAxis()).getLowerBound(), "Y axis Lower bound");
    assertEquals(((NumberAxis) jfxChart.getYAxis()).getTickUnit(), ((NumberAxis) copy.getYAxis()).getTickUnit(), "Y axis Tick Unit");
  }

  @Test
  public void testObjectCopy() throws IOException, ClassNotFoundException {
    Integer i = Integer.valueOf("12");
    assertEquals(i, DeepCopier.deepCopy(i));
  }
}
