import tech.tablesaw.api.Table

table = Table.read().csv(new File(io.projectDir(), "/data/sessions.csv"))
table.setName("Full")

chart = se.alipsa.grade.chart.AreaChart.create(
  "dbsessions", 
  table.column("user_name"),
  table.column("sessions"), 
  table.column("sessions").multiply(0.5))
io.display(chart)

figure = se.alipsa.grade.chart.Plot.jsPlot(chart)
io.display(figure, "plotly")
//tech.tablesaw.plotly.Plot.show(figure)
//inout.viewer("/home/per/programs/grade/testoutput/output15b71368-3e8b-42c5-9b1a-1f894c5dda56.html")
