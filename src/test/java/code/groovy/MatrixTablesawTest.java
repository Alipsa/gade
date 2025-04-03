package code.groovy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.alipsa.matrix.core.Matrix;
import se.alipsa.matrix.tablesaw.TableUtil;
import se.alipsa.matrix.tablesaw.gtable.Gtable;

public class MatrixTablesawTest {

  @Test
  void testMatrixTablesaw() {
    // Create a Matrix with sample data
    Map<String, List> columns = new LinkedHashMap<>();
    columns.put("name", List.of("Alice", "Bob", "Charlie", "David", "Eve"));
    columns.put("age", List.of(25, 30, 35, 40, 45));
    columns.put("salary", List.of(50000, 60000, 70000, 80000, 90000));
    columns.put("department", List.of("HR", "IT", "Finance", "IT", "HR"));

    var matrix = Matrix.builder().data(columns)
        .types(String.class, Integer.class, Integer.class, String.class)
        .build();

    // Convert Matrix to GTable
    Gtable gTable = TableUtil.fromMatrix(matrix);
    assertEquals(5, gTable.rowCount());
    assertEquals(4, gTable.columnCount());
  }
}
