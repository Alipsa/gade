package tablesaw;

import org.junit.jupiter.api.Test;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.xml.XmlWriteOptions;
import tech.tablesaw.io.xml.XmlWriter;

import java.io.StringWriter;

public class ExportDataTest {

  @Test
  public void testXmlExport() {
    var url = getClass().getResource("/data/glaciers.txt");
    var table = Table.read().csv(url);
    table.setName("glaciers");

    StringWriter writer = new StringWriter();

    var options = XmlWriteOptions.builder(writer)
        .build();
    table.write().usingOptions(options);

    System.out.println("XML content: " + writer);
  }
}
