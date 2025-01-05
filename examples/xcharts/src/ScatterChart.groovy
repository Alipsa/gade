import se.alipsa.matrix.datasets.Dataset

data = Dataset.airquality()
scatterChart = ScatterChart.create("Temperature and Ozone", data, "Temp", "Ozone")
io.display(scatterChart, 'jfx plot')

io.display(SwingPlot.swing(scatterChart), "Swingchart")

