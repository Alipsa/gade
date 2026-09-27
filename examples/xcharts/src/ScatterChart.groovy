import se.alipsa.matrix.datasets.Dataset
import se.alipsa.matrix.xchart.ScatterChart

data = Dataset.airquality()
chart = ScatterChart.builder(data).title('Temperature and Ozone').x('Temp').y('Ozone').build()
io.display(chart.exportSwing(), 'Temperature and Ozone')
