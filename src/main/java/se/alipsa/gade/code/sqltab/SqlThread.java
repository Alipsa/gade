package se.alipsa.gade.code.sqltab;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.console.CountDownTask;
import se.alipsa.gade.console.ScriptThread;

import java.sql.SQLException;
import se.alipsa.gade.environment.connections.ConnectionException;

public class SqlThread extends ScriptThread {

  private static final Logger log = LogManager.getLogger(SqlThread.class);

  QueryTask task;
  SqlTab sqlTab;

  public SqlThread(QueryTask task, SqlTab sqlTab) {
    super(task, sqlTab);
    this.task = task;
    this.sqlTab = sqlTab;
  }

  @Override
  public void interrupt() {
    try {
      log.info("Cancelling query");
      task.abort();
      task.cancel(true);
    } catch (ConnectionException e) {
      e.printStackTrace();
    }
    super.interrupt();
    sqlTab.setNormalCursor();
    log.info("Query interrupted, back to normal");
  }
}
