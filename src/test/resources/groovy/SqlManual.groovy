package groovy

//@GrabConfig(systemClassLoader=true)
@Grab('com.h2database:h2:2.1.214')

import groovy.sql.Sql
import java.sql.Driver

def driver
try {
    driver = Class.forName(dbDriver).getDeclaredConstructor().newInstance() as Driver
} catch (ClassNotFoundException ignored) {
    try {
        driver = Thread.currentThread().getContextClassLoader().loadClass(dbDriver).getDeclaredConstructor().newInstance() as Driver
    } catch(ClassNotFoundException e2) {
        driver = this.getClass().getClassLoader().loadClass(dbDriver).getDeclaredConstructor().newInstance() as Driver
    }
}

def props = new Properties()
props.setProperty("user", dbUser)
props.setProperty("password", dbPasswd)

def conn = driver.connect(dbUrl, props)
def sql = new Sql(conn)
def idList = new ArrayList();

try {
    sql.eachRow("SELECT id, name FROM project") {
        idList.add(it.getLong(1))
    }
} finally {
    sql.close()
    conn.close()
}
println("manual: got ${idList.size()} ids")
return idList
