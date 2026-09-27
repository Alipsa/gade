import java.time.LocalDate
import se.alipsa.matrix.core.*
import se.alipsa.matrix.pict.*

import static se.alipsa.matrix.core.ListConverter.*


empData = Matrix.builder().data(
    emp_id: 1..5,
    emp_name: ["Rick","Dan","Michelle","Ryan","Gary"],
    salary: [623.3,515.2,611.0,729.0,843.25],
    start_date: toLocalDates("2012-01-01", "2013-09-23", "2014-11-15", "2014-05-11", "2015-03-27"),
  ).types(int, String, Number, LocalDate).build()

chart = PieChart.create("Salaries", empData, "emp_name", "salary")
chart.style.plotBackgroundColor = new java.awt.Color(30, 30, 128)
chart.style.chartBackgroundColor = new java.awt.Color(60, 100, 170)
chart.legend.visible = true
chart.legend.position = se.alipsa.matrix.pict.Style.Position.BOTTOM
chart.style.titleVisible = true

// show jfx and swing plotting
io.display(chart, "jfx piechart")
swingChart = Plot.swing(chart)
io.display(swingChart, 'swingchart')

// save to file
file = io.projectFile("piechart.png")
io.save(chart, file, 542, 345)
io.display(file)
file2 = io.projectFile("piechart2.png")
io.save(chart, file2)
io.display(file2)
