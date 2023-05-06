/**
 * Some examples of exploring a data set
 */
import tech.tablesaw.api.*
import se.alipsa.groovy.datautil.TableUtil
import tech.tablesaw.plotly.api.*

data = Table.read().csv(new File(io.scriptDir(), "../../data/airquality.csv"))

// print basic layout of the table
println data.shape()

// print the first and last 5 rows
println data.print(10)

// print all column names and their types
println data.structure()

// print some info on the data
println data.summary()

// check how the Temp varable is distributed
freq = TableUtil.frequency(data, "Temp")
println freq

// Show the distribution of temperature
io.display(Histogram.create("Temperature (°F)", data, "Temp"), "Temp histogram")

// Show a heat map of temp and ozone
io.display(Histogram2D.create("Distribution of temp and ozone", data, "Temp", "Ozone"), "Temp/Ozone")

// show a bok plot for how temperature is distributed each month
io.display(BoxPlot.create("Temp by Month", data, "Month", "Temp"), "Temp/Month")

// Show how Temerature and Ozone as (possibly) related
io.display(ScatterPlot.create("Temperature and Ozone", data, "Temp", "Ozone"))