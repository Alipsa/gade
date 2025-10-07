package se.alipsa.gade.inout;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.utils.FileUtils;

public class HelpTab extends Tab {

  private final TabPane helpPane;

  private static final Logger log = LogManager.getLogger(HelpTab.class);

  public HelpTab() {
    setText("Viewer");
    helpPane = new TabPane();
    setContent(helpPane);
    helpPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
  }

  public void showText(String content, String... title) {
    Tab tab = new Tab();
    if (title.length > 0) {
      tab.setText(title[0]);
    } else {
      tab.setText("help");
    }
    helpPane.getTabs().add(tab);
    TextArea ta = new TextArea();
    ta.setText(content);
    tab.setContent(ta);
    helpPane.getSelectionModel().select(tab);
  }

  public void showUrl(String url, String... title) {
    if (url == null) {
      log.warn("url is null, nothing to view");
      return;
    }
    Tab tab = new Tab();
    if (title.length > 0) {
      tab.setText(title[0]);
    } else {
      tab.setText(FileUtils.baseName(url));
    }
    tab.setTooltip(new Tooltip(url));
    helpPane.getTabs().add(tab);
    WebView browser = new WebView();
    browser.setContextMenuEnabled(true);
    WebEngine webEngine = browser.getEngine();
    webEngine.setJavaScriptEnabled(true);

    log.debug("Opening {} in view tab", url);
    webEngine.load(url);

    tab.setContent(browser);
    helpPane.getSelectionModel().select(tab);
  }
}
