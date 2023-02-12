package se.alipsa.gade.code.munin;

import javafx.event.ActionEvent;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.gmdtab.GmdUtil;
import se.alipsa.gade.model.MuninReport;

public class MuninGmdTab extends MuninTab {

  public MuninGmdTab(Gade gui, MuninReport report) {
    super(gui, report);
    getMiscTab().setReportType(ReportType.GMD);
    if (report.getDefinition() != null) {
      replaceContentText(0,0, report.getDefinition());
    }
  }

  @Override
  void viewAction(ActionEvent actionEvent) {
    GmdUtil.viewGmd(gui, getTitle(), getTextContent());
    gui.getConsoleComponent().updateEnvironment();
  }
}
