# <a id="cleanMergeTransform"/>Clean, merge and transform

Once you have a basic understanding of the data you often need to adjust it in order
to be able to go deeper into the analysis e.g. by collecting data together into groups (aggregating),
comparing variables or merging dataset (tables) together.

## <a id="sort"/>Sort

There are several methods to sort a Table. Perhaps the simplest and yet quite powerful way is
the `sortOn()` method. It takes the column name(s) to sort on as argument. If you want to sort descending then
prefix the column name with `-`.

```groovy
import tech.tablesaw.api.*
import tech.tablesaw.plotly.api.*

data = Table.read().csv(new File(io.scriptDir(), "../../data/airquality.csv"))
// sort by Month and Day in ascending order, and Temp and Ozone in descending order
sortedData = data.sortOn("Month", "Day", "-Temp", "-Ozone")
```
If you need finer grade of control, you can implement a [Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)
and pass it to the sortOn() method. Here is an example:

```groovy
import tech.tablesaw.api.*
import tech.tablesaw.plotly.api.*

data = Table.read().csv(new File(io.scriptDir(), "../../data/airquality.csv"))

def windChill(temp, wind) {

  // There is no windchill factor for high temp and/or weak winds
  if (temp >= 50 && wind < 3.0) {
    return temp
  }

  // Formula according to https://www.weather.gov/media/epz/wxcalc/windChill.pdf
  // 35.74 + 0.6215T – 35.75(V^0.16) + 0.4275T(V^0.16)
  return Math.round(
      35.74 + 0.6215 * temp
          - ( 35.75 * wind ** 0.16 )
          + ( 0.4275* temp * wind ** 0.16)
  )
}

def comparator = { a,b ->
  windChill(a.getInt("Temp"), a.getDouble("Wind")) <=> windChill(b.getInt("Temp"), b.getDouble("Wind"))
} as Comparator

comparedData = data.sortOn(comparator)
```
Note that in practice you are like better off adding an additional, calculated, column rather
than doing complex logic in the sort comparator.

## <a id="removeMissing"/>Remove missing

A simple way to remove any row that has a missing value is to use the `table.dropRowsWithMissingValues()` method.

To be more specific, e.g. drop rows where a particular column has missing values, you
can use `table.dropWhere(aSelection)` instead. Here is an example

```groovy
import tech.tablesaw.api.*

data = Table.read().csv(new File(io.scriptDir(), "../../data/airquality.csv"))
solarData = data.dropWhere(data.column("Solar.R").isMissing())
```

## <a id="removeOutliers"/>Remove outliers
There are two ways to do this. One is to remove rows with excessive data using `dropRows()`, e.g.
```groovy
filtered = data.dropWhere(
    data.numberColumn("Wind").isGreaterThan(20)
        .and(data.numberColumn("Solar.R").isLessThan(20))
)
```
The other approach is to retain only the value ranges you are interested in using `where()`, e.g:
```groovy
filtered2 = data.where(
    data.numberColumn("Wind").isLessThan(20)
        .and(data.numberColumn("Solar.R").isGreaterThan(20))
)
```

## <a id="adjustScales"/>Adjust scales
### <a id="minMaxScaling"/>Min Max Scaling
Ranges the data values to be between 0 and 1, the formula is:

Z<sub>i</sub> = ( X<sub>i</sub> - min(X) ) / ( max(X) - min(X) )

### <a id="meanNormalization"/>Mean normalization
Scales the data values to be between (–1, 1), the formula is

X´ = ( X - μ ) / ( max(X) - min(X) )

where X´ is the mean normalized value, μ is the sample mean, and X the observed (original) value

### <a id = "standardScaler"/>Standard scaler
scales the distribution of data values so that the mean of the observed values will be 0 and standard deviation will be 1.
The formula is:

Z = ( X<sub>i</sub> - μ ) / σ

Where μ is the sample mean, and σ the standard deviation.

## <a id="merge"/>Merge
## <a id="aggregate"/>Aggregate