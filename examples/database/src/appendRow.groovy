/****************************************
 * Append a Tablsesaw row to a db table *
 ****************************************/
connectionInfo = io.dbConnection("h2 test")
connectionInfo.setPassword(dbPasswd)
 
if (!io.dbTableExists(connectionInfo, "test")) {
  evaluate(new File(io.scriptDir(), 'createTable.groovy'))
}

test = io.dbSelect(connectionInfo, "* from test")
row = test.appendRow()

row.setInt("ID", 4)
row.setString("NAME", "Chris")
row.setLong("EMPLOYEE_NUMBER", 4567890123)

io.dbInsert(connectionInfo, "test", row)
  