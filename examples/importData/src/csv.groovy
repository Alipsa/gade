/**************************************** 
 * Simple example fo how to import data
 * from a csv file
 ****************************************/ 

import static tech.tablesaw.api.ColumnType.*
import tech.tablesaw.io.csv.*
import tech.tablesaw.api.*

csvFile = new File(io.scriptDir(), "../data/glaciers.txt")

CsvReadOptions.Builder builder = CsvReadOptions.builder(csvFile)
  .separator(',' as Character)
  .columnTypes([INTEGER, DOUBLE, INTEGER] as ColumnType[])

glaciers = Table.read().usingOptions(builder.build())