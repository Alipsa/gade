package se.alipsa.gade.code.jstab;

import se.alipsa.gade.TaskListener;
import se.alipsa.gade.console.CountDownTask;
import se.alipsa.gade.console.ScriptThread;

public abstract class JsTask extends CountDownTask<Void> {

  public JsTask(TaskListener taskListener) {
    super(taskListener);
  }

  @Override
  public ScriptThread createThread() {
    ScriptThread thread = new ScriptThread(this, taskListener);
    thread.setDaemon(false);
    return thread;
  }
}
