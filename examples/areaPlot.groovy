import tech.tablesaw.api.Table
import se.alipsa.gade.chart.AreaChart

robberies = Table.read().csv(new File(io.scriptDir(), "/data/boston-robberies.csv"));
robberies.setName("Boston Robberies by month: Jan 1966-Oct 1975")

robChart = AreaChart.create(
  robberies.name(),
  robberies.column("Record").asStringColumn(), 
  robberies.column("Robberies")
)
io.display(robChart, "Boston Robberies")

io.display(tech.tablesaw.plotly.api.AreaPlot.create(
  "Boston Robberies by month: Jan 1966-Oct 1975", 
  robberies, 
  "Record", 
  "Robberies"),
  "Boston Robberies"
)
/*
tech.tablesaw.plotly.Plot.show(
    AreaPlot.create(
        "Boston Robberies by month: Jan 1966-Oct 1975", robberies, "Record", "Robberies"));
*/