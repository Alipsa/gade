package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.embed.swing.JFXPanel;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.utils.DeepCopier;

import java.util.List;

public class DeepCopyTest {

  @Test
  public void testDeepCopyAreaChart() {
    new JFXPanel();
    final NumberAxis xAxis = new NumberAxis(1, 31, 1);
    final NumberAxis yAxis = new NumberAxis();
    final AreaChart<Number,Number> ac =
        new AreaChart<Number,Number>(xAxis,yAxis);
    ac.setTitle("Temperature Monitoring (in Degrees C)");

    XYChart.Series seriesApril= new XYChart.Series();
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
      assertEquals(acSeries.getName(),  copySeries.getName());
      for (int j = 0; j < acSeries.getData().size(); j++) {
        var acData = acSeries.getData().get(j);
        var copyData = (XYChart.Data)copySeries.getData().get(j);
        assertEquals(acData.getXValue(), copyData.getXValue(), "XYChart.Data for index " + j);
        assertEquals(acData.getYValue(), copyData.getYValue());
      }
    }

  }
}
