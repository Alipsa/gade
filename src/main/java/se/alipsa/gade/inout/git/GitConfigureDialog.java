package se.alipsa.gade.inout.git;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.StoredConfig;
import se.alipsa.gade.Gade;
import se.alipsa.gade.utils.GuiUtils;

public class GitConfigureDialog extends Dialog<ConfigResult> {

  private final Git git;
  private final ComboBox<CoreConfig.AutoCRLF> autoCrLfCombo;

  public GitConfigureDialog(Git git) {
    this.git = git;
    StoredConfig config = git.getRepository().getConfig();
    setTitle("Global options");

    getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(10, 15, 10, 10));
    getDialogPane().setContent(grid);

    Label autoCrlfLabel = new Label("autoCrLf");
    grid.add(autoCrlfLabel, 0,0);
    autoCrLfCombo = new ComboBox<>();

    autoCrLfCombo.getItems().addAll(CoreConfig.AutoCRLF.values());
    autoCrLfCombo.getSelectionModel().select(config.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
        ConfigConstants.CONFIG_KEY_AUTOCRLF, CoreConfig.AutoCRLF.INPUT));
    grid.add(autoCrLfCombo, 1,0);

    Gade gui = Gade.instance();
    GuiUtils.addStyle(gui, this);

    setResultConverter(button -> button == ButtonType.OK ? createResult() : null);
  }

  private ConfigResult createResult() {
    ConfigResult res = new ConfigResult();
    res.autoCRLF = autoCrLfCombo.getValue();
    return res;
  }
}
