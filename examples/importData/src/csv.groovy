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

/*
import tech.tablesaw.io.json.*
writer = new StringWriter();
jsonOptions = JsonWriteOptions.builder(writer).asObjects(true).header(true).build()
glaciers.write()
  .usingOptions(jsonOptions)

jsonFile = new File(io.scriptDir(), "../data/glaciers.json")

jsonFile.write writer.toString()
*/