/**************************************** 
 * Simple example fo how to import data
 * from an excel file
 ****************************************/ 

import tech.tablesaw.api.Table
import tech.tablesaw.io.xlsx.XlsxReadOptions
import tech.tablesaw.io.xlsx.XlsxReader

excelFile = new File(io.scriptDir(), "../data/glaciers.xlsx")
XlsxReadOptions options = XlsxReadOptions.builder(excelFile)
  .sheetIndex(0)
  .tableName("Glaciers")
  .build()
XlsxReader xlsxReader = new XlsxReader()
glaciers = xlsxReader.read(options)

