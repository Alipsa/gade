package view;

import org.junit.jupiter.api.Test;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static se.alipsa.gade.utils.TableUtils.transposeAny;

public class ViewTest {

  @Test
  public void testViewMatrix() {
    List<List<?>> matrix = new ArrayList<>();
    matrix.add(List.of(10, "Groovy", "http://groovy.codehaus.org"));
    matrix.add(List.of(20, "Alipsa", "http://www.alipsa.se"));
    var t = transposeAny(matrix);

    List<String> header = createAnonymousHeader(t.size());
    StringColumn[] columns = new StringColumn[t.size()];
    for (int i = 0; i < columns.length; i++) {
      var heading = header.get(i);
      var stringCol = t.get(i).stream().map(String::valueOf).toArray(String[]::new);
      columns[i] = StringColumn.create(heading, stringCol);
    }
    Table table = Table.create().addColumns(columns);
    assertEquals("20", table.get(1, 0));
    assertEquals("Groovy", table.get(0, 1));
  }

  private List<String> createAnonymousHeader(int size) {
    List<String> header = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      header.add("V" + i);
    }
    return header;
  }
}
