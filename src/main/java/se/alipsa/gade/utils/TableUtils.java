package se.alipsa.gade.utils;

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

  public static List<List<?>> transposeAny(List<List<?>> matrix) {
    List<List<?>> grid = new ArrayList<>();
    final int N = matrix.get(0).size();
    for (int i = 0; i < N; i++) {
      List<Object> col = new ArrayList<>();
      for (List<?> row : matrix) {
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
}
