package se.alipsa.gade.code.sqltab;

import se.alipsa.gade.console.CountDownTask;
import se.alipsa.gade.console.ScriptThread;

import java.sql.SQLException;
import se.alipsa.gade.environment.connections.ConnectionException;

public class SqlThread extends ScriptThread {

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
      System.out.println("Cancelling query");
      task.abort();
      task.cancel(true);
    } catch (ConnectionException e) {
      e.printStackTrace();
    }
    super.interrupt();
    sqlTab.setNormalCursor();
    System.out.println("Query interrupted, back to normal");
  }
}
