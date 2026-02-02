package se.alipsa.gade.code.sqltab;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.console.ScriptThread;
import se.alipsa.gade.environment.connections.ConnectionException;
import se.alipsa.gade.environment.connections.ConnectionHandler;
import se.alipsa.matrix.bigquery.Bq;
import se.alipsa.matrix.core.Matrix;

public class BigQueryTask extends QueryTask {

  private static final Logger log = LogManager.getLogger(BigQueryTask.class);

  ConnectionHandler connectionHandler;
  String[] batchedQry;
  Gade gui;

  public BigQueryTask(ConnectionHandler ch, Gade gui, String[] batchedQry, SqlTab sqlTab) {
    super(sqlTab);
    connectionHandler = ch;
    this.batchedQry = batchedQry;
    this.gui = gui;
  }

  @Override
  void abort() throws ConnectionException {
    log.warn("Abort is not yet implemented");
  }

  @Override
  public Object execute() throws Exception {
    if (batchedQry == null || batchedQry.length == 0) {
      log.debug("No query to execute");
      return null;
    }
    if (batchedQry.length > 1) {
      log.warn("Multiple query statements detected, not sure how to handle this");
    }
    AtomicInteger queryCount = new AtomicInteger(1);
    Bq bq = new Bq(connectionHandler.getConnectionInfo().getUrl());
    Matrix table = bq.query(batchedQry[0]);
    Platform.runLater(() ->
        gui.getInoutComponent().viewTable(table, sqlTab.getTitle() + " " + queryCount.getAndIncrement() + ".")
    );
    return bq;
  }

}
