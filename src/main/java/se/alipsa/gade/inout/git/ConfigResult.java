package se.alipsa.gade.inout.git;

import org.eclipse.jgit.lib.CoreConfig;

public class ConfigResult {

  public CoreConfig.AutoCRLF autoCRLF;

  @Override
  public String toString() {
    return "autoCRLF = " + autoCRLF;
  }
}
