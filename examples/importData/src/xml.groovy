import static tech.tablesaw.api.ColumnType.*
import tech.tablesaw.api.*
import tech.tablesaw.io.xml.*

xmlFile = new File(io.scriptDir(), "../data/glaciers.xml")

options = XmlReadOptions.builder(xmlFile)
        .columnTypes(f -> switch (f) {
          case "Year", "Number_of_observations" -> INTEGER
          case "Mean_cumulative_mass_balance" -> DOUBLE
          default -> STRING
        })
        .build()
glaciers = Table.read().usingOptions(options);