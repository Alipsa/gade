/**************************************** 
 * Simple example of how to export data
 * to an excel file
 ****************************************/ 

import tech.tablesaw.io.xlsx.XlsxWriteOptions

// create the emp table
evaluate(new File(io.scriptDir(), "data.groovy"))

// The file to export to
file = new File(io.scriptDir().getParentFile(), "employees.xlsx")

XlsxWriteOptions options = XlsxWriteOptions.builder(file)
  .build()

emp.write().usingOptions(options)

