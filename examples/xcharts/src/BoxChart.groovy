import org.knowm.xchart.*
import se.alipsa.matrix.core.*
import se.alipsa.matrix.charts.swing.SwingPlot

boxData = new Matrix(
    aaa: [40, 30, 20, 60, 50],
    bbb: [-20, -10, -30, -15, -25],
    ccc: [50, -20, null, null, null],
    [int, int, int]
)
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
for (colName in colNames) {
  chart.addSeries(colName, boxData[colName] - null); 
}

io.display(chart, 'xchart directly')

boxChart = se.alipsa.groovy.charts.BoxChart.create("box plot demo", boxData, colNames)
io.display(SwingPlot.swing(boxChart), 'matrix chart to xchart')

categoryCol = []
valueCol = []
for (colName in colNames) {
  valueCol += boxData[colName]
  categoryCol += [colName]*boxData[colName].size()
}
data = new Matrix('boxchart', ['category', 'value'], [categoryCol, valueCol])
data.content()
boxChart2 = se.alipsa.groovy.charts.BoxChart.create("box plot demo2", data, 'category', 'value')
io.display(boxChart2)
io.display(SwingPlot.swing(boxChart2), 'matrix2 chart to xchart2')