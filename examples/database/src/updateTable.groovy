/******************************************
 * Update a db table based on the data in *
 * in a tablesaw Table                    *
 ******************************************/
@Grab('com.h2database:h2:2.1.214')
import se.alipsa.groovy.datautil.SqlUtil

// create the test table and load database connection info
evaluate(new File(io.scriptDir(), 'createTable.groovy'))
 
// Using Groovy Sql (with the help of SqlUtil)
SqlUtil.withInstance(dbUrl, dbUser, dbPasswd, dbDriver) { sql -> 
  def numrows = sql.executeUpdate("update test set role = 'CEO' where name = 'Per'")
  println "${numrows} rows updated"
}

// Same thing but using a connection created in the Connections tab called h2 test
// passwords are not saved in the defined connections so we add it (otherwise it will prompt for it))
connectionInfo = io.dbConnection("h2 test")
connectionInfo.setPassword(dbPasswd)
def numrows = io.dbUpdate(connectionInfo, "update test set role = 'CTO' where name = 'Per'")
println "${numrows} rows updated"

