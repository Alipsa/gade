@Grab('se.alipsa.matrix:matrix-core:3.9.0')
@Grab('se.alipsa.matrix:matrix-pict:0.6.0')
import se.alipsa.groovy.svg.io.SvgWriter
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

chart = BarChart.builder(empData)
    .title('Salaries')
    .x('emp_name')
    .y('salary')
    .vertical()
    .legendVisible(true)
    .legendPosition(Style.Position.LEFT)
    .build()
io.displaySvg(SvgWriter.toXml(Plot.svg(chart)), "charts barchart")
file = io.projectFile("barchart.png")
Plot.png(chart, file, 800, 600)
io.display(file)
