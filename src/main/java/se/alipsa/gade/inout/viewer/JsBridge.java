package se.alipsa.gade.inout.viewer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JsBridge {

  private static Logger log = LogManager.getLogger();
  public void exit() {
    log.info("Exit viewer");
  }

  public void log(String text) {
    log.info(text);
  }

  public boolean onError(String msg, String url, Integer line, Integer col, Object exception) {
    log.warn("Javascript error: {}:{} {}; {}", url, line, exception, msg);
    return true;
  }
}
