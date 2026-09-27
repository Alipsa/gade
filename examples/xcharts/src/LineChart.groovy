import se.alipsa.matrix.core.Matrix
import se.alipsa.matrix.xchart.LineChart

salesData = Matrix.builder('Sales data').data(
  yearMonth: [202301, 202302, 202303, 202304, 202305, 202306, 202307, 202308, 202309, 202310, 202311, 202312],
  sales: [23, 14, 15, 24, 34, 36, 22, 45, 43, 17, 29, 25]
).types(Integer, Integer).build()

chart = LineChart.builder(salesData).title('Monthly sales').x('yearMonth').y('sales').build()
io.display(chart.exportSwing(), 'Monthly sales')
