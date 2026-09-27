import se.alipsa.matrix.core.Matrix
import se.alipsa.matrix.pict.*

chart = Histogram.create("Airquality.Temp", Matrix.builder().data(new File(io.scriptDir(), "../data/airquality.csv")).build(), "Temp", 5)
io.display(chart, "Histogram")
