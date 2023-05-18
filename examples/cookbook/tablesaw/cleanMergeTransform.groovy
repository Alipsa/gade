import tech.tablesaw.api.*
import tech.tablesaw.plotly.api.*

data = Table.read().csv(new File(io.scriptDir(), "../../data/airquality.csv"))

sortedData = data.sortOn("Month", "Day", "-Temp", "-Ozone")


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
    windChill(a.getInt("Temp"), a.getDouble("Wind")) <=> windChill(b.getInt("Temp"), b.getDouble("Wind"))
} as Comparator  

comparedData = data.sortOn(comparator)
//io.view(comparedData, "Tablesaw")

// Remove rows with missing Solar.R values
solarData = data.dropWhere(data.column("Solar.R").isMissing())

// Remove outliers
filtered = data.dropWhere(
    data.numberColumn("Wind").isGreaterThan(20.0)
    .and(data.numberColumn("Solar.R").isLessThan(20.0))
)
//io.view(filtered.sortOn("Month", "Day"))

filtered2 = data.where(
    data.numberColumn("Wind").isLessThan(20)
        .and(data.numberColumn("Solar.R").isGreaterThan(20))
)
//io.view(filtered2.sortOn("Month", "Day"))