import java.time.LocalDate
import se.alipsa.matrix.core.Matrix
import se.alipsa.matrix.pict.*

import static se.alipsa.matrix.core.ListConverter.*

empData = Matrix.builder().data(
    emp_id: 1..5,
    emp_name: ["Rick","Dan","Michelle","Ryan","Gary"],
    salary: [623.3,515.2,611.0,729.0,843.25],
    start_date: toLocalDates("2012-01-01", "2013-09-23", "2014-11-15", "2014-05-11", "2015-03-27"),
  ).types(int, String, Number, LocalDate)
  .build()

chart = BarChart.createVertical("Salaries", empData, "emp_name", ChartType.BASIC, "salary")
chart.legend.visible = true
chart.legend.position = se.alipsa.matrix.pict.Style.Position.LEFT
io.display(chart, "charts barchart")

swingChart = Plot.swing(chart)
//c = swingChart.getChart()
//c.getStyler().setLegendPosition(org.knowm.xchart.style.Styler.LegendPosition.OutsideN)
io.display(swingChart)


file = io.projectFile("barchart.png")

jfxChart = Plot.jfx(chart)
io.display(jfxChart, true, "jfx barchart")
jfxFile = io.projectFile("jfxBarchart.png")
io.save(jfxChart, jfxFile, 640, 480) 
io.display(jfxFile)
io.display(jfxChart, true, "jfx barchart2")

io.save(chart, file, 800, 600)
io.display(file)
file2 = io.projectFile("barchart2.png")
io.save(chart, file2)
io.display(file2)

""
