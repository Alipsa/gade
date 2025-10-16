package se.alipsa.gade.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//import se.alipsa.gade.environment.connections.ConnectionInfo;
import se.alipsa.groovy.datautil.ConnectionInfo;

public class QueryBuilder {

  private static final Logger log = LogManager.getLogger(QueryBuilder.class);
  public static final String DRIVER_VAR_NAME = "QueryBuilderDrv";
  public static final String CONNECTION_VAR_NAME = "QueryBuilderCon";


  public static StringBuilder baseQueryString(ConnectionInfo con, String sql) {
    StringBuilder str = new StringBuilder();

    String url = con.getUrl();
    String user = con.getUser() == null ? "" : con.getUser().trim();
    String password = con.getPassword() == null ? "" : con.getPassword().trim();

    str.append("""
        
        """);
    str.append("@Grab('").append(con.getDependency()).append("')\n")
        .append("""
        @Grab('se.alipsa.groovy:data-utils:2.0.0')
        import se.alipsa.groovy.datautil.SqlUtil
        import se.alipsa.matrix.core.Matrix
        
        def sql = SqlUtil.newInstance('""")
        .append(url).append("', '")
        .append(user).append("', '")
        .append(password).append("', '")
        .append(con.getDriver())
        .append("')\n")
        .append("\nsql.query(\"\"\"\n  ").append(sql).append("\n\"\"\") { rs -> {")
        .append("\n  table = Matrix.builder().data(rs).build()\n")
        .append("  }\n")
        .append("}\n")
        .append("sql.close()\n")
        .append("// do something with table\n\n")
        .append("// Alternative syntax using a connection from the connections tab:\n")
            .append("new groovy.sql.Sql(io.connect(\"")
        .append(con.getName())
        .append("""                                        
                ")).withCloseable { sql -> {
                  sql.query(""\"select * from SomeTable""\") { rs -> {
                    table = Matrix.builder().data(rs).build()
                  }}
                }}
                
                """);
    str.append("// Or even simpler:\n")
            .append("table = io.select(\"").append(con.getName())
        .append("\", \"\"\"select * from SomeTable\"\"\")\n");

    log.info(str.toString());
    return str;
  }

  public static StringBuilder cleanupQueryString() {
    return new StringBuilder("sql.close()\n");
  }
}
