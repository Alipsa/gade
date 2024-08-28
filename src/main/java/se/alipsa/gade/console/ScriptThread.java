package se.alipsa.gade.console;

import javafx.concurrent.Task;
import se.alipsa.gade.TaskListener;

public class ScriptThread extends Thread {


  TaskListener listener;

  public ScriptThread(Task task, TaskListener listener) {
    super(task);
    this.listener = listener;
  }

  @Override
  public void start() {
    listener.taskStarted();
    super.start();
  }

  @Override
  public void interrupt() {
    super.interrupt();
    listener.taskEnded();
  }


}
