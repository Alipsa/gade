package se.alipsa.gade.code;

import javafx.scene.control.Button;
import se.alipsa.gade.Gade;
import se.alipsa.gade.TaskListener;

import java.io.File;

/**
 * Abstract base class for tabs that can execute code/scripts.
 * <p>
 * Provides common functionality for tabs like GroovyTab, JsTab, SqlTab, etc.
 * that have a run/execute button and implement TaskListener for managing execution state.
 * <p>
 * This class consolidates the common boilerplate pattern:
 * <ul>
 *   <li>Execute button creation and management</li>
 *   <li>TaskListener implementation (disable button during execution)</li>
 *   <li>Delegation methods for file and content operations</li>
 * </ul>
 *
 * <b>Usage:</b>
 * Subclasses must:
 * <ol>
 *   <li>Implement {@link #getTextArea()} to return the specific text area instance</li>
 *   <li>Implement {@link #executeAction()} to define what happens when the execute button is clicked</li>
 *   <li>Implement {@link #getCodeArea()} (inherited from TextAreaTab)</li>
 * </ol>
 *
 * <p>
 * The execute button is automatically added to the buttonPane in the constructor.
 * Subclasses can customize the button text via {@link #getExecuteButtonText()}.
 */
public abstract class ExecutableTab extends TextAreaTab implements TaskListener {

  /**
   * The execute/run button. Automatically disabled during task execution.
   */
  protected final Button executeButton;

  /**
   * Creates an executable tab with a default "Run" button.
   *
   * @param gui the GUI instance
   * @param codeType the code type for this tab
   */
  public ExecutableTab(Gade gui, CodeType codeType) {
    this(gui, codeType, "Run");
  }

  /**
   * Creates an executable tab with a custom button text.
   *
   * @param gui the GUI instance
   * @param codeType the code type for this tab
   * @param buttonText the text to display on the execute button
   */
  public ExecutableTab(Gade gui, CodeType codeType, String buttonText) {
    super(gui, codeType);
    executeButton = new Button(buttonText);
    executeButton.setOnAction(a -> executeAction());
    buttonPane.getChildren().add(executeButton);
  }

  /**
   * Returns the text area instance for delegation.
   * <p>
   * This method is used internally to delegate file and content operations
   * to the specific text area implementation (e.g., GroovyTextArea, SqlTextArea).
   *
   * @return the text area instance
   */
  protected abstract CodeTextArea getTextArea();

  /**
   * Executes the action when the execute button is clicked.
   * <p>
   * Subclasses should implement their execution logic here (e.g., run Groovy script,
   * execute SQL query, run JavaScript).
   */
  protected abstract void executeAction();

  /**
   * Called when a task starts. Disables the execute button.
   */
  @Override
  public void taskStarted() {
    executeButton.setDisable(true);
  }

  /**
   * Called when a task ends. Re-enables the execute button.
   */
  @Override
  public void taskEnded() {
    executeButton.setDisable(false);
  }

  // Delegation methods to text area

  @Override
  public File getFile() {
    return getTextArea().getFile();
  }

  @Override
  public void setFile(File file) {
    getTextArea().setFile(file);
  }

  @Override
  public String getTextContent() {
    return getTextArea().getTextContent();
  }

  @Override
  public String getAllTextContent() {
    return getTextArea().getAllTextContent();
  }

  @Override
  public void replaceContentText(int start, int end, String content) {
    getTextArea().replaceContentText(start, end, content);
  }

  @Override
  public void replaceContentText(String content, boolean isReadFromFile) {
    getTextArea().replaceContentText(content, isReadFromFile);
  }

  @Override
  public CodeTextArea getCodeArea() {
    return getTextArea();
  }
}
