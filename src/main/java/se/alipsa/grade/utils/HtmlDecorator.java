package se.alipsa.grade.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;

public class HtmlDecorator {

  private static final Logger log = LogManager.getLogger();

  private static final String HIGHLIGHT_JS_CSS_PATH = "highlightJs/default.css";
  private static final String HIGHLIGHT_JS_SCRIPT_PATH = "highlightJs/highlight.pack.js";
  private static final String BOOTSTRAP_CSS_PATH = "META-INF/resources/webjars/bootstrap/5.1.3/css/bootstrap.css";
  private static final String HIGHLIGHT_JS_INIT = "\n<script>hljs.initHighlightingOnLoad();</script>\n";
  // The highlightJs stuff is in the mdr package
  public static final String HIGHLIGHT_JS_CSS = "\n<link rel='stylesheet' href='" + resourceUrlExternalForm(HIGHLIGHT_JS_CSS_PATH) + "'>\n";
  public static final String HIGHLIGHT_JS_SCRIPT = "\n<script src='" + resourceUrlExternalForm(HIGHLIGHT_JS_SCRIPT_PATH) + "'></script>\n";
  public static final String BOOTSTRAP_CSS = resourceUrlExternalForm(BOOTSTRAP_CSS_PATH);

  private static String resourceUrlExternalForm(String resource) {
    URL url = FileUtils.getResourceUrl(resource);
    return url == null ? "" : url.toExternalForm();
  }

  public static String decorate(String html, boolean withMargin, boolean embed) {
    return "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n"
        + getHighlightStyle(true)
        + getBootstrapStyle(true)
        + getHighlightCustomStyle()
        + (withMargin ? "\n</head>\n<body style='margin-left: 15px; margin-right: 15px'>\n" : "\n</head>\n<body>\n")
        + html
        + "\n</body>\n"
        + getHighlightJs(true)
        + getHighlightInitScript()
        + "\n</html>";
  }

  public static String getHighlightStyle(boolean embed) {
    if (embed) {
      try {
        return "\n<style>\n" + FileUtils.readContent(HIGHLIGHT_JS_CSS_PATH) + "\n</style>\n";
      } catch (IOException e) {
        log.warn("Failed to get content of highlight css, falling back to external link.", e);
      }
    }
    return HIGHLIGHT_JS_CSS;
  }

  public static String getHighlightJs(boolean embed) {
    if (embed) {
      try {
        return "\n<script>" + FileUtils.readContent(HIGHLIGHT_JS_SCRIPT_PATH) + "</script>\n";
      } catch (IOException e) {
        log.warn("Failed to get content of highlight js, falling back to external link.", e);
      }
    }
    return HIGHLIGHT_JS_SCRIPT;
  }

  public static String getBootstrapStyle(boolean embed) {
    if (embed) {
      try {
        // @charset directive is not allowed when embedding the stylesheet
        String css = FileUtils.readContent(BOOTSTRAP_CSS_PATH).replace("@charset \"UTF-8\";", "\n");
        return "\n<style>\n" + css + "\n</style>\n";
      } catch (IOException e) {
        log.warn("Failed to read content to embed, resort to external ref instead", e);
      }
    }
    return "<link rel='stylesheet' href='" + BOOTSTRAP_CSS + "'>";
  }

  public static String getHighlightCustomStyle() {
    return "\n<style>code { color: black } .hljs-string { color: DarkGreen } .hljs-number { color: MidnightBlue } "
        + ".hljs-built_in { color: Maroon } .hljs-literal { color: MidnightBlue }</style>\n";
  }

  public static String getHighlightInitScript() {
    return HIGHLIGHT_JS_INIT;
  }
}
