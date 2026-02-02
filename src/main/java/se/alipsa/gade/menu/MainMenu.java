package se.alipsa.gade.menu;

import javafx.scene.control.MenuBar;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.TextAreaTab;
import se.alipsa.gade.model.MuninConnection;

import java.io.File;

public class MainMenu extends MenuBar {

  private final Gade gui;
  private final FileOperations fileOperations;
  private final ProjectTemplates projectTemplates;
  private final MenuBuilder menuBuilder;

  public MainMenu(Gade gui) {
    this.gui = gui;
    this.fileOperations = new FileOperations(gui, this::restartEngine);
    this.projectTemplates = new ProjectTemplates(gui, this::refreshRuntimesMenu);
    this.menuBuilder = new MenuBuilder(gui, fileOperations, projectTemplates, this::restartEngine);
    getMenus().addAll(menuBuilder.buildMenus());
  }

  public void refreshRuntimesMenu() {
    menuBuilder.refreshRuntimesMenu();
  }

  public void disableInterruptMenuItem() {
    menuBuilder.disableInterruptMenuItem();
  }

  public void enableInterruptMenuItem() {
    menuBuilder.enableInterruptMenuItem();
  }

  public void commentLines() {
    menuBuilder.commentLines();
  }

  public void displayFind() {
    menuBuilder.displayFind();
  }

  public void saveContent(TextAreaTab codeArea) {
    fileOperations.saveContent(codeArea);
  }

  public File promptForFile() {
    return fileOperations.promptForFile();
  }

  public File promptForFile(String fileTypeDescription, String extension, String suggestedName) {
    return fileOperations.promptForFile(fileTypeDescription, extension, suggestedName);
  }

  public MuninConnection configureMuninConnection() {
    return menuBuilder.configureMuninConnection();
  }

  public void restartEngine() {
    gui.getConsoleComponent().restartGroovy();
    gui.getInoutComponent().setPackages(null);
    gui.getEnvironmentComponent().restarted();
  }
}
