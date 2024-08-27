package se.alipsa.gade.console;

import javafx.concurrent.Task;

import java.util.concurrent.CountDownLatch;

public abstract class CountDownTask<V> extends Task<V> {
  CountDownLatch countDownLatch;

  public CountDownTask() {
    this.countDownLatch = new CountDownLatch(1);
  }

  @Override
  protected V call() throws Exception {
    V result = execute();
    countDownLatch.countDown();
    return result;
  }

  public abstract V execute() throws Exception;

  public abstract Thread createThread();
}
