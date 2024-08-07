# <a id="gather" />Gather
In "the real world", the data that you need to do analysis typically comes from a few different
sources, usually some relational database and spreadsheets. In order to be able to combine
such data you need them to be in a format that allows you to treat it in a similar way. There are
many ways in which you can do this. A database centric way would be to get all the data into the database
first and then work with it there. If your knowledge of SQL is high this is a powerful way to approach things
but a big drawback is that you typically don't know exactly what you need and in what structure you need it
leaving you with a lot of ad hoc tables to clean up afterwards.

Another way is to pull the data into Gade. You could use something like a List<List<Object>> to
represent sets of observations but working with that very basic data structure requires a lot of
boilerplate code and type casting. There are several good "data structure" libraries available in Groovy/Java
that you can use. I think the two best ones are [Joinery](https://github.com/cardillo/joinery)
and [Tablesaw](/jtablesaw/tablesaw). Of the two Gade provides a lot of convenience methods to use
Tablesaw, but you can certainly use Joinery instead of you prefer. At the core of the Tablesaw api is
the Table class which gives you similar power to manipulate data as you have with SQL in a relational database.

## Import a csv file
Importing a csv file could typically be as simple as:
```groovy
import tech.tablesaw.api.*

houseData = Table.read().csv(new File(io.scriptDir(), "../data/kc_house_data.csv"))
```
The io object is an object injected by Gade that provides some convenience methods and the ability to
interact with the Gade from your Groovy code. `io.ScriptDir()` is a `File` corresponding to the directory of
the script that you are executing. You could use io.projectDir() to get the location of the "project" i.e.
the root directory shown in the "Files" tab in the lower right quadrant of Gade, or you could simply hard code
the value based on your file system. The problem with the last approach is that if you want to share your work
with someone else, they will have to duplicate the location of the project on their hard drive.

If you need to adapt the import to work with the format of the csv file, you can use the
[CsvReadOptions.Builder](https://www.javadoc.io/doc/tech.tablesaw/tablesaw-core/latest/tech/tablesaw/io/csv/CsvReadOptions.Builder.html)
for that, e.g:

```groovy
import tech.tablesaw.api.*
import tech.tablesaw.io.csv.*

CsvReadOptions.Builder builder = CsvReadOptions.builder("myFile.csv")
  .separator('\t')                       // table is tab-delimited
  .header(false)                         // no header
  .dateFormat("yyyy.MM.dd")              // the date format to use. 
  .skipRowsWithInvalidColumnCount(true)  // skip incorrect rows
  .missingValueIndicator("", "N/A")      // missing is represented either an empty string or the string N/A
CsvReadOptions options = builder.build();

table = Table.read().usingOptions(options);
```

## Import an excel file
The Excel import is somewhat crude in the sense that the sheet you import should be pretty much only the
tabular data you want to import. Any extra header texts besides just the column names will make the sheet
impossible to import. Hence, you might need to massage the excel file a bit e.g. before importing it.

```groovy
import tech.tablesaw.api.Table
import tech.tablesaw.io.xlsx.XlsxReadOptions
import tech.tablesaw.io.xlsx.XlsxReader

excelFile = new File(io.scriptDir(), "../data/glaciers.xlsx")
XlsxReadOptions options = XlsxReadOptions.builder(excelFile)
  .sheetIndex(0)
  .tableName("Glaciers")
  .build()
XlsxReader xlsxReader = new XlsxReader()
glaciers = xlsxReader.read(options)
```

## Import data from a relational database

Groovy has excellent built-in support for reading from relational databases in the Sql class.
There is a snag when using @Grab however. The Sql class uses DriverManager to get the connection which
requires that the Driver is available from the System classloader i.e. it must reside the the Grade lib dir.
A better alternative is to use the SqlUtil class from the data-utils library which collaborates much
better with the Groovy Classloader that @Grab interacts with. The static methods of SqlUtil corresponds to
the methods available from the Sql class and, just like Sql, returns an instance of the Sql class that can be
used to insert, update, delete and select data. Below is an example:

```groovy
@Grab('se.alipsa.groovy:data-utils:1.0.5')
@Grab('com.h2database:h2:2.1.214')

import se.alipsa.groovy.datautil.SqlUtil

dbUser = "sa"
dbPasswd = "pwd"
dbDriver = "org.h2.Driver"
dbUrl = "jdbc:h2:file:/tmp/mydatabase"

SqlUtil.withInstance(dbUrl, dbUser, dbPasswd, dbDriver) { sql ->
    sql.query('SELECT * FROM mytable') { rs ->
        table = Table.read().db(rs)
    }
}
// do something with the table
```

Since querying a relational database is such a frequent task, the io object that is inserted into the
groovy session has several convenience methods to do that. If you have defined connections in the
Grade connections tab, you can use that to query a database, e.g:

```groovy
table = io.dbSelect("mydatabase", "select * from mytable")
```
If you need to set the credentials (password are not saved in the connections tab), you can do:

```groovy
connectionInfo = io.dbConnection("mydatabase").withPassword(getDbPasswdFromSomewhere())
table = io.dbSelect(connectionInfo, "select * from mytable")
```

## Import Json

```groovy
import static tech.tablesaw.api.ColumnType.*
import tech.tablesaw.api.*
import tech.tablesaw.io.json.*
 
jsonFile = new File(io.scriptDir(), "../data/glaciers.json")

options = JsonReadOptions.builder(jsonFile)
        .columnTypes(colName -> switch (colName) {
          case "Year", "Number of observations" -> INTEGER;
          case "Mean cumulative mass balance" -> DOUBLE;
          default -> STRING;
        })
        .build();
  
glaciers = Table.read().usingOptions(options)
```

## Import XML
```groovy
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
```

## Import Open Office Calc
```groovy
import tech.tablesaw.io.ods.OdsReadOptions
import tech.tablesaw.io.ods.OdsReader

odsFile = new File(io.scriptDir(), "../data/glaciers.ods")
OdsReadOptions options = OdsReadOptions.builder(odsFile)
.sheetIndex(0)
.tableName("Glaciers")
.build()
OdsReader reader = new OdsReader()
glaciers = reader.read(options)
```