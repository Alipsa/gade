package tablesaw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.tablesaw.api.ColumnType.*;

import org.junit.jupiter.api.Test;
import tech.tablesaw.api.*;
import tech.tablesaw.io.json.*;
import tech.tablesaw.io.xml.XmlReadOptions;

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

  @Test
  public void testXmlImport() throws IOException {
    var xmlUrl = getClass().getResource("/data/glaciers.xml");
    var options = XmlReadOptions.builder(xmlUrl)
        .columnTypes(f -> switch (f) {
          case "Year", "Number_of_observations" -> INTEGER;
          case "Mean_cumulative_mass_balance" -> DOUBLE;
          default -> STRING;
        })
        .build();
    var glaciers = Table.read().usingOptions(options);
    assertEquals(70, glaciers.rowCount());
    //System.out.println(glaciers);
    //System.out.println(glaciers.structure());
  }
}
