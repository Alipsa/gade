package se.alipsa.gade.console;

import java.util.function.Function;

public abstract class GroovyTask extends CountDownTask<Void> {


  public GroovyTask() {
  }

  @Override
  public Thread createThread() {
    Thread thread = new Thread(this);
    thread.setDaemon(false);
    return thread;
  }
}
