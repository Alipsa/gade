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
![../scatterPlot_Temp_Ozone.png](../scatterPlot_Temp_Ozone.png "Temperature and Ozone")


