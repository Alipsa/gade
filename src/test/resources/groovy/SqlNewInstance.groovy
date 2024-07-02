package groovy

@Grab('se.alipsa.groovy:data-utils:1.0.5-SNAPSHOT')
@Grab('com.h2database:h2:2.2.224')

import se.alipsa.groovy.datautil.SqlUtil

def sql = SqlUtil.newInstance(dbUrl, dbUser, dbPasswd, dbDriver)
def idList = new ArrayList()

try {
    sql.eachRow("SELECT id FROM project") {
        idList.add(it.getLong(1))
    }
} finally {
    sql.getConnection().close()
    sql.close()
}
println("newInstance: got ${idList.size()} ids")
return idList