package se.alipsa.gade.chart;

import tech.tablesaw.api.ColumnType;
import tech.tablesaw.columns.Column;

import static tech.tablesaw.api.ColumnType.*;

/**
 * When plotting a chart we need to be able to determine whether the column is numeric or categorical
 * When validating a series input we can consider LONG and DOUBLE columns to be OK but not a STRING (categorical) and
 * a DOUBLE (numeric). This class makes this easy to do that.
 * TODO: maybe do something with dates
 */
public class DataType {

  public static final String NUMERIC = "numeric";
  public static final String CHARACTER = "character";

  public static boolean equals(ColumnType one, ColumnType two) {
    String oneType = dataType(one);
    String twoType = dataType(two);
    return oneType.equals(twoType);
  }

  public static boolean differs(ColumnType one, ColumnType two) {
    return !equals(one, two);
  }

  public static String dataType(ColumnType columnType) {
    if (ColumnType.STRING.equals(columnType) || ColumnType.TEXT.equals(columnType)) {
      return CHARACTER;
    } else {
      return NUMERIC;
    }
  }

  public static boolean isCharacter(ColumnType columnType) {
    return CHARACTER.equals(dataType(columnType));
  }

  public static boolean isCategorical(Column column) {
    ColumnType type = column.type();
    return CHARACTER.equals(dataType(type));
  }

  public static String sqlType(ColumnType columnType, int... varcharSize) {
    if (SHORT.equals(columnType)) {
      return "SMALLINT";
    }
    if (INTEGER.equals(columnType)) {
      return "INTEGER";
    }
    if (LONG.equals(columnType)) {
      return "BIGINT";
    }
    if (FLOAT.equals(columnType)) {
      return "REAL";
    }
    if (BOOLEAN.equals(columnType)) {
      return "BIT";
    }
    if (STRING.equals(columnType)) {
      return "VARCHAR(" + (varcharSize.length > 0 ? varcharSize[0] : 8000) + ")";
    }
    if (DOUBLE.equals(columnType)) {
      return "DOUBLE";
    }
    if (LOCAL_DATE.equals(columnType)) {
      return "DATE";
    }
    if(LOCAL_TIME.equals(columnType)) {
      return "TIME";
    }
    if (LOCAL_DATE_TIME.equals(columnType)) {
      return "TIMESTAMP";
    }
    if (INSTANT.equals(columnType)) {
      return "TIMESTAMP";
    }
    if (TEXT.equals(columnType)) {
      return "CLOB";
    }
    return "VARCHAR(8000)";
  }
}
