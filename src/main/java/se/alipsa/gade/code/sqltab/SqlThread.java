package se.alipsa.gade.code.sqltab;

import java.sql.SQLException;

public class SqlThread extends Thread {

  SqlTask task;
  SqlTab sqlTab;

  public SqlThread(SqlTask task, SqlTab sqlTab) {
    super(task);
    this.task = task;
    this.sqlTab = sqlTab;
  }

  @Override
  public void interrupt() {
    try {
      System.out.println("Cancelling query");
      task.abort();
      task.cancel(true);
    } catch (SQLException e) {
      e.printStackTrace();
    }
    super.interrupt();
    sqlTab.setNormalCursor();
    System.out.println("Query interrupted, back to normal");
  }
}
