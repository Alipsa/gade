/******************************************
 * Copy a tablesaw Table to the database  *
 * Creates the table and inserts all data *
 * into the new table                     *
 ******************************************/
 
import tech.tablesaw.api.Table
 
evaluate(new File(io.scriptDir(), 'dbInfo.groovy'))
 
table = Table.read().csv(new File(io.scriptDir(), "/../../data/boston-robberies.csv"))
table.setName("boston_robberies")

connectionInfo = io.dbConnection("h2 test")
connectionInfo.setPassword(dbPasswd)

if (io.dbTableExists(connectionInfo, table.name())) {
  io.dbExecuteSql(connectionInfo, "drop table if exists ${table.name()}")
}
  
io.dbCreate(connectionInfo, table, "Record")


 