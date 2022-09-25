import static tech.tablesaw.aggregate.AggregateFunctions.*
import tech.tablesaw.api.*
import tech.tablesaw.columns.numbers.*
import tech.tablesaw.chart.*
import tech.tablesaw.plotly.api.PiePlot

tornadoes = Table.read().csv(new File(io.scriptDir(), "/data/tornadoes_1950-2014.csv"))
// Get the scale column and replace any values of -9 with the column's missing value indicator
scale = tornadoes.intColumn("scale")
scale.set(scale.isEqualTo(-9), IntColumnType.missingValueIndicator())

// Sum the number of fatalities from each tornado, grouping by scale
fatalities1 = tornadoes.summarize("fatalities", sum).by("scale")
    
// PIE PLOT
// io.javadoc(tech.tablesaw.plotly.api.PiePlot.class)
pieCart = PieChart.create("fatalities by scale", fatalities1, "scale", "sum [fatalities]")

io.display(pieCart, "jfx")

figure = Plot.jsPlot(pieCart)
io.display(figure, "plotly")
