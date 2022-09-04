// Simplistic groovy project

import static tech.tablesaw.api.ColumnType.*

import tech.tablesaw.api.*
import java.time.LocalDate

def borrower = args.length > 0 ? args[0] : "Per"

table = Table.create("Books").addColumns(
    INTEGER.create("id"),
    STRING.create("name"),
    LOCAL_DATE.create("checked_out"),
    STRING.create("borrower")
)
// Note: by not using "def" or type, variables can be accessed from tests as if we explicitly did
//binding.setVariable("table", table)

def row = table.appendRow()
row.setInt("id", 1)
row.setString("name", "Dao De Jing")
row.setDate("checked_out", LocalDate.now())
row.setString("borrower", borrower)

row = table.appendRow()
row.setInt("id", 1)
row.setString("name", "The Analects")
row.setDate("checked_out", null)
row.setString("borrower", null)

// Print the result to console
println table
