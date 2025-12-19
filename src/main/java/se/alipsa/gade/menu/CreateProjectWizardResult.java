package se.alipsa.gade.menu;

import java.io.File;

public class CreateProjectWizardResult {

  String groupName;
  String projectName;
  File dir;
  boolean changeToDir;
  BuildSystem buildSystem = BuildSystem.GRADLE;

  @Override
  public String toString() {
    return "groupName = " + groupName + ", packageName = " + projectName + ", dir = " + dir
        + ", changeToDir = " + changeToDir + ", buildSystem = " + buildSystem;
  }
}
