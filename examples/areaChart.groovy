import tech.tablesaw.api.*
import se.alipsa.grade.chart.*

table = Table.read().csv(new File(io.projectDir(), "/data/sessions.csv"))
table.setName("Full")

chart = se.alipsa.grade.chart.AreaChart.create(
  "dbsessions", 
  table.column("user_name"),
  table.column("sessions"), 
  table.column("sessions").multiply(0.5))
io.display(chart, "jfx")

figure = Plot.jsPlot(chart)
io.display(figure, "plotly")