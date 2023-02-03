/**************************************** 
 * Simple example for how to import data
 * from a json file
 ****************************************/ 
 
import static tech.tablesaw.api.ColumnType.*
import tech.tablesaw.api.*
import tech.tablesaw.io.json.*
 
jsonFile = new File(io.scriptDir(), "../data/glaciers.json")

options = JsonReadOptions.builder(jsonFile)
        .columnTypes(colName -> switch (colName) {
          case "Year", "Number of observations" -> INTEGER;
          case "Mean cumulative mass balance" -> DOUBLE;
          default -> STRING;
        })
        .build();
  
glaciers = Table.read().usingOptions(options)