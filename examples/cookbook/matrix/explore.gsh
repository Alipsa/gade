import se.alipsa.groovy.matrix.*
import static se.alipsa.groovy.matrix.Stat.*

aq = Matrix.create(new File(io.scriptDir(), "../../data/airquality.csv"))

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
freq = frequency(aq, "Temp").sort('Frequency', true)
println freq.content()
''