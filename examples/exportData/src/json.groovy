/**************************************** 
 * Simple example of how to export data
 * to a json file
 ****************************************/

import tech.tablesaw.io.*
import tech.tablesaw.io.json.*

// create the emp table
evaluate(new File(io.scriptDir(), "data.groovy"))

jsonFile = new File(io.scriptDir().getParentFile(), "employees.json")
jsonOptions = JsonWriteOptions.builder(new Destination(jsonFile))
  .asObjects(true)
  .header(true)
  .build()
emp.write().usingOptions(jsonOptions)
