package se.alipsa.gade.code.sqltab;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.TextAreaTab;
import se.alipsa.gade.console.ConsoleComponent;
//import se.alipsa.gade.environment.connections.ConnectionInfo;
import se.alipsa.groovy.datautil.ConnectionInfo;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.SqlParser;
import se.alipsa.gade.utils.StringUtils;
import se.alipsa.groovy.matrix.Matrix;

import java.io.File;
import java.sql.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class SqlTab extends TextAreaTab {

  private final SqlTextArea sqlTextArea;
  private final Button executeButton;
  private final ComboBox<ConnectionInfo> connectionCombo;

  private final CheckBox keepConnectionOpenCheckBox;
  private Connection con;

  private static final Logger log = LogManager.getLogger(SqlTab.class);

  private static final int PRINT_QUERY_LENGTH = 30;

  public SqlTab(String title, Gade gui) {
    super(gui, CodeType.SQL);
    setTitle(title);

    executeButton = new Button("Run");
    executeButton.setDisable(true);
    executeButton.setOnAction(e -> executeQuery(getTextContent()));
    buttonPane.getChildren().add(executeButton);

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

  private void setNormalCursor() {
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
    StringBuilder parseMessage = new StringBuilder();
    // The parser will not be able to understand more complex queries in which case
    // the whole sql code will be in batchedQry[0]
    String[] batchedQry = SqlParser.split(sqlCode, parseMessage);
    if (!parseMessage.isEmpty()) {
      consoleComponent.addWarning(getTitle(), parseMessage.toString() + '\n', false);
    }
    consoleComponent.addOutput(getTitle(), "Query contains " + batchedQry.length + " statements", false, true);

    Task<Void> updateTask = new Task<>() {
      @Override
      protected Void call() throws Exception {
        ConnectionInfo ci = connectionCombo.getValue();

        if (con == null) {
          con = gui.getEnvironmentComponent().connect(ci);
        }
        try {
          if (con == null) {
            throw new Exception("Failed to establish a connection");
          }
          con.setAutoCommit(false);
          con.setHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT);

          AtomicInteger queryCount = new AtomicInteger(1);
          try (Statement stm = con.createStatement()) {
            for (String qry : batchedQry) {
              if (qry.isBlank()) {
                continue;
              }
              //log.info("{}. Executing SQL: {}", stmCount++, qry);
              boolean hasMoreResultSets = stm.execute(qry);
              int capLen = Math.min(qry.length(), PRINT_QUERY_LENGTH);
              String queryCapture = StringUtils.fixedLengthString(qry.substring(0, capLen).trim(), PRINT_QUERY_LENGTH);

              while (hasMoreResultSets || stm.getUpdateCount() != -1) {
                printWarnings("statement", stm.getWarnings());
                stm.clearWarnings();
                if (hasMoreResultSets) {
                  try (ResultSet rs = stm.getResultSet()) {
                    printWarnings("resultset", rs.getWarnings());
                    Matrix table = Matrix.create(rs);
                    Platform.runLater(() ->
                        gui.getInoutComponent().viewTable(table, SqlTab.this.getTitle() + " " + queryCount.getAndIncrement() + ".")
                    );
                  }
                } else { // if ddl/dml/...
                  int queryResult = stm.getUpdateCount();
                  if (queryResult == -1) { // no more queries processed
                    continue;
                  }

                  Platform.runLater(() ->
                      consoleComponent.addOutput("", new StringBuilder()
                              .append(queryCount.getAndIncrement())
                              .append(". [")
                              .append(queryCapture)
                              .append("...], Rows affected: ")
                              .append(queryResult).toString()
                          , false, true)
                  );
                }
                hasMoreResultSets = stm.getMoreResults();
              }
              con.commit();
              // We must have one printWarnings here if the statement only contains messages
              printWarnings("statement", stm.getWarnings());
              stm.clearWarnings();
            }
          }
          printWarnings("connection", con.getWarnings());
          con.clearWarnings();
          con.setAutoCommit(true);
        } catch (SQLException e) {
          if (con != null) {
            con.rollback();
          }
          throw e;
        } finally {
          if (!keepConnectionOpenCheckBox.isSelected()) {
            con.close();
            con = null;
          }
        }
        return null;
      }
    };
    updateTask.setOnSucceeded(e -> {
      setNormalCursor();
      consoleComponent.addOutput("", "Success", true, true);
    });

    updateTask.setOnFailed(e -> {
      setNormalCursor();
      Throwable exc = updateTask.getException();
      consoleComponent.addWarning("","\nFailed to execute query\n" + exc, true);
      String clazz = exc.getClass().getName();
      String message = exc.getMessage() == null ? "" : "\n" + exc.getMessage();
      ExceptionAlert.showAlert("Query failed: " + clazz + message, exc );
    });

    Thread scriptThread = new Thread(updateTask);
    scriptThread.setContextClassLoader(gui.getConsoleComponent().getSession().getClass().getClassLoader());
    scriptThread.setDaemon(false);
    scriptThread.start();
  }

  private void printWarnings(String context, SQLWarning warning) {
    final ConsoleComponent consoleComponent = getGui().getConsoleComponent();
    while (warning != null) {
      String message = warning.getMessage();
      if ("statement".equals(context)) {
        Platform.runLater(
            () -> consoleComponent.addOutput("", message, false, true)
        );
      } else {
        Platform.runLater(
            () -> consoleComponent.addWarning(context, message + "\n", false)
        );
      }
      warning = warning.getNextWarning();
    }
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

  @Override
  public void replaceContentText(String content, boolean isReadFromFile) {
    sqlTextArea.replaceContentText(content, isReadFromFile);
  }

  @Override
  public CodeTextArea getCodeArea() {
    return sqlTextArea;
  }
}
