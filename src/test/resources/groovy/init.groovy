package groovy

@Grab('com.h2database:h2:2.2.224')
import groovy.sql.Sql

import java.sql.Driver

def driver = this.getClass().getClassLoader().loadClass(dbDriver).getDeclaredConstructor().newInstance() as Driver
def props = new Properties()
props.setProperty("user", dbUser)
props.setProperty("password", dbPasswd)
def conn = driver.connect(dbUrl, props)
def sql = new Sql(conn)

println("Create table if needed")
sql.execute '''
          create table IF NOT EXISTS PROJECT  (
              id integer not null primary key,
          name varchar(50),
              url varchar(100)
                  )
          '''
println("deleting PROJECT content")
sql.execute('delete from PROJECT')
println("Adding two rows to PROJECT")
sql.execute 'insert into PROJECT (id, name, url) values (?, ?, ?)', [10, 'Groovy', 'http://groovy.codehaus.org']
sql.execute 'insert into PROJECT (id, name, url) values (?, ?, ?)', [20, 'Alipsa', 'http://www.alipsa.se']

sql.close()
conn.close()