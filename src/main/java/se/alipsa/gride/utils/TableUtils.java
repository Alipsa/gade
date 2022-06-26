package se.alipsa.gride.utils;

import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;

import java.util.ArrayList;
import java.util.List;

public class TableUtils {

  public static <T> List<List<T>> transpose(List<List<T>> matrix) {
    List<List<T>> grid = new ArrayList<List<T>>();
    final int N = matrix.get(0).size();
    for (int i = 0; i < N; i++) {
      List<T> col = new ArrayList<T>();
      for (List<T> row : matrix) {
        col.add(row.get(i));
      }
      grid.add(col);
    }
    return grid;
  }

  public static Object[][] transpose(Object[][] matrix){
    int m = matrix.length;
    int n = matrix[0].length;

    Object[][] transposedMatrix = new Object[n][m];

    for(int x = 0; x < n; x++) {
      for(int y = 0; y < m; y++) {
        transposedMatrix[x][y] = matrix[y][x];
      }
    }
    return transposedMatrix;
  }

  public static List<List<Object>> toRowList(Table table) {
    List<List<Object>> rowList = new ArrayList<>(table.rowCount());
    int ncol = table.columnCount();
    for (Row row : table) {
      List<Object> r = new ArrayList<>();
      for (int i = 0; i < ncol; i++) {
        r.add(row.getObject(i));
      }
      rowList.add(r);
    }
    return  rowList;
  }
}
