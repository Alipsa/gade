package se.alipsa.gade.code.jstab;

import se.alipsa.gade.console.CountDownTask;

public abstract class JsTask extends CountDownTask<Void> {

  @Override
  public Thread createThread() {
    Thread thread = new Thread(this);
    thread.setDaemon(false);
    return thread;
  }
}
