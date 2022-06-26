package se.alipsa.gride.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gride.environment.connections.ConnectionInfo;

public class QueryBuilder {

  private static final Logger log = LogManager.getLogger(QueryBuilder.class);
  public static final String DRIVER_VAR_NAME = "QueryBuilderDrv";
  public static final String CONNECTION_VAR_NAME = "QueryBuilderCon";


  public static StringBuilder baseQueryString(ConnectionInfo con, String sql) {
    StringBuilder str = new StringBuilder();

    String url = con.getUrl();
    String user = con.getUser() == null ? "" : con.getUser().trim();
    String password = con.getPassword() == null ? "" : con.getPassword().trim();
    str.append("@Grab('").append(con.getDependency()).append("')\n")
        .append("@Grab('se.alipsa.groovy:data-utils:1.0-SNAPSHOT')\n\n")
        .append("import se.alipsa.groovy.datautil.SqlUtil\n")
        .append("import tech.tablesaw.api.Table\n\n")
        .append("def sql = SqlUtil.newInstance('")
        .append(url).append("', '")
        .append(user).append("', '")
        .append(password).append("', '")
        .append(con.getDriver())
        .append("')\n")
        .append("Table table\n")
        .append("\ndef rs = sql.query(\"\"\"").append(sql).append("\"\"\") { rs -> {")
        .append("\n  table = Table.read().db(rs)\n")
        .append("  }\n")
        .append("}\n")
        .append("sql.close()\n")
        .append("return table");
    log.info(str.toString());
    return str;
  }

  public static StringBuilder cleanupQueryString() {
    return new StringBuilder("sql.close()\n");
  }
}
