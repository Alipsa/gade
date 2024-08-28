package se.alipsa.gade.code.sqltab;

import static se.alipsa.gade.code.sqltab.SqlTab.PRINT_QUERY_LENGTH;

import javafx.application.Platform;
import javafx.concurrent.Task;
import se.alipsa.gade.Gade;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.console.CountDownTask;
import se.alipsa.gade.console.ScriptThread;
import se.alipsa.gade.utils.StringUtils;
import se.alipsa.groovy.datautil.ConnectionInfo;
import se.alipsa.groovy.matrix.Matrix;

import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SqlTask extends CountDownTask<Connection> {

  Statement statement;
  ConnectionInfo ci;
  Connection con;
  Gade gui;
  String[] batchedQry;
  boolean keepConnectionOpen;
  String title;
  SqlTab sqlTab;

  public SqlTask(ConnectionInfo ci, Connection con, Gade gui, String[] batchedQry, boolean keepConnectionOpen, SqlTab sqlTab) {
    super(sqlTab);
    this.ci = ci;
    this.con = con;
    this.gui = gui;
    this.batchedQry = batchedQry;
    this.keepConnectionOpen = keepConnectionOpen;
    this.title = sqlTab.getTitle();
    this.sqlTab = sqlTab;
  }

  @Override
  public ScriptThread createThread() {
    SqlThread thread = new SqlThread(this, sqlTab);
    thread.setDaemon(false);
    return thread;
  }


  @Override
  public Connection execute() throws Exception {
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
        statement = stm;
        for (String qry : batchedQry) {
          if (qry.isBlank()) {
            continue;
          }
          //log.info("{}. Executing SQL: {}", stmCount++, qry);
          boolean hasMoreResultSets = statement.execute(qry);
          int capLen = Math.min(qry.length(), PRINT_QUERY_LENGTH);
          String queryCapture = StringUtils.fixedLengthString(qry.substring(0, capLen).trim(), PRINT_QUERY_LENGTH);

          while (hasMoreResultSets || statement.getUpdateCount() != -1) {
            printWarnings("statement", statement.getWarnings());
            statement.clearWarnings();
            if (hasMoreResultSets) {
              try (ResultSet rs = statement.getResultSet()) {
                printWarnings("resultset", rs.getWarnings());
                Matrix table = Matrix.create(rs);
                Platform.runLater(() ->
                    gui.getInoutComponent().viewTable(table, title + " " + queryCount.getAndIncrement() + ".")
                );
              }
            } else { // if ddl/dml/...
              int queryResult = stm.getUpdateCount();
              if (queryResult == -1) { // no more queries processed
                continue;
              }

              Platform.runLater(() ->
                  gui.getConsoleComponent().addOutput("", new StringBuilder()
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
          // We must have one printWarnings here if the statement only contains messages
          printWarnings("statement", stm.getWarnings());
          stm.clearWarnings();
        }
      }
      con.commit(); // maybe only commit if keepConnectionOpenCheckBox is unselected
      printWarnings("connection", con.getWarnings());
      con.clearWarnings();
    } catch (SQLException e) {
      if (con != null) {
        con.rollback();
      }
      throw e;
    } finally {
      if (con != null) {
        if (!keepConnectionOpen) {
          con.close();
          con = null;
        }
      }
    }
    return null;
  }

  private void printWarnings(String context, SQLWarning warning) {
    final ConsoleComponent consoleComponent = gui.getConsoleComponent();
    while (warning != null) {
      String message = warning.getMessage();
      if ("statement".equals(context)) {
        Platform.runLater(
            () -> consoleComponent.addExternalMessage("", message, false, true)
        );
      } else {
        Platform.runLater(
            () -> consoleComponent.addWarning(context, message + "\n", false)
        );
      }
      warning = warning.getNextWarning();
    }
  }

  public void abort() throws SQLException {
    if (statement != null) {
      statement.cancel();
    }
  }
}
