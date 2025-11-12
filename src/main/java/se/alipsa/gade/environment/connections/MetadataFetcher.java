package se.alipsa.gade.environment.connections;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MetadataFetcher {

  /**
   * Retrieves column metadata and transforms it into a Map where keys are column headers
   * (e.g., "TABLE_NAME") and values are lists of all entries for that header.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, List> getNonSystemColumnMetadata(Connection connection) throws SQLException {

    // 1. Define the final map structure and initialize it
    Map<String, List> finalColumnMap = new LinkedHashMap<>();

    // Define the column headers corresponding to your SQL query
    List<String> headers = Arrays.asList(
        "TABLE_NAME", "TABLE_TYPE", "COLUMN_NAME", "ORDINAL_POSITION",
        "IS_NULLABLE", "DATA_TYPE", "CHARACTER_MAXIMUM_LENGTH",
        "NUMERIC_PRECISION", "NUMERIC_SCALE", "COLLATION_NAME", "TABLE_SCHEMA"
    );

    // Initialize all lists in the map
    for (String header : headers) {
      finalColumnMap.put(header, new ArrayList<>());
    }

    // --- (Table filtering logic from previous response remains the same) ---
    DatabaseMetaData dbMetadata = connection.getMetaData();
    String[] tableTypes = {"TABLE", "VIEW"};
    List<String> excludedSchemas = Arrays.asList(
        "SYSTEM TABLE", "PG_CATALOG", "INFORMATION_SCHEMA",
        "pg_catalog", "information_schema"
    );
    List<TableInfo> filteredTables = new ArrayList<>();

    try (ResultSet tablesResultSet = dbMetadata.getTables(null, null, null, tableTypes)) {
      while (tablesResultSet.next()) {
        String tableSchema = tablesResultSet.getString("TABLE_SCHEM");
        String tableType = tablesResultSet.getString("TABLE_TYPE");
        if (!excludedSchemas.contains(tableSchema) && !tableType.equals("SYSTEM TABLE")) {
          filteredTables.add(new TableInfo(tablesResultSet.getString("TABLE_NAME"), tableSchema, tableType));
        }
      }
    }

    // 2. Iterate through columns and directly populate the lists in the map
    for (TableInfo table : filteredTables) {
      try (ResultSet columnsResultSet = dbMetadata.getColumns(null, table.getSchema(), table.getName(), null)) {

        while (columnsResultSet.next()) {
          // Populate the lists for each column header

          finalColumnMap.get("TABLE_NAME").add(columnsResultSet.getString("TABLE_NAME"));
          finalColumnMap.get("TABLE_TYPE").add(table.getType()); // From TableInfo
          finalColumnMap.get("COLUMN_NAME").add(columnsResultSet.getString("COLUMN_NAME"));
          finalColumnMap.get("ORDINAL_POSITION").add(columnsResultSet.getInt("ORDINAL_POSITION"));
          finalColumnMap.get("IS_NULLABLE").add(columnsResultSet.getString("IS_NULLABLE"));
          finalColumnMap.get("DATA_TYPE").add(columnsResultSet.getString("TYPE_NAME"));

          // CHARACTER_MAXIMUM_LENGTH uses COLUMN_SIZE
          finalColumnMap.get("CHARACTER_MAXIMUM_LENGTH").add(columnsResultSet.getInt("COLUMN_SIZE"));

          finalColumnMap.get("NUMERIC_PRECISION").add(columnsResultSet.getInt("PRECISION"));
          finalColumnMap.get("NUMERIC_SCALE").add(columnsResultSet.getInt("DECIMAL_DIGITS"));

          // COLLATION_NAME is not standard (set to null)
          finalColumnMap.get("COLLATION_NAME").add(null);

          finalColumnMap.get("TABLE_SCHEMA").add(table.getSchema()); // From TableInfo
        }
      }
    }

    return finalColumnMap;
  }

  // --- (TableInfo helper class from previous response is still required) ---
  private static class TableInfo {
    private String name;
    private String schema;
    private String type;

    public TableInfo(String name, String schema, String type) {
      this.name = name;
      this.schema = schema;
      this.type = type;
    }

    public String getName() { return name; }
    public String getSchema() { return schema; }
    public String getType() { return type; }
  }
}
