package se.alipsa.gade.runtime;

import java.util.List;

public class RuntimeEditorResult {
  private final List<RuntimeConfig> customRuntimes;
  private final RuntimeConfig selectedRuntime;

  public RuntimeEditorResult(List<RuntimeConfig> customRuntimes, RuntimeConfig selectedRuntime) {
    this.customRuntimes = customRuntimes;
    this.selectedRuntime = selectedRuntime;
  }

  public List<RuntimeConfig> getCustomRuntimes() {
    return customRuntimes;
  }

  public RuntimeConfig getSelectedRuntime() {
    return selectedRuntime;
  }
}
