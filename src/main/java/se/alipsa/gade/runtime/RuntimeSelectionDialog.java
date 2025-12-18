package se.alipsa.gade.runtime;

import java.util.List;
import java.util.Optional;
import javafx.scene.control.ChoiceDialog;
import se.alipsa.gade.Gade;
import se.alipsa.gade.utils.GuiUtils;

/**
 * Simple dialog used when the selected runtime is not available.
 */
public class RuntimeSelectionDialog {

  public Optional<RuntimeConfig> select(RuntimeConfig missingRuntime, List<RuntimeConfig> alternatives) {
    ChoiceDialog<RuntimeConfig> dialog = new ChoiceDialog<>(alternatives.isEmpty() ? null : alternatives.getFirst(), alternatives);
    dialog.setTitle("Runtime unavailable");
    dialog.setHeaderText("Runtime " + missingRuntime.getName() + " is not available for this project.");
    dialog.setContentText("Select a runtime to use:");
    dialog.setResizable(true);
    GuiUtils.addStyle(Gade.instance(), dialog);
    return dialog.showAndWait();
  }
}
