/**************************************** 
 * Simple example fo how to export data
 * to a csv file
 ****************************************/ 

import static tech.tablesaw.api.ColumnType.*
import tech.tablesaw.io.csv.*
import tech.tablesaw.api.*

// create the emp table
evaluate(new File(io.scriptDir(), "data.groovy"))

// The file to export to
csv = new File(io.scriptDir().getParentFile(), "employees.csv")

// define destination and format(s)
def builder = CsvWriteOptions.builder(csv)
.separator(';' as Character)

// export the table data
emp.write().usingOptions(builder.build())  


