package se.alipsa.gade.code.sqltab;

import com.manticore.jsqlformatter.JSQLFormatter;
import javafx.collections.FXCollections;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.gade.Gade;
import se.alipsa.gade.TaskListener;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.TextAreaTab;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.environment.connections.ConnectionHandler;
import se.alipsa.groovy.datautil.ConnectionInfo;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.SqlParser;

import java.io.File;
import java.sql.*;
import java.util.Set;

public class SqlTab extends TextAreaTab implements TaskListener {

  private SqlTextArea sqlTextArea = null;
  private final Button formatButton;
  private final Button executeButton;
  private final ComboBox<ConnectionInfo> connectionCombo;

  private final CheckBox keepConnectionOpenCheckBox;
  private Connection con;

  private static final Logger log = LogManager.getLogger(SqlTab.class);

  public static final int PRINT_QUERY_LENGTH = 30;

  public SqlTab(String title, Gade gui) {
    super(gui, CodeType.SQL);
    setTitle(title);

    executeButton = new Button("Run");
    executeButton.setDisable(true);
    executeButton.setOnAction(e -> executeQuery(getTextContent()));
    buttonPane.getChildren().add(executeButton);

    formatButton = new Button("Format");
    formatButton.setTooltip(new Tooltip("Format SQL code"));
    formatButton.setOnAction(e -> {
      if (sqlTextArea == null) {
        return;
      }
      String original = getTextContent().trim();
      boolean endsWithSemicolon = original.endsWith(";");
      String[] options = new String[] {
          "indentWidth=2",
          "keywordSpelling=UPPER",
          "functionSpelling=KEEP",
          "objectSpelling=LOWER",
          "separation=BEFORE"
      };
      try {
        String formatted = JSQLFormatter.format(original, options).trim();
        if (!endsWithSemicolon && formatted.endsWith(";")) {
          formatted = formatted.substring(0, formatted.length() - 1).trim();
        }
        if (formatted.contains("-- failed to format")) {
          Alerts.warn("SQL Format Warning", formatted);
          return;
        }

        if (sqlTextArea.hasSelection()) {
          replaceSelectedContent(formatted);
        } else {
          replaceContentText(formatted, false);
        }

      } catch (Exception ex) {
        ExceptionAlert.showAlert("Failed to format SQL", ex);
      }

    });
    buttonPane.getChildren().add(formatButton);

    connectionCombo = new ComboBox<>();
    connectionCombo.setTooltip(new Tooltip("Create connections in the Connections tab \nand select the name here"));
    connectionCombo.getSelectionModel().selectedItemProperty().addListener(
        (options, oldValue, newValue) -> executeButton.setDisable(false)
    );
    buttonPane.getChildren().add(connectionCombo);
    updateConnections();

    keepConnectionOpenCheckBox = new CheckBox("Keep connection open");
    buttonPane.getChildren().add(keepConnectionOpenCheckBox);

    keepConnectionOpenCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
      if (oldValue == true && newValue == false && con != null) {
        try {
          con.close();
          con = null;
          gui.getConsoleComponent().addOutput("", "Connection closed", false, true);
        } catch (SQLException e) {
          con = null;
          ExceptionAlert.showAlert("Failed to close connection", e);
        }
      }
    });

    setOnCloseRequest(r -> {
      boolean isClosing = super.checkSave(getGui());
      if (con != null && isClosing) {
        try {
          con.close();
        } catch (SQLException e) {
          log.warn("Failed to close connection when closing tab " + title, e);
        }
      }
    });

    sqlTextArea = new SqlTextArea(this);
    VirtualizedScrollPane<SqlTextArea> scrollPane = new VirtualizedScrollPane<>(sqlTextArea);
    pane.setCenter(scrollPane);
  }

  public void updateConnections() {
    ConnectionInfo current = connectionCombo.getValue();
    Set<ConnectionInfo> connectionInfos = gui.getEnvironmentComponent().getConnections();
    connectionCombo.setItems(FXCollections.observableArrayList(connectionInfos));
    int rows = Math.min(5, connectionInfos.size());
    connectionCombo.setVisibleRowCount(rows);
    if (current != null && connectionCombo.getItems().contains(current)) {
      connectionCombo.setValue(current);
    } else if (connectionCombo.getItems().size() == 1) {
      connectionCombo.getSelectionModel().select(0);
    }
  }

  public void removeConnection(String connectionName) {
    connectionCombo.getItems().removeIf(c -> c.getName().equals(connectionName));
  }

  public void setNormalCursor() {
    gui.setNormalCursor();
    sqlTextArea.setCursor(Cursor.DEFAULT);
  }

  private void setWaitCursor() {
    gui.setWaitCursor();
    sqlTextArea.setCursor(Cursor.WAIT);
  }

  void executeQuery(String sqlCode) {
    if (executeButton.isDisabled()) {
      Alerts.info("Cannot run SQL", "You  must select a database connection first!");
      return;
    }
    setWaitCursor();
    final ConsoleComponent consoleComponent = getGui().getConsoleComponent();
    // TODO: Make it possible to interrupt the process when clicking the icon
    consoleComponent.running();
    StringBuilder parseMessage = new StringBuilder();
    // The parser will not be able to understand more complex queries in which case
    // the whole sql code will be in batchedQry[0]
    String[] batchedQry = SqlParser.split(sqlCode, parseMessage);
    if (!parseMessage.isEmpty()) {
      consoleComponent.addWarning(getTitle(), parseMessage.toString() + '\n', false);
    }
    consoleComponent.addOutput(getTitle(), "Query contains " + batchedQry.length + " statements", false, true);
    ConnectionInfo ci = connectionCombo.getValue();
    QueryTask task;
    ConnectionHandler ch = new ConnectionHandler(ci);
    if (ch.isJdbc()) {
      task = new SqlTask(ch, con, gui, batchedQry, SqlTab.this);
    } else if (ch.isBigQuery()) {
      task = new BigQueryTask(ch, gui, batchedQry, SqlTab.this);
    } else {
      Alerts.warn("Unknown connection type", "The connection is of unknown type: " + ch.getConnectionType());
      return;
    }

    task.setOnSucceeded(e -> {
      taskEnded();
      setNormalCursor();
      consoleComponent.waiting();
      consoleComponent.addOutput("", "Success", true, true);
      var result = task.getValue();
      if (result instanceof Connection c) {
        con = c;
      }
    });

    task.setOnFailed(e -> {
      taskEnded();
      setNormalCursor();
      consoleComponent.waiting();
      Throwable exc = task.getException();
      consoleComponent.addWarning("","\nFailed to execute query\n" + exc, true);
      String clazz = exc.getClass().getName();
      String message = exc.getMessage() == null ? "" : "\n" + exc.getMessage();
      ExceptionAlert.showAlert("Query failed: " + clazz + message, exc );
    });
    Thread scriptThread = new SqlThread(task, this);
    scriptThread.setContextClassLoader(gui.getConsoleComponent().getSessionClassloader());
    scriptThread.setDaemon(false);
    consoleComponent.startTaskWhenOthersAreFinished(task, "sql");
  }

  public boolean keepConnectionOpen() {
    return keepConnectionOpenCheckBox.isSelected();
  }

  @Override
  public File getFile() {
    return sqlTextArea.getFile();
  }

  @Override
  public void setFile(File file) {
    sqlTextArea.setFile(file);
  }

  @Override
  public String getTextContent() {
    return sqlTextArea.getTextContent();
  }

  @Override
  public String getAllTextContent() {
    return sqlTextArea.getAllTextContent();
  }

  @Override
  public void replaceContentText(int start, int end, String content) {
    sqlTextArea.replaceContentText(start, end, content);
  }

  public void replaceSelectedContent(String content) {
    var indexArea = sqlTextArea.getSelection();
    replaceContentText(indexArea.getStart(), indexArea.getEnd(), content);
  }

  @Override
  public void replaceContentText(String content, boolean isReadFromFile) {
    sqlTextArea.replaceContentText(content, isReadFromFile);
  }

  @Override
  public CodeTextArea getCodeArea() {
    return sqlTextArea;
  }

  @Override
  public void taskStarted() {
    executeButton.setDisable(true);
  }

  @Override
  public void taskEnded() {
    executeButton.setDisable(false);
  }
}
