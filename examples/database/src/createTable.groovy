@Grab('com.h2database:h2:2.1.214')
import se.alipsa.groovy.datautil.SqlUtil

evaluate(new File(io.scriptDir(), 'dbInfo.groovy'))
SqlUtil.withInstance(dbUrl, dbUser, dbPasswd, dbDriver) { sql ->
    sql.execute("drop table if exists test")
    sql.execute("""\
      create table test (
        id integer not null primary key,
        name varchar(100),
        employee_number bigint,
        role varchar(100)
      )
    """)
    sql.execute "insert into test (id, name, employee_number, role) values (1, 'Per', 1234567890, 'CTO')"
    sql.execute "insert into test (id, name, employee_number, role) values (2, 'John', 2345678901, 'Developer')"
    sql.execute "insert into test (id, name, employee_number, role) values (3, 'Anna', 3456789012, 'CFO')"
}