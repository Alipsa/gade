package groovy

@Grab('se.alipsa.groovy:data-utils:1.0.4')
@Grab('com.h2database:h2:2.2.224')

import se.alipsa.groovy.datautil.SqlUtil

def idList = new ArrayList()

SqlUtil.withInstance(dbUrl, dbUser, dbPasswd, dbDriver) { sql ->
    sql.query('SELECT id FROM project') { rs ->
        while (rs.next()) {
            idList.add(rs.getLong(1))
        }
    }
}
println("withInstance: got ${idList.size()} ids")
return idList