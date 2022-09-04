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

// set variables we want to be able to test
binding.setVariable("table", table)
// Print the result to console
println table
