# Gade cookbook

Gade is an integrated analytics development environment meant to aid you in the process of
gathering data, extract information from that data, and visualize that data to transform that information into knowledge.

A lot of the inspiration for writing this cookbook is drawn from Paul Kings [Groovy Data Science](https://speakerdeck.com/paulk/groovy-data-science)
presentation as well as his excellent [groovy.data-science](https://github.com/paulk-asert/groovy-data-science) project at Github.
The other major inspiration comes from the [Tablesaw documentation](https://jtablesaw.github.io/tablesaw/).
I highly recommend you to read all of these after reading this cookbook to go deeper into the details.

# Table of content
- [Gather](gather.md)
  - [Import from CSV](gather.md/#import-a-csv-file)
  - [Import from Excel](gather.md/#Import-an-excel-file)
  - [Import from database](gather.md/#Import-data-from-a-relational-database)
  - [Import Json](gather.md/#import-json)
  - [Import XML](gather.md/#import-xml)
  - [Import Open Office Calc](gather.md/#import-open-office-calc)
- [Explore](#explore)
  - [Table info](#tableInfo)
  - [Frequency tables](#frequencyTables)
  - [Histograms](#histograms)
  - [Heat maps](#heatMaps)
  - [Box plots](#boxPlots)
  - [Scatter Plots](#scatterPlots)
- [Clean, merge and transform](#cleanMergeTransform)
  - [Sort](#sort)
  - [Remove Missing](#removeMissing)
  - [Remove Outliers](#removeOutliers)
  - [Adjust Scales](#adjustScales)
  - [Merge](#merge)
  - [Aggregate](#aggregate)
- [Analyze](#analyze)
- [Visualize](#visualize)
- [Machine learning, modelling and regressions](#modelling)
  - [Linear regression](#linearRegression)
  - [Logistic regression](#logisticRegression)
  - [Poisson Regression](#poissonRegression)
  - [Decision tree regression](#decisionTreeRegression)
  - [Random forest](#randomForest)
  - [K means clustering](#kMeansClustering)
  - [Neural networks](#neuralNetworks)
- [Reporting](#reporting)
  - [Groovy Markdown](#groovyMarkdown)
  - [Save to a spreadsheet](#saveToSpreadsheets)
  - [Save to a presentation (Powerpoint or Impress)](#saveToPresentations)
- [Creating libraries](#creatingLibraries)

# <a id="explore"/>Explore

By exploring I mean getting a basic understanding of the data e.g. what kind of columns are in a table, how are values distributed,
etc. Exploring typically means looking at the variables and their distribution.
Tablesaw provides many ways to do this.

## <a id="tableInfo"/>Table info (summaries)
A Tablesaw Table has support for basic summaries built in:
- __shape()__ tells how many rows and columns this table has, example:
  ```groovy
  glaciers.shape()
  ```
  output:
  ```
  glaciers: 70 rows X 3 cols
  ```
- __structure()__ return a table with 3 columns describing the column index, name and type. Example:
  ```groovy
  glaciers.structure()
  // note: if you have many columns you probably want to do table.structure().printAll() instead
  ```
  output:
  ```
                  Structure of glaciers                
  Index  |          Column Name           |  Column Type  |
  ----------------------------------------------------------
      0  |        Number of observations  |      INTEGER  |
      1  |                          Year  |      INTEGER  |
      2  |  Mean cumulative mass balance  |       DOUBLE  |
  ```
- __summary()__ returns a table containing summary statistics for the columns in the table; example:
  ```groovy
  glaciers.summary()
  ```
  output:
  ```
  Summary  |  Number of observations  |         Year         |  Mean cumulative mass balance  |
  -----------------------------------------------------------------------------------------------
    Count  |                      70  |                  70  |                            70  |
      sum  |                          |              138565  |            -898.9509999999998  |
     Mean  |                          |              1979.5  |           -12.842157142857143  |
      Min  |                       1  |                1945  |                       -28.652  |
      Max  |                      37  |                2014  |                             0  |
    Range  |                      36  |                  69  |                        28.652  |
  Variance |                          |   414.1666666666667  |             43.28931022132505  |
  Std. Dev |                          |  20.351085147152883  |             6.579461240962291  |
  ```
- __print(maxRows)__ return a pretty-printed' string representation of at most maxRows rows.
  ```groovy
  glaciers.print(10)
  ```
  output:
  ```
                           glaciers                            
  Number of observations  |  Year  |  Mean cumulative mass balance  |
  --------------------------------------------------------------------
                          |  1945  |                             0  |
                       1  |  1946  |                         -1.13  |
                       1  |  1947  |                         -3.19  |
                       1  |  1948  |                         -3.19  |
                       3  |  1949  |                         -3.82  |
                     ...  |   ...  |                           ...  |
                      37  |  2010  |                       -25.158  |
                      37  |  2011  |                       -26.294  |
                      36  |  2012  |                        -26.93  |
                      31  |  2013  |                       -27.817  |
                      24  |  2014  |                       -28.652  |
  ```
You will notice that print samples data from both the head and the tail of the table. 
To print the first 10 rows only, use `println glaciers.first(10)`

## <a id="frequencyTables"/>Frequency tables
The se.alipsa.groovy:data-utils library (included in Gade) has a TableUtil class that we can use to create frequency tables:

```groovy
import se.alipsa.groovy.datautil.TableUtil

TableUtil.frequency(glaciers, "Number of observations")
```
output:
```
      Number of observations       
 Value  |  Frequency  |  Percent  |
-----------------------------------
    37  |         31  |    44.29  |
    32  |          5  |     7.14  |
     3  |          4  |     5.71  |
    36  |          4  |     5.71  |
     7  |          3  |     4.29  |
    31  |          3  |     4.29  |
     1  |          3  |     4.29  |
    22  |          2  |     2.86  |
     9  |          2  |     2.86  |
    35  |          2  |     2.86  |
    24  |          2  |     2.86  |
    33  |          1  |     1.43  |
    11  |          1  |     1.43  |
        |          1  |     1.43  |
    14  |          1  |     1.43  |
    15  |          1  |     1.43  |
    27  |          1  |     1.43  |
    29  |          1  |     1.43  |
     6  |          1  |     1.43  |
    20  |          1  |     1.43  |
```
## <a id="distributions"/>Distributions

Frequency diagrams gives you a visual representation of a distribution. As this is at the data 
exploration stage, colors, fonts and other formatting is not important so I will instead cover those things
in the [Visualize](#visualize) section. 

```groovy
import tech.tablesaw.api.*
import tech.tablesaw.plotly.api.*

data = Table.read().csv("airquality.csv")
io.display(Histogram.create("Temperature (°F)", data, "Temp"), "Temperature distribution")
```
![Temperature_histogram.png](docs/Temperature_histogram.png "Temperature distribution")

## <a id="heatMaps"/>Heat maps
```groovy
import tech.tablesaw.api.*
import tech.tablesaw.plotly.api.*

data = Table.read().csv("airquality.csv")
io.display(Histogram2D.create("Distribution of temp and ozone", data, "Temp", "Ozone"), "Temp/Ozone")
```
![heatmap_Temp_Ozone.png](docs/heatmap_Temp_Ozone.png "Temperature and Ozone distribution")

### <a id="boxPlots"/>Box plots
```groovy
import tech.tablesaw.api.*
import tech.tablesaw.plotly.api.*

data = Table.read().csv("airquality.csv")
io.display(BoxPlot.create("Temp by Month", data, "Month", "Temp"), "Temp/Month")
```
![boxplot_Temp_Month.png](docs/boxplot_Temp_Month.png "Temperature by month")

## <a id="scatterPlots"/>Scatter plots

```groovy
import tech.tablesaw.api.*
import tech.tablesaw.plotly.api.*

data = Table.read().csv("airquality.csv")
io.display(ScatterPlot.create("Temperature and Ozone", data, "Temp", "Ozone"))
```
![scatterPlot_Temp_Ozone.png](docs/scatterPlot_Temp_Ozone.png "Temperature and Ozone")

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

# <a id="analyze"/>Analyze

# <a id="visualize"/>Visualize

# <a id="modelling"/>Machine learning, modelling and regressions
## <a id="linearRegression"/>Linear regression
## <a id="logisticRegression"/>Logistic regression
## <a id="poissonRegression"/>Poisson Regression
## <a id="decisionTreeRegression"/>Decision tree regression
## <a id="randomForest"/>Random forest
## <a id="kMeansClustering"/>K means clustering
## <a id="neuralNetworks"/>Neural networks
# <a id="reporting"/>Reporting

## <a id="groovyMarkdown"/>Groovy Markdown (gmd)
Groovy Markdown is basically Markdown with groovy code snippets to dynamically create markdown content.
The [gmd](https://github.com/perNyfelt/gmd/blob/main/README.md) library combines the Groovy StreamingTemplateEngine
with the Flexmark Markdown package to create a nice Groovy Markdown processor. 

## <a id="saveToSpreadsheets"/>Save to a spreadsheet

## <a id ="saveToPresentations"/>Save to a presentation (PowerPoint or Impress)

# <a id="creatingLibraries"/>Creating libraries