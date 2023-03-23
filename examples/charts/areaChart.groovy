import java.time.LocalDate
import se.alipsa.groovy.matrix.*
import se.alipsa.groovy.charts.*

import static se.alipsa.groovy.matrix.ListConverter.*


empData = TableMatrix.create(
    emp_id: 1..5,
    emp_name: ["Rick","Dan","Michelle","Ryan","Gary"],
    salary: [623.3,515.2,611.0,729.0,843.25],
    start_date: toLocalDates("2012-01-01", "2013-09-23", "2014-11-15", "2014-05-11", "2015-03-27"),
    [int, String, Number, LocalDate]
)

chart = AreaChart.create("Salaries", empData, "emp_name", "salary")
io.display(chart, "jfx areachart")
file = io.projectFile("areachart.png")
Plot.png(chart, file)
io.display(file)
file2 = io.projectFile("areachart2.png")
io.save(chart, file2)
io.display(file2)