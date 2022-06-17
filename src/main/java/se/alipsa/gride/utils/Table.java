package se.alipsa.gride.utils;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class Table extends ArrayList<List<Object>> {
  public Table(List<String> header, List<List<Object>> lsitMatrix) {
  }

  public Table(Vector<?> vec) {

  }

  public Table(Matrix matrix) {

  }

  public Table(ResultSet rs) {
  }

  public List<String> getHeaderList() {
    return null;
  }

  public List<List<Object>> getRowList() {
    return null;
  }

  public Object getColumnType(int i) {
    return null;
  }
}
