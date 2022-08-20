import static Math.*
import tech.tablesaw.api.*
import tech.tablesaw.plotly.api.*

data = Table.read().csv(new File(io.scriptDir(), "../../data/airquality.csv"))

sortedData = data.sortOn("Month", "Day", "-Temp", "-Ozone")


class TempComparator implements Comparator<Row> {

  // adjust the temperature depending on the wind to represent "felt" temperature
  def windChill(temp, wind) {
    
    // There is no windchill factor for high temp and/or weak winds
    if (temp >= 50 && wind < 3.0) {
      return temp
    }
    
    // Formula according to https://www.weather.gov/media/epz/wxcalc/windChill.pdf
    // 35.74 + 0.6215T – 35.75(V^0.16) + 0.4275T(V^0.16)
    def exponent = 0.16 as double
    return round(
      35.74 + 0.6215 * temp 
      - ( 35.75 * pow(wind, exponent) ) 
      + ( 0.4275* temp * pow(wind, exponent))
    )
  }
  

  @Override
  public int compare(Row o1, Row o2) {
    def val1 = o1.getInt("Temp")
    return windChill(o1.getInt("Temp"), o1.getDouble("Wind")) - windChill(o2.getInt("Temp"), o2.getDouble("Wind"));
  }
}

comparedData = data.sortOn(new TempComparator())