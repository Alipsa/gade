import se.alipsa.matrix.core.Matrix
import se.alipsa.matrix.xchart.BoxChart
import se.alipsa.gi.swing.InOut

io = new InOut()
boxData = Matrix.builder().data(
  aaa: [40, 30, 20, 60, 50],
  bbb: [-20, -10, -30, -15, -25],
  ccc: [50, -20, null, null, null]
).types(Integer, Integer, Integer).build()
io.view(boxData)

chart = BoxChart.builder(boxData).title('Box plot by column').y('aaa', 'bbb', 'ccc').build()
io.display(chart.exportSwing(), 'Box plot by column')

category = []
value = []
boxData.columnNames().each { name ->
  boxData.column(name).findAll { it != null }.each { number ->
    category << name
    value << number
  }
}
longData = Matrix.builder('Box plot by category').data(category: category, value: value)
  .types(String, Integer).build()
chartByCategory = BoxChart.builder(longData).title('Box plot by category')
  .x('category').y('value').build()
io.display(chartByCategory.exportSwing(), 'Box plot by category')
