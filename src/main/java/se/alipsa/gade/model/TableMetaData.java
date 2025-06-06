package se.alipsa.gade.model;

import se.alipsa.matrix.core.Row;

import java.util.List;

public class TableMetaData {

  //private static final Logger log = LogManager.getLogger();

  public static final String COLUMN_META_START = " [ ";
  public static final String COLUMN_META_END = " ]";
  private String tableName;
  private String tableType;
  private String columnName;
  private Integer ordinalPosition;
  private String isNullable;
  private String dataType;
  private Integer characterMaximumLength;
  private Integer numericPrecision;
  private Integer numericScale;
  private String collationName;
  private String schemaName;


  public TableMetaData(List<Object> row) {
    setTableName(String.valueOf(row.get(0)));
    setTableType(String.valueOf(row.get(1)));
    setColumnName(String.valueOf(row.get(2)));
    setOrdinalPosition(toInt(row.get(3))); // Usually an int but on hsqldb this is a double
    setIsNullable(String.valueOf(row.get(4)));
    setDataType(String.valueOf(row.get(5).toString())); // On h2 this is an INT, on SQL Server it is VARCHAR
    setCharacterMaximumLength(toInt(row.get(6)));
    setNumericPrecision(toInt(row.get(7)));
    setNumericScale(toInt(row.get(8)));
    setCollationName(String.valueOf(row.get(9)));
    setSchemaName(String.valueOf(row.get(10)));
  }

  public TableMetaData(Row row) {
    setTableName(row.getAt(0, String.class));
    setTableType(row.getAt(1, String.class));
    setColumnName(row.getAt(2, String.class));
    Number ordinal = row.getAt(3, Number.class);
    setOrdinalPosition(ordinal == null ? null : ordinal.intValue()); // Usually an int but on hsqldb this is a double
    setIsNullable(row.getAt(4, String.class));
    setDataType(String.valueOf(row.get(5))); // On h2 this is an INT, on SQL Server it is VARCHAR
    // On h2 this is a Long, on most other db's it is an int
    Object maxLengthObj = row.get(6);
    Integer maxLength = maxLengthObj == null ? null : Long.valueOf(String.valueOf(maxLengthObj)).intValue();
    setCharacterMaximumLength(maxLength);
    setNumericPrecision(toInteger(row.get(7))); // On SQL Server, NUMERIC_PRECISION is of type SHORT and cannot be cast to INTEGER
    setNumericScale(row.getAt(8, Integer.class));
    setCollationName(row.getAt(9, String.class));
    setSchemaName(row.getAt(10, String.class));
  }

  private Integer toInteger(Object obj) {
    return obj == null ? null : Integer.parseInt(String.valueOf(obj));
  }

  public TableMetaData(Object tableName, Object tableType, Object columnName, Object ordinalPosition,
                       Object isNullable, Object dataType, Object characterMaximumLength, Object numericPrecision,
                       Object numericScale, Object collationName, Object schemaName) {

    setTableName((String) tableName);
    setTableType((String) tableType);
    setColumnName((String) columnName);
    setOrdinalPosition((Integer) ordinalPosition);
    setIsNullable((String) isNullable);
    setDataType((String) dataType);
    setCharacterMaximumLength((Integer) characterMaximumLength);
    setNumericPrecision((Integer) numericPrecision);
    setNumericScale((Integer) numericScale);
    setCollationName((String) collationName);
    setSchemaName(String.valueOf(schemaName));
  }

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  public String getTableType() {
    return tableType;
  }

  public void setTableType(String tableType) {
    this.tableType = tableType;
  }

  public String getColumnName() {
    return columnName;
  }

  public void setColumnName(String columnName) {
    this.columnName = columnName;
  }

  public Integer getOrdinalPosition() {
    return ordinalPosition;
  }

  public void setOrdinalPosition(Integer ordinalPosition) {
    this.ordinalPosition = ordinalPosition;
  }

  public String getIsNullable() {
    return isNullable;
  }

  public void setIsNullable(String isNullable) {
    this.isNullable = isNullable;
  }

  public String getDataType() {
    return dataType;
  }

  public void setDataType(String dataType) {
    String DATATYPE = dataType == null ? "" : dataType.trim().toUpperCase();
    switch (DATATYPE) {
      case "-6":
        this.dataType = "TINYINT";
        break;
      case "-5":
        this.dataType = "BIGINT";
        break;
      case "-3":
        this.dataType = "BINARY";
        break;
      case "-2":
        this.dataType = "UUID";
        break;
      case "1":
        this.dataType = "CHAR";
        break;
      case "3":
        this.dataType = "DECIMAL";
        break;
      case "4":
        this.dataType = "INT";
        break;
      case "5":
        this.dataType = "SMALLINT";
        break;
      case "7":
        this.dataType = "REAL";
        break;
      case "8":
        this.dataType = "DOUBLE";
        break;
      case "12":
        this.dataType = "VARCHAR";
        break;
      case "16":
        this.dataType = "BOOLEAN";
        break;
      case "91":
        this.dataType = "DATE";
        break;
      case "92":
        this.dataType = "TIME";
        break;
      case "93":
        this.dataType = "TIMESTAMP";
        break;
      case "1111":
        this.dataType = "OTHER";
        break;
      case "2003":
        this.dataType = "ARRAY";
        break;
      case "2004":
        this.dataType = "BLOB";
        break;
      case "2005":
        this.dataType = "CLOB";
        break;
      case "2014":
        this.dataType = "TIMESTAMP WITH TZ";
        break;
      default:
        this.dataType = dataType;
    }
  }

  public Integer getCharacterMaximumLength() {
    return characterMaximumLength;
  }

  public void setCharacterMaximumLength(Integer characterMaximumLength) {
    this.characterMaximumLength = characterMaximumLength;
  }

  public Integer getNumericPrecision() {
    return numericPrecision;
  }

  public void setNumericPrecision(Integer numericPrecision) {
    this.numericPrecision = numericPrecision;
  }

  public Integer getNumericScale() {
    return numericScale;
  }

  public void setNumericScale(Integer numericScale) {
    this.numericScale = numericScale;
  }

  public String getCollationName() {
    return collationName;
  }

  public void setCollationName(String collationName) {
    this.collationName = collationName;
  }

  public String getSchemaName() {
    return schemaName;
  }

  public void setSchemaName(String schemaName) {
    this.schemaName = schemaName;
  }

  public String asColumnString() {
    String precision = "";
    String DATATYPE = dataType == null ? "" : dataType.trim().toUpperCase();
    if (!DATATYPE.contains("(") && (DATATYPE.contains("VARCHAR") || DATATYPE.equals("CHARACTER VARYING"))) {
      precision = "(" + characterMaximumLength + ")";
    } else if (DATATYPE.equals("DECIMAL") || DATATYPE.equals("NUMBER") || DATATYPE.equals("NUMERIC")) {
      precision = "(" + numericPrecision + "," + numericScale + ")";
    }
    String nullable;
    if (isNullable.equalsIgnoreCase("YES")
        || isNullable.equalsIgnoreCase("TRUE")
        || isNullable.equalsIgnoreCase("1")) {
      nullable = "nullable";
    } else {
      nullable = "not nullable";
    }
    //System.out.println(columnName + COLUMN_META_START + dataType + precision + ", " + nullable + COLUMN_META_END);
    return columnName + COLUMN_META_START + dataType + precision + ", " + nullable + COLUMN_META_END;
  }

  private int toInt(Object obj) {
    if (obj instanceof Integer) {
      return (Integer)obj;
    }
    return (int)Math.round(Double.parseDouble(String.valueOf(obj)));
  }
}
