/*******************************************
 * Simple example of how to export data
 * to an open/libre office spreadsheet file
 *******************************************/

import tech.tablesaw.io.ods.OdsWriteOptions

// create the emp table
evaluate(new File(io.scriptDir(), "data.groovy"))

// The file to export to
file = new File(io.scriptDir().getParentFile(), "employees.ods")

OdsWriteOptions options = OdsWriteOptions.builder(file)
    .build()

emp.write().usingOptions(options)