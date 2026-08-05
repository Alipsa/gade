package se.alipsa.gade.code.bashtab;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.ExecutableTab;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gade.utils.ExceptionAlert;

public class BashTab extends ExecutableTab {

  private final BashTextArea bashTextArea;

  private static final Logger log = LogManager.getLogger(BashTab.class);

  public BashTab(String title, Gade gui) {
    super(gui, CodeType.BASH);
    setTitle(title);
    bashTextArea = new BashTextArea(this);
    VirtualizedScrollPane<BashTextArea> scrollPane = new VirtualizedScrollPane<>(bashTextArea);
    pane.setCenter(scrollPane);
  }

  @Override
  protected void executeAction() {
    runBash(getTextContent());
  }

  @Override
  protected CodeTextArea getTextArea() {
    return bashTextArea;
  }

  public void runBash() {
    runBash(getTextContent());
  }

  public void runBash(final String content) {
    ConsoleComponent consoleComponent = gui.getConsoleComponent();
    consoleComponent.running();

    BashTask task = new BashTask(content, getFile(), gui, this) {
      @Override
      public Void execute() throws Exception {
        try {
          return super.execute();
        } catch (RuntimeException e) {
          throw new Exception(e);
        }
      }
    };

    task.setOnSucceeded(e -> {
      consoleComponent.getConsole().flush();
      consoleComponent.promptAndScrollToEnd();
      consoleComponent.waiting();
      taskEnded();
    });

    task.setOnFailed(e -> {
      taskEnded();
      Throwable throwable = task.getException();
      Throwable ex = throwable.getCause();
      if (ex == null) {
        ex = throwable;
      }
      consoleComponent.waiting();
      ExceptionAlert.showAlert(ex.getMessage(), ex);
      gui.getConsoleComponent().promptAndScrollToEnd();
    });
    consoleComponent.startTaskWhenOthersAreFinished(task, "bash");
  }
}
