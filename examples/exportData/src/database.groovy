/****************************************
 * Simple example of how to export data
 * to a database
 ****************************************/
import java.time.LocalDate

connectionInfo = io.dbConnection("mydatabase").withPassword(getDbPasswdFromSomewhere())

// Inserting data can be done in 4 ways, see "examples/database" for the first one

// Method 2: create the table and then "Manually" insert row by row
io.dbExecuteSql(connectionInfo, "create table employee( employeeNum int not null primary key, name varchar(200), employmentStart date)")
io.dbInsert(connectionInfo, "into employee values (1, 'Per', ${LocalDate.of(2020, 1, 30)}")

// Method 3: Or we can do builkinserts
io.dbInsert(connectionInfo, table)

// Method 4: Or create the table and insert the data:
io.dbCreate(connectionInfo, table, "employeeNum")