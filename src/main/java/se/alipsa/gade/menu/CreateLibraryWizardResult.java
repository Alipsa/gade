package se.alipsa.gade.menu;

import java.io.File;

public class CreateLibraryWizardResult {

  String groupName;
  String libName;
  File dir;
  boolean changeToDir;

  @Override
  public String toString() {
    return "groupName = " + groupName + ", packageName = " + libName + ", dir = " + dir + ", changeToDir = " + changeToDir;
  }
}
