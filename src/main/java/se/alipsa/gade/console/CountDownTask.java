package se.alipsa.gade.console;

import javafx.concurrent.Task;
import se.alipsa.gade.TaskListener;

import java.util.concurrent.CountDownLatch;

public abstract class CountDownTask<V> extends Task<V> {
  CountDownLatch countDownLatch;
  protected TaskListener taskListener;

  public CountDownTask(TaskListener taskListener) {
    this.countDownLatch = new CountDownLatch(1);
    this.taskListener = taskListener;
  }

  @Override
  protected V call() throws Exception {
    V result = execute();
    countDownLatch.countDown();
    return result;
  }

  public abstract V execute() throws Exception;

  public abstract ScriptThread createThread();
}
