package se.alipsa.gade.console;

import se.alipsa.gade.TaskListener;

import java.util.function.Function;

public abstract class GroovyTask extends CountDownTask<Void> {


  public GroovyTask(TaskListener taskListener) {
    super(taskListener);
  }

  @Override
  public ScriptThread createThread() {
    ScriptThread thread = new ScriptThread(this, taskListener);
    thread.setDaemon(false);
    return thread;
  }
}
