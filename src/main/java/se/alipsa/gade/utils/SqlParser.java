package se.alipsa.gade.utils;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class SqlParser {

  private static final Logger log = LogManager.getLogger(SqlParser.class);

  public static String[] split(String sql, StringBuilder warnings) {
    try {
      Statements statements = CCJSqlParserUtil.parseStatements(sql);
      List<String> list = new ArrayList<>(statements.size());
      for (Statement stmt : statements) {
        list.add(stmt.toString());
      }
      return list.toArray(new String[0]);
    } catch (JSQLParserException e) {
      log.debug("Failed to parse sql: {}", e.toString());
      int numlines = org.apache.commons.lang3.StringUtils.countMatches(sql,"\n") + 1;
      warnings.append("Failed to parse statement(s), will try the whole string (")
          .append(numlines).append(" lines, ").append(sql.length()).append(" chars)\n");
      return new String[] {sql};
    }
  }
}
