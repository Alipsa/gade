import tech.tablesaw.api.Table
import tech.tablesaw.plotly.Plot
import se.alipsa.gride.chart.AreaChart

Table robberies = Table.read().csv(new File(inout.scriptDir(), "/data/boston-robberies.csv"));
robberies.setName("Boston Robberies by month: Jan 1966-Oct 1975")

robChart = AreaChart.create(robberies.select("Record", "Robberies"))
inout.plot(robChart, "Boston Robberies")

inout.plot(tech.tablesaw.plotly.api.AreaPlot.create(
  "Boston Robberies by month: Jan 1966-Oct 1975", 
  robberies, 
  "Record", 
  "Robberies"),
  "Boston Robberies"
)
/*
Plot.show(
    AreaPlot.create(
        "Boston Robberies by month: Jan 1966-Oct 1975", robberies, "Record", "Robberies"));
*/