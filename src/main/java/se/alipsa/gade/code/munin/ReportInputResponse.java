package se.alipsa.gade.code.munin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class ReportInputResponse {

  private static final Logger log = LogManager.getLogger(ReportInputResponse.class);

  private String params;
  private final Stage stage;

  public static final TypeReference<HashMap<String, Object>> TYPE_REF = new TypeReference<>() {
  };

  public ReportInputResponse(Stage stage) {
    this.stage = stage;
  }

  public void addParams(String jsonParams) {
    //System.out.println("ReportInputResponse: Got " + jsonParams);
    this.params = jsonParams;
    stage.close();
  }

  Map<String, Object> asMap() throws JsonProcessingException {
    if (params == null) return null;
    return new ObjectMapper().readValue(params, TYPE_REF);
  }
}
