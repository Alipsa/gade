package se.alipsa.gade.code.munin;

import se.alipsa.gade.console.CountDownTask;

public abstract class MuninTask extends CountDownTask<String> {

  @Override
  public Thread createThread() {
    Thread thread = new Thread(this);
    thread.setDaemon(false);
    return thread;
  }
}
