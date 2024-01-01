import se.alipsa.groovy.datasets.Dataset
import se.alipsa.groovy.charts.*

data = Dataset.airquality()
scatterChart = ScatterChart.create("Temperature and Ozone", data, "Temp", "Ozone")
io.display(scatterChart, 'jfx plot')

io.display(SwingPlot.swing(scatterChart), "Swingchart")

