package se.alipsa.gade.environment;

import static se.alipsa.gade.Constants.INDENT;

import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.StyleClassedTextArea;
import se.alipsa.gade.Gade;
import se.alipsa.gade.UnStyledCodeArea;
//import se.alipsa.gade.environment.connections.ConnectionInfo;
import se.alipsa.groovy.datautil.ConnectionInfo;
import se.alipsa.gade.environment.connections.ConnectionsTab;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class EnvironmentComponent extends TabPane {

  private static final Logger LOG = LogManager.getLogger();

  private final UnStyledCodeArea envTa;

  ConnectionsTab connectionsTab;
  HistoryTab historyTab;

  List<ContextFunctionsUpdateListener> contextFunctionsUpdateListeners = new ArrayList<>();

  int MAX_CONTENT_LENGTH = 200;

  public EnvironmentComponent(Gade gui) {
    Tab environment = new Tab();
    environment.setText("Environment");
    envTa = new UnStyledCodeArea();
    envTa.setEditable(false);
    envTa.getStyleClass().add("environment");
    envTa.replaceText("Environment");
    VirtualizedScrollPane<StyleClassedTextArea> scrollPane = new VirtualizedScrollPane<>(envTa);
    environment.setContent(scrollPane);
    getTabs().add(environment);

    historyTab = new HistoryTab();
    getTabs().add(historyTab);

    connectionsTab = new ConnectionsTab(gui);

    getTabs().add(connectionsTab);
    setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
  }

  public void setEnvironment(Map<String, Object> contextObjects) {
    Platform.runLater(() -> {
      envTa.clear();
      for (var entry : contextObjects.entrySet()) {
        String varName = entry.getKey();
        int start = envTa.getContent().getLength();
        envTa.appendText(varName);
        int endVar = start + varName.length();
        envTa.setStyleClass(start, endVar, "env-varName");

        Object value = entry.getValue();
        String content = "";
        if (value instanceof Collection<?> collection) {
          content = "(" + collection.size() +  " elements)";
        } else {
          content = String.valueOf(value);
          if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH) + "... (length = " + content.length() + ")";
          }
        }
        content = INDENT + value.getClass().getSimpleName() + ": " + content;
        envTa.appendText(content + "\n");
        envTa.setStyleClass(endVar + 1, endVar + content.length(), "env-varValue");
      }
    });
  }

  public void clearEnvironment() {
    envTa.clear();
  }

  public Set<ConnectionInfo> getConnections() {
    return connectionsTab.getConnections();
  }

  public void addInputHistory(String text) {
    historyTab.addInputHistory(text);
  }

  public void addOutputHistory(String text) {
    historyTab.addOutputHistory(text);
  }

  public void restarted() {
    historyTab.restarted();
  }

  public void setNormalCursor() {
    if (connectionsTab != null) {
      connectionsTab.setNormalCursor();
    }
  }

  public void addConnection(ConnectionInfo connectionInfo) {
    connectionsTab.validateAndAddConnection(connectionInfo);
  }
  public Connection connect(ConnectionInfo ci) throws SQLException {
    return connectionsTab.connect(ci);
  }

  public Set<ConnectionInfo> getDefinedConnections() {
    return connectionsTab.getDefinedConnections();
  }

  public Set<String> getDefinedConnectionsNames() {
    return connectionsTab.getDefinedConnections().stream().map(ConnectionInfo::getName).collect(Collectors.toSet());
  }
}
