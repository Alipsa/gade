package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.model.centralsearch.CentralSearchResult;
import se.alipsa.gade.utils.FileUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CentralSearchTest {

  @Test
  public void testJsonDeserialization() throws IOException {
    String json = FileUtils.readContent(FileUtils.getResource("utils/centralSearchResponse.json"));
    ObjectMapper mapper = new ObjectMapper();
    var result = mapper.readValue(json, CentralSearchResult.class);
    assertEquals(20, result.response.docs.size());
    assertEquals("org.matheclipse", result.response.docs.get(0).g);
    assertEquals("tech.tablesaw", result.response.docs.get(1).g);
    assertEquals("tablesaw-core", result.response.docs.get(1).a);
  }
}
