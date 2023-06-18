import java.time.LocalDate
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.chart.*
import javafx.scene.layout.*
import javafx.scene.shape.Shape

import se.alipsa.groovy.matrix.*
import se.alipsa.groovy.charts.*

import static se.alipsa.groovy.matrix.ListConverter.*


empData = TableMatrix.create(
    emp_id: 1..5,
    emp_name: ["Rick","Dan","Michelle","Ryan","Gary"],
    salary: [623.3,515.2,611.0,729.0,843.25],
    start_date: toLocalDates("2012-01-01", "2013-09-23", "2014-11-15", "2014-05-11", "2015-03-27"),
    [int, String, Number, LocalDate]
)

chart = se.alipsa.groovy.charts.BarChart.createVertical("Salaries", empData, "emp_name", ChartType.NONE, "salary")
io.display(chart, "charts barchart")
file = io.projectFile("barchart.png")

jfxChart = Plot.jfx(chart)
println "Upper: jfx: ${jfxChart.getYAxis().getUpperBound()}"
println "Tickunit: jfx: ${jfxChart.getYAxis().getTickUnit()}"
println ""

Axis copyAxis(Axis axis) {
  if (axis instanceof CategoryAxis) {
    def categoryAxis = axis
    List<String> categories = new ArrayList<>();
    categories.addAll(categoryAxis.getCategories());
    CategoryAxis copy = new CategoryAxis();
    copy.getCategories().addAll(categories);
    return copy;
  }
  if (axis instanceof NumberAxis) {
    def numberAxis = axis
    if (numberAxis.isAutoRanging()) {
      return new NumberAxis();
    } else {
      return new NumberAxis(numberAxis.getLowerBound(), numberAxis.getUpperBound(), numberAxis.getTickUnit());
    }
  }
  println("Support for axis " + axis.getClass() + " not implemented!")
  throw new RuntimeException("Support for axis " + axis.getClass() + " not implemented!");
}

def xAxis = copyAxis(jfxChart.getXAxis());
def yAxis = copyAxis(jfxChart.getYAxis());
copy = new BarChart(xAxis, yAxis);

copy.setTitle(jfxChart.getTitle());
for (def fromSeries : jfxChart.getData()) {
  def toSerie = new XYChart.Series<>();
  toSerie.setName(fromSeries.getName());
  def dat = []
  for (var data : fromSeries.getData()) {
    dat.add(new XYChart.Data(data.getXValue(), data.getYValue()));
  }
  toSerie.getData().addAll(dat)
  copy.getData().add(toSerie);
}
copy.setStyle(jfxChart.getStyle());
Background background = jfxChart.getBackground();
if (background != null) {
  Background bg = new Background(background.getFills(), background.getImages());
  copy.setBackground(bg);
}
    

for (int i = 0; i < jfxChart.getData().size(); i++) {
      def acSeries = jfxChart.getData().get(i);
      def copySeries = copy.getData().get(i)
      println("${acSeries.getName()} : ${copySeries.getName()}")
      for (int j = 0; j < acSeries.getData().size(); j++) {
        def acData = acSeries.getData().get(j);
        def copyData = copySeries.getData().get(j);
        println("[$i, $j] : ${acData.getXValue()}, ${acData.getYValue()} : ${copyData.getXValue()}, ${copyData.getYValue()}")
      }
}

io.display(jfxChart, false, "jfxChart") 
io.display(copy, false, "copy") 


println "Upper: jfx: ${jfxChart.getYAxis().getUpperBound()}, copy: ${copy.getYAxis().getUpperBound()}"
println "Tickunit: jfx: ${jfxChart.getYAxis().getTickUnit()}, copy ${copy.getYAxis().getTickUnit()}"

