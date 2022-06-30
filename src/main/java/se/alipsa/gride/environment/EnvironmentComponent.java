package se.alipsa.gride.environment;

import static se.alipsa.gride.Constants.INDENT;

import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.StyleClassedTextArea;
import se.alipsa.gride.Gride;
import se.alipsa.gride.UnStyledCodeArea;
import se.alipsa.gride.environment.connections.ConnectionInfo;
import se.alipsa.gride.environment.connections.ConnectionsTab;
import tech.tablesaw.api.Table;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

public class EnvironmentComponent extends TabPane {

  private static final Logger LOG = LogManager.getLogger();

  private final UnStyledCodeArea envTa;

  ConnectionsTab connectionsTab;
  HistoryTab historyTab;

  List<ContextFunctionsUpdateListener> contextFunctionsUpdateListeners = new ArrayList<>();

  int MAX_CONTENT_LENGTH = 200;

  public EnvironmentComponent(Gride gui) {
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

  public void setEnvironment(ScriptEngine engine) {
    Platform.runLater(() -> {
      envTa.clear();
      for (var entry : engine.getBindings(ScriptContext.ENGINE_SCOPE).entrySet()) {
        String varName = entry.getKey();
        int start = envTa.getContent().getLength();
        envTa.appendText(varName);
        int endVar = start + varName.length();
        envTa.setStyleClass(start, endVar, "env-varName");

        Object value = entry.getValue();
        String content = "";
        if (value instanceof Table table) {
          var tableName = table.name() == null ? "" : table.name() + ": ";
          String colNames = String.join(", ", table.columnNames().stream().map(String::valueOf).toList());
          if (colNames.length() > MAX_CONTENT_LENGTH - 50) {
            colNames = colNames.substring(0, MAX_CONTENT_LENGTH - 50) + "...";
          }
          content = tableName
              + colNames
              + " (" + table.columnNames().size() + " columns, " + table.rowCount() + " rows)";
        } else if (value instanceof Collection<?> collection) {
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

  public void rRestarted() {
    historyTab.rRestarted();
  }

  public void updateContextFunctions(List<String> functions, List<String> objects) {
    final TreeSet<String> contextFunctions = new TreeSet<>(functions);
    final TreeSet<String> contextObjects = new TreeSet<>(objects);
    Platform.runLater(() ->
      contextFunctionsUpdateListeners.forEach(l -> l.updateContextFunctions(contextFunctions, contextObjects))
    );
  }

  public void addContextFunctionsUpdateListener(ContextFunctionsUpdateListener listener) {
    contextFunctionsUpdateListeners.add(listener);
  }

  public void removeContextFunctionsUpdateListener(ContextFunctionsUpdateListener listener) {
    contextFunctionsUpdateListeners.remove(listener);
  }

  public void setNormalCursor() {
    if (connectionsTab != null) {
      connectionsTab.setNormalCursor();
    }
  }

  public Connection connect(ConnectionInfo ci) throws SQLException {
    return connectionsTab.connect(ci);
  }
}
