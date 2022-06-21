package groovy

@Grab('se.alipsa.groovy:data-utils:1.0-SNAPSHOT')
@Grab('com.h2database:h2:2.1.214')

import se.alipsa.groovy.datautil.SqlUtil

def idList = new ArrayList()

SqlUtil.withInstance(dbUrl, dbUser, dbPasswd, dbDriver, this) { sql ->
    sql.query('SELECT id FROM project') { rs ->
        while (rs.next()) {
            idList.add(rs.getLong(1))
        }
    }
    sql.close()
}
println("withInstance: got ${idList.size()} ids")
return idList