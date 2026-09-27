import java.time.LocalDate
import se.alipsa.matrix.core.Matrix
import se.alipsa.matrix.xchart.BarChart
import se.alipsa.gi.swing.InOut
import static se.alipsa.matrix.core.ListConverter.*

io = new InOut()
empData = Matrix.builder().data(
  emp_id: 1..5,
  emp_name: ['Rick', 'Dan', 'Michelle', 'Ryan', 'Gary'],
  salary: [623.3, 515.2, 611.0, 729.0, 843.25],
  bonus: [12.2, 10.4, 75.2, 19.1, 55.1],
  start_date: toLocalDates('2012-01-01', '2013-09-23', '2014-11-15', '2014-05-11', '2015-03-27')
).types(int, String, BigDecimal, BigDecimal, LocalDate).build()

println empData.content()
chart = BarChart.builder(empData).title('Salaries').x('emp_name').y('salary', 'bonus').stacked().build()
io.display(chart.exportSwing(), 'Stacked salaries')
