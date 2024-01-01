import se.alipsa.groovy.datasets.*
import se.alipsa.groovy.charts.*

chart = Histogram.create("Mtcars.mpg", Dataset.mtcars(), "mpg", 5)
io.display(chart, "jfx Histogram")


swingChart = SwingPlot.swing(chart)
io.display(swingChart, "Matrix chart to XChart")