import java.time.LocalDate
import se.alipsa.groovy.matrix.*
import se.alipsa.groovy.charts.*

import static se.alipsa.groovy.matrix.ListConverter.*


empData = new Matrix(
    emp_id: 1..5,
    emp_name: ["Rick","Dan","Michelle","Ryan","Gary"],
    salary: [623.3,515.2,611.0,729.0,843.25],
    start_date: toLocalDates("2012-01-01", "2013-09-23", "2014-11-15", "2014-05-11", "2015-03-27"),
    [int, String, Number, LocalDate]
)

chart = PieChart.create("Salaries", empData, "emp_name", "salary")
chart.style.plotBackgroundColor = new java.awt.Color(30, 30, 128)
chart.style.chartBackgroundColor = new java.awt.Color(60, 100, 170)
chart.style.legendBackgroundColor = new java.awt.Color(80, 120, 200)
chart.style.legendVisible = true
chart.style.legendPosition = 'RIGHT'
chart.style.titleVisible = true

// show jfx and swing plotting
io.display(chart, "jfx piechart")
swingChart = se.alipsa.groovy.charts.SwingPlot.swing(chart)
io.display(swingChart, 'swingchart')

// save to file
file = io.projectFile("piechart.png")
Plot.png(chart, file, 542, 345)
io.display(file)
file2 = io.projectFile("piechart2.png")
io.save(chart, file2)
io.display(file2)