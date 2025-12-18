@Grab('tech.tablesaw:tablesaw-core:0.44.4')
@Grab('tech.tablesaw:tablesaw-jsplot:0.44.4')
import static tech.tablesaw.aggregate.AggregateFunctions.*
import tech.tablesaw.api.*
import tech.tablesaw.columns.numbers.*
import tech.tablesaw.plotly.api.VerticalBarPlot
import tech.tablesaw.plotly.components.Figure
import tech.tablesaw.plotly.components.Layout
import tech.tablesaw.plotly.components.Page

Table table = Table.read().csv(new File(io.scriptDir(), "/data/tornadoes_1950-2014.csv"))
NumericColumn<Number> logNInjuries = table.numberColumn("injuries").add(1).logN()
logNInjuries.setName("log injuries")
table.addColumns(logNInjuries)
IntColumn scale = table.intColumn("scale")
scale.set(scale.isLessThan(0), IntColumnType.missingValueIndicator())

summaryTable = table
  .summarize("fatalities", "log injuries", sum)
  .by("Scale")
  .sortAscendingOn("scale")


scaleColumn = summaryTable.column("Scale").asStringColumn()
chart = VerticalBarPlot.create("Tornado Impact", 
  summaryTable,
  "scale",
  Layout.BarMode.STACK,
  "Sum [Fatalities]",
  "Sum [log injuries]"
);
display(chart)

void display(Figure chart) {
  Page page = Page.pageBuilder(chart, "target").build();
  String output = page.asJavascript();
  io.view(output, chart?.layout?.title ?: "plot")
}

// io.view("https://docs.oracle.com/javafx/2/charts/bar-chart.htm#CIHJFHDE")