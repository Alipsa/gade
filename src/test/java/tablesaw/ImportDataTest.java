package tablesaw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.tablesaw.api.ColumnType.*;

import org.junit.jupiter.api.Test;
import tech.tablesaw.api.*;
import tech.tablesaw.io.json.*;

import java.io.IOException;

public class ImportDataTest {

  @Test
  public void testJsonImport() throws IOException {
    var jsonUrl = getClass().getResource("/data/glaciers.json");

    var options = JsonReadOptions.builder(jsonUrl)
        .columnTypes(f -> switch (f) {
          case "Year", "Number of observations" -> INTEGER;
          case "Mean cumulative mass balance" -> DOUBLE;
          default -> STRING;
        })
        .build();

    var glaciers = Table.read().usingOptions(options);
    assertEquals(70, glaciers.rowCount());
  }
}
