package se.alipsa.gride.chart;

import tech.tablesaw.api.ColumnType;

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
}
