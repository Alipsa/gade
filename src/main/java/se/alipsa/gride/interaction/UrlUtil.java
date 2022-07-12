package se.alipsa.gride.interaction;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class UrlUtil implements GuiInteraction {

  public boolean exists(String urlString, int timeout) {
    try {
      URL url = new URL(urlString);
      HttpURLConnection con = (HttpURLConnection) url.openConnection();
      HttpURLConnection.setFollowRedirects(false);
      con.setRequestMethod("HEAD");
      con.setConnectTimeout(timeout);
      int responseCode = con.getResponseCode();
      return responseCode == 200;
    } catch (RuntimeException | IOException e) {
      return false;
    }
  }

  @Override
  public String help() {
    return """
        UrlUtil: Convenient url utilities
        ---------------------------------
        boolean exists(String urlString, int timeout)
          attempts to connect to the url specified with a HEAD request to see if it is there or not.
        """;
  }

  @Override
  public String toString() {
    return "Convenient url utilities";
  }
}
