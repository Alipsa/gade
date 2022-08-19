/****************************************
 * Simple example of how to import data
 * from an ods file
 ****************************************/

import tech.tablesaw.io.ods.OdsReadOptions
import tech.tablesaw.io.ods.OdsReader

odsFile = new File(io.scriptDir(), "../data/glaciers.ods")
OdsReadOptions options = OdsReadOptions.builder(odsFile)
    .sheetIndex(0)
    .tableName("Glaciers")
    .build()
OdsReader reader = new OdsReader()
glaciers = reader.read(options)