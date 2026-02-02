package se.alipsa.gade.code.completion.sql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface SqlSchemaIntrospector {

  List<String> tables();
  List<String> columns(String table);

  SqlSchemaIntrospector NONE = new SqlSchemaIntrospector() {
    @Override public List<String> tables() { return Collections.emptyList(); }
    @Override public List<String> columns(String table) { return Collections.emptyList(); }
  };

  final class Introspector implements SqlSchemaIntrospector {
    private static final Logger log = LogManager.getLogger(Introspector.class);
    private final Connection conn;
    public Introspector(Connection conn) { this.conn = conn; }

    @Override
    public List<String> tables() {
      if (conn == null) return Collections.emptyList();
      List<String> out = new ArrayList<>();
      try {
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE","VIEW"})) {
          while (rs.next()) {
            out.add(rs.getString("TABLE_NAME"));
          }
        }
      } catch (Exception e) {
        log.debug("Failed to introspect database tables, returning empty list", e);
      }
      return out;
    }

    @Override
    public List<String> columns(String table) {
      if (conn == null) return Collections.emptyList();
      List<String> out = new ArrayList<>();
      if (table == null) return out;
      try {
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getColumns(conn.getCatalog(), null, table, "%")) {
          while (rs.next()) {
            out.add(rs.getString("COLUMN_NAME"));
          }
        }
      } catch (Exception e) {
        log.debug("Failed to introspect table columns for '{}', returning empty list", table, e);
      }
      return out;
    }
  }
}
