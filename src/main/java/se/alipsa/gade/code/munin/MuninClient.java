package se.alipsa.gade.code.munin;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.model.MuninConnection;
import se.alipsa.gade.model.MuninReport;
import se.alipsa.simplerest.RequestMethod;
import se.alipsa.simplerest.Response;
import se.alipsa.simplerest.RestClient;

import java.util.List;
import java.util.Map;
import se.alipsa.simplerest.RestException;

import static se.alipsa.simplerest.CommonHeaders.basicAuthHeader;

public class MuninClient {

  private static final Logger log = LogManager.getLogger();
  private static RestClient restClient;

  private static RestClient createClient() throws RestException {
    if (restClient == null) {
      restClient = new RestClient();
    }
    return restClient;
  }

  public static Map<String, List<String>> fetchReportInfo(MuninConnection con) throws Exception {
    RestClient client = createClient();
    Response response = client.get(con.target("/api/getReportInfo"), basicAuthHeader(con.getUserName(), con.getPassword()));

    if (response.getResponseCode() != 200) {
      throw new Exception("Failed to fetch report info on Munin server: " + con.target()
          + ". The response code was " + response.getResponseCode());
    }
    try {
      return response.getForType(new TypeReference<>(){});
    } catch (JsonProcessingException e) {
      throw new Exception("Failed to fetch report info on Munin server: " + con.target(), e);
    }
  }

  public static List<String> fetchReportGroups(MuninConnection con) throws Exception {
    RestClient client = createClient();

    Response response = client.get(con.target("/api/getReportGroups"), basicAuthHeader(con.getUserName(), con.getPassword()));
    if (response.getResponseCode() != 200) {
      throw new Exception("Failed to fetch report groups on Munin server: " + con.target()
          + ". The response code was " + response.getResponseCode());
    }
    try {
      return response.getObjectList(String.class);
    } catch (JsonProcessingException e) {
      throw new Exception("Failed to fetch report groups on Munin server: " + con.target(), e);
    }
  }

  public static List<MuninReport> fetchReports(MuninConnection con, String groupName) throws Exception {
    RestClient client = createClient();
    String url = con.target("/api/getReports", "groupName", groupName);
    log.info("Calling {}", url);
    Response response = client.get(url, basicAuthHeader(con.getUserName(), con.getPassword()));

    if (response.getResponseCode() != 200) {
      throw new Exception("Failed to fetch reports on Munin server: " + con.target()
          + ". The response code was " + response.getResponseCode());
    }

    try {
      return response.getObjectList(MuninReport.class);
    } catch (JsonProcessingException e) {
      throw new Exception("Failed to fetch reports on Munin server: " + con.target(), e);
    }
  }

  public static void publishReport(MuninConnection muninConnection, MuninReport muninReport, boolean newReport) throws Exception {
    if(newReport) {
      addReport(muninConnection, muninReport);
    } else {
      updateReport(muninConnection, muninReport);
    }
  }

  public static void addReport(MuninConnection con, MuninReport report) throws Exception {
    upsertReport(con, report, "/api/addReport", RequestMethod.POST);
  }

  public static void updateReport(MuninConnection con, MuninReport report) throws Exception {
    upsertReport(con, report, "/api/updateReport", RequestMethod.PUT);
  }

  private static void upsertReport(MuninConnection con, MuninReport report, String url, String method) throws Exception {
    RestClient client = createClient();
    Response response;

    if (RequestMethod.PUT.equals(method)) {
      response = client.put(con.target(url), report, basicAuthHeader(con.getUserName(), con.getPassword()));
    } else if (RequestMethod.POST.equals(method)) {
      response = client.post(con.target(url), report, basicAuthHeader(con.getUserName(), con.getPassword()));
    } else {
      throw new IllegalArgumentException("Unknown method: "  + method);
    }

    if (response.getResponseCode() != 200) {
      throw new Exception("Failed to publish report to Munin server: " + con.target(url)
                          + ". The response code was " + response.getResponseCode()
                          + "\n, Error message was '" + response.getPayload() + "'"
      );
    }
  }


}
