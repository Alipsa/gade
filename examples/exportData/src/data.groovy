import static tech.tablesaw.api.ColumnType.*
import tech.tablesaw.api.*
import java.time.*

emp = Table.create("Employees").addColumns(
  INTEGER.create("employeeNum"),
  STRING.create("name"),
  LOCAL_DATE.create("employmentStart"),
  LOCAL_TIME.create("coffeeTime")
)
def row = emp.appendRow()
row.setInt("employeeNum", 1)
row.setString("name", "Per")
row.setDate("employmentStart", LocalDate.of(2020, 1, 30))
row.setTime("coffeeTime", LocalTime.of(9, 20))

row = emp.appendRow()
row.setInt("employeeNum", 2)
row.setString("name", "Louise")
row.setDate("employmentStart", LocalDate.of(2020, 2, 1))
row.setTime("coffeeTime", LocalTime.of(9, 20))

row = emp.appendRow()
row.setInt("employeeNum", 3)
row.setString("name", "Erik")
row.setDate("employmentStart", LocalDate.of(2021, 12, 5))
row.setTime("coffeeTime", LocalTime.of(14, 10, 30))

row = emp.appendRow()
row.setInt("employeeNum", 4)
row.setString("name", "Ann")
row.setDate("employmentStart", LocalDate.of(2022, 3, 15))
row.setTime("coffeeTime", LocalTime.of(14, 10, 30))