import se.alipsa.groovy.matrix.*
import static se.alipsa.groovy.matrix.Stat.*
import static se.alipsa.groovy.matrix.Matrix.*
import se.alipsa.groovy.charts.*

data = Matrix.builder().data(new File(io.scriptDir(), "../../data/airquality.csv"))
    .build()
    .convert(
  [
  Ozone: Integer,
  'Solar.R': Integer,
  Wind: BigDecimal,
  Temp: BigDecimal,
  Month: Integer,
  Day: Integer
  ]
)

sortedData = data.sortBy(["Month": ASC, "Day": ASC, "Temp": DESC, "Ozone": DESC])
//sortedData.content()

def windChill(temp, wind) {
    
    // There is no windchill factor for high temp and/or weak winds
    if (temp >= 50 && wind < 3.0) {
      return temp
    }
    
    // Formula according to https://www.weather.gov/media/epz/wxcalc/windChill.pdf
    // 35.74 + 0.6215T – 35.75(V^0.16) + 0.4275T(V^0.16)
    Math.round(
      35.74 + 0.6215 * temp 
      - ( 35.75 * wind ** 0.16 ) 
      + ( 0.4275* temp * wind ** 0.16)
    )
}

def comparator = { a,b -> 
  def tempIdx = data.columnIndex('Temp')
  def windIdx = data.columnIndex('Wind')
  windChill(a[tempIdx], a[windIdx]) <=> windChill(b[tempIdx], b[windIdx])
} as Comparator 

comparedData = data.sortBy(comparator)
//io.view(comparedData, "matrix")

// Remove rows with missing Solar.R values
solarData = data.subset( "Solar.R", { it != null })

def windIdx = data.columnIndex('Wind')
def solarIdx = data.columnIndex('Solar.R')
  
// Remove outliers
filtered = data.subset { row ->
  !(row[windIdx] > 20) || !(row[solarIdx] < 20)
}
//io.view(filtered.sortBy(['Month': ASC, 'Day': ASC]))

filtered2 = data.subset{
  (it[windIdx] < 20) && (it[solarIdx] > 20)
}

io.view(filtered2.sortBy(['Month': ASC, 'Day': ASC]))