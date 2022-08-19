/**************************************** 
 * Simple example of how to export data
 * to a xml file
 ****************************************/
import tech.tablesaw.api.*
import tech.tablesaw.io.xml.*

// create the emp table
evaluate(new File(io.scriptDir(), "data.groovy"))

xmlFile = new File(io.scriptDir().getParentFile(), "employees.xml")

options = XmlWriteOptions.builder(xmlFile).build()
emp.write().usingOptions(options);