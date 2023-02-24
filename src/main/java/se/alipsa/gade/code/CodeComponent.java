package se.alipsa.gade.code;

import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.gmdtab.GmdTab;
import se.alipsa.gade.code.gradle.GradleTab;
import se.alipsa.gade.code.groovytab.GroovyTab;
import se.alipsa.gade.code.javatab.JavaTab;
import se.alipsa.gade.code.jstab.JsTab;
import se.alipsa.gade.code.mdtab.MdTab;
import se.alipsa.gade.code.munin.MuninTab;
import se.alipsa.gade.code.rtab.RTab;
import se.alipsa.gade.code.sastab.SasTab;
import se.alipsa.gade.code.sqltab.SqlTab;
import se.alipsa.gade.code.txttab.TxtTab;
import se.alipsa.gade.code.xmltab.XmlTab;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.ExceptionAlert;

import java.io.File;
import java.io.IOException;

public class CodeComponent extends BorderPane {

  private final TabPane pane;
  private final Gade gui;

  private static final Logger log = LogManager.getLogger(CodeComponent.class);

  public CodeComponent(Gade gui) {
    this.gui = gui;

    pane = new TabPane();
    setCenter(pane);
    addCodeTab(CodeType.GROOVY);
  }

  public TextAreaTab addCodeTab(CodeType type) {
    //final String untitled = "Untitled";
    TextAreaTab tab = switch (type) {
      case TXT -> new TxtTab(type.getDisplayValue(), gui);
      case JAVA -> new JavaTab(type.getDisplayValue(), gui);
      case XML -> new XmlTab(type.getDisplayValue(), gui);
      case SQL -> new SqlTab(type.getDisplayValue(), gui);
      case GMD -> new GmdTab(type.getDisplayValue(), gui);
      case MD -> new MdTab(type.getDisplayValue(), gui);
      case GROOVY -> new GroovyTab(type.getDisplayValue(), gui);
      case JAVA_SCRIPT -> new JsTab(type.getDisplayValue(), gui);
      case R -> new RTab(type.getDisplayValue(), gui);
      case SAS -> new SasTab(type.getDisplayValue(), gui);
      default -> throw new RuntimeException("Unknown filetype " + type);
    };
    addTabAndActivate(tab);
    return tab;
  }

  public TextAreaTab addTabAndActivate(TextAreaTab codeTab) {
    pane.getTabs().add(codeTab);
    SingleSelectionModel<Tab> selectionModel = pane.getSelectionModel();
    selectionModel.select(codeTab);
    return codeTab;
  }

  /**
   * Get the name (title) of the active tab.
   * Used to name view tabs, files etc.
   */
  public String getActiveScriptName() {
    return getActiveTab().getTitle();

  }

  /**
   * Get the text content from the active tab
   *
   * @return the textual content (String) of the active code tab
   */
  public String getTextFromActiveTab() {
    TabTextArea ta = getActiveTab();
    return ta.getTextContent();
  }

  /**
   * Update connections into in each tab
   * that uses connections (currently only SQL tabs)
   */
  public void updateConnections() {
    for(Tab tab : pane.getTabs()) {
      if (tab instanceof SqlTab) {
        ((SqlTab) tab).updateConnections();
      }
    }
  }

  public TextAreaTab getActiveTab() {
    SingleSelectionModel<Tab> selectionModel = pane.getSelectionModel();
    return (TextAreaTab) selectionModel.getSelectedItem();
  }

  public TextAreaTab addTab(File file, CodeType type) {
    log.trace("Opening {} as type {}", file, type);
    try {
      TextAreaTab tab;
      String title = file.getName();
      tab = switch (type) {
        case MD -> new MdTab(title, gui);
        case GMD -> new GmdTab(title, gui);
        case XML -> new XmlTab(title, gui);
        case JAVA -> new JavaTab(title, gui);
        case SQL -> new SqlTab(title, gui);
        case GRADLE -> new GradleTab(title, gui);
        case GROOVY -> new GroovyTab(title, gui);
        case JAVA_SCRIPT -> new JsTab(title, gui);
        case R -> new RTab(title, gui);
        case SAS -> new SasTab(title, gui);
        //case TXT -> new TxtTab(title, gui);
        default -> new TxtTab(title, gui);
      };
      tab.loadFromFile(file);
      return addTabAndActivate(tab);
    } catch (Throwable e) {
      log.warn("Failed to open {} as type {}", file, type);
      ExceptionAlert.showAlert("Failed to read content of file " + file, e);
    }
    return null;
  }

  public TextAreaTab addTab(MuninTab tab) {
    return addTabAndActivate(tab);
  }

  public void fileSaved(File file) {
    getActiveTab().setTitle(file.getName());
    getActiveTab().setFile(file);
  }

  public boolean hasUnsavedFiles() {
    for (Tab tab : pane.getTabs()) {
      TextAreaTab taTab = (TextAreaTab) tab;
      if (taTab.isChanged()) {
        return true;
      }
    }
    return false;
  }

  public TextAreaTab getTab(File file) {
    for (Tab tab : pane.getTabs()) {
      TextAreaTab textAreaTab = (TextAreaTab) tab;
      if (file.equals(textAreaTab.getFile())) {
        return textAreaTab;
      }
    }
    return null;
  }

  public void activateTab(TextAreaTab tab) {
    pane.getSelectionModel().select(tab);
  }

  /**
   * Remove a connection from all code tabs
   */
  public void removeConnectionFromTabs(String value) {
    for (Tab tab : pane.getTabs()) {
      if (tab instanceof SqlTab) {
        SqlTab sqlTab = (SqlTab) tab;
        sqlTab.removeConnection(value);
      }
    }
  }

  /**
   * Reload a tab from disk
   * @param tab the tab to reload
   */
  public void reloadTabContent(TextAreaTab tab) {
    File file = tab.getFile();
    if (file != null && file.exists() && !tab.isChanged()) {
      log.trace("Reloading from disk");
      tab.reloadFromDisk();
    } else {
      log.info("User tried to reload {} but this is not possible", file);
    }
  }
}
