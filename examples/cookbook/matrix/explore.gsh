import se.alipsa.groovy.matrix.*
import static se.alipsa.groovy.matrix.Stat.*
import se.alipsa.groovy.charts.*

aq = Matrix.builder()
    .data(new File(io.scriptDir(), "../../data/airquality.csv"))
    .build()

aq.name
// print basic layout of the table
println str(aq)

aq = aq.convert(
  [
  Ozone: Integer,
  'Solar.R': Integer,
  Wind: BigDecimal,
  Temp: BigDecimal,
  Month: Integer,
  Day: Integer
  ]
)

// print basic layout of the table after conversion
println str(aq)

// print the first and last 5 rows
println aq.head(10)

// print more info on the data
println summary(aq)

// check how the Temp varable is distributed, sorted by the highest occuring ones first
freq = frequency(aq, "Temp").orderBy('Frequency', true)
println freq.content()

// Show the distribution of temperature
io.display(Histogram.create(
    title: "Temperature (°F)", 
    data: aq, 
    columnName: "Temp", 
    binDecimals: 0), 
  "Temp histogram"
)

//io.view(aq)
// show a bok plot for how temperature is distributed each month
io.display(BarChart.create("bar Temp by Month", ChartType.BASIC, aq.convert("Month": String), "Month", ChartDirection.VERTICAL, "Temp"), "bar Temp/Month")
println("TODO: Boxcharts does not yet exist")
io.display(BoxChart.create("box Temp by Month", aq, "Month", "Temp"), "box Temp/Month")

// Show how Temerature and Ozone as (possibly) related
scatterChart = ScatterChart.create("Temperature and Ozone", aq, "Temp", "Ozone")
//println(scatterChart.xAxisScale)
//scatterChart.setxAxisScale((min(aq['Temp']) * 0.99), (max(aq['Temp']) * 1.01), 5)
io.display(scatterChart)
''