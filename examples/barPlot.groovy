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

summaryTable = table.summarize("fatalities", "log injuries", sum).by("Scale")

/*
inout.plot(
  HorizontalBarPlot.create(
    "Tornado Impact",
    summaryTable,
    "scale",
    Layout.BarMode.STACK,
    "Sum [Fatalities]",
    "Sum [log injuries]")
)
*/

scaleColumn = summaryTable.column("Scale").asStringColumn()
injuriesData= new Table("Injuries", summaryTable.column("sum [log injuries]"), scaleColumn)
fatalitiesData = new Table("Fatalities", summaryTable.column("Sum [Fatalities]"), scaleColumn)
chart = BarChart.create("Tornado Impact", ChartType.STACKED, injuriesData, fatalitiesData)
inout.plot(chart)

// TODO: does not work
figure = se.alipsa.gride.chart.Plot.jsPlot(chart)
inout.plot(figure, "plotly")

inout.plot(
  tech.tablesaw.plotly.api.HorizontalBarPlot.create(
    "Tornado Impact",
    summaryTable,
    "scale",
    tech.tablesaw.plotly.components.Layout.BarMode.STACK,
    "Sum [Fatalities]",
    "Sum [log injuries]"
  )
)