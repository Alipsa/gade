package se.alipsa.gade.menu;

import java.io.File;

public class CreateLibraryWizardResult {

  String groupName;
  String libName;
  File dir;
  boolean changeToDir;
  BuildSystem buildSystem = BuildSystem.GRADLE;

  @Override
  public String toString() {
    return "groupName = " + groupName + ", packageName = " + libName + ", dir = " + dir
        + ", changeToDir = " + changeToDir + ", buildSystem = " + buildSystem;
  }
}
