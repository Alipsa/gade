import org.knowm.xchart.*
import se.alipsa.matrix.core.*
import se.alipsa.matrix.charts.swing.SwingPlot
import se.alipsa.gi.swing.InOut
import org.knowm.xchart.XChartPanel

io = new InOut()

boxData = Matrix.builder().data(
    aaa: [40, 30, 20, 60, 50],
    bbb: [-20, -10, -30, -15, -25],
    ccc: [50, -20, null, null, null]
  )
  .types(int, int, int)
  .build()
io.view(boxData)

BoxChart chart =
    new BoxChartBuilder()
        .title("box plot demo")
        .xAxisTitle("X")
        .yAxisTitle("Y")
        //.theme(ChartTheme.GGPlot2)
        .build();

// Series
colNames =['aaa', 'bbb', 'ccc']
data = boxData.apply('ccc') {
  it ?: 0   
}
for (colName in colNames) {
  chart.addSeries(colName, data[colName]); 
}

var panel = new XChartPanel<>(chart);
io.display(panel, 'xchart directly')

//io.view(data)
boxChart = se.alipsa.matrix.charts.BoxChart.create("box plot demo", data, colNames)
println boxChart.categorySeries
println boxChart.valueSeries
io.display(SwingPlot.swing(boxChart), 'matrix chart to xchart')

categoryCol = []
valueCol = []
for (colName in colNames) {
  valueCol += boxData[colName]
  categoryCol += [colName]*boxData[colName].size()
}
data = Matrix.builder('boxchart').data(
    'category': categoryCol, 
    'value': valueCol
  ).build()
println data.content()
boxChart2 = se.alipsa.groovy.charts.BoxChart.create("box plot demo2", data, 'category', 'value')
io.display(boxChart2)
io.display(SwingPlot.swing(boxChart2), 'matrix2 chart to xchart2')