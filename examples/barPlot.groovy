import static tech.tablesaw.aggregate.AggregateFunctions.*
import tech.tablesaw.api.*
import tech.tablesaw.columns.numbers.*
import tech.tablesaw.plotly.api.*
import tech.tablesaw.plotly.components.*
import se.alipsa.gride.chart.*

Table table = Table.read().csv(new File(inout.scriptDir(), "/data/tornadoes_1950-2014.csv"))
NumericColumn<?> logNInjuries = table.numberColumn("injuries").add(1).logN()
logNInjuries.setName("log injuries")
table.addColumns(logNInjuries)
IntColumn scale = table.intColumn("scale")
scale.set(scale.isLessThan(0), IntColumnType.missingValueIndicator())

summaryTable = table
  .summarize("fatalities", "log injuries", sum)
  .by("Scale")
  .sortAscendingOn("scale")


scaleColumn = summaryTable.column("Scale").asStringColumn()
chart = BarChart.create("Tornado Impact", ChartType.STACKED, ChartDirection.HORIZONTAL,
  scaleColumn,
  summaryTable.column("sum [log injuries]"),
  summaryTable.column("Sum [Fatalities]")
);
inout.display(chart)


figure = se.alipsa.gride.chart.Plot.jsPlot(chart)
inout.display(figure, "plotly")

/*
inout.display(
  tech.tablesaw.plotly.api.HorizontalBarPlot.create(
    "Tornado Impact",
    summaryTable,
    "scale",
    tech.tablesaw.plotly.components.Layout.BarMode.STACK,
    "Sum [log injuries]",
    "Sum [Fatalities]",
  ), "manual"
)
*/
// inout.view("https://docs.oracle.com/javafx/2/charts/bar-chart.htm#CIHJFHDE")