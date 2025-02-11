package se.alipsa.gade.code.sqltab;

import java.sql.Connection;
import se.alipsa.gade.console.CountDownTask;
import se.alipsa.gade.console.ScriptThread;
import se.alipsa.gade.environment.connections.ConnectionException;

public abstract class QueryTask extends CountDownTask<Object> {

  protected SqlTab sqlTab;

  public QueryTask(SqlTab taskListener) {
    super(taskListener);
    this.sqlTab = taskListener;
  }

  @Override
  public ScriptThread createThread() {
    SqlThread thread = new SqlThread(this, sqlTab);
    thread.setDaemon(false);
    return thread;
  }

  abstract void abort() throws ConnectionException;


}
