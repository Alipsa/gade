package se.alipsa.gride.inout;

import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import se.alipsa.gride.chart.Chart;
import se.alipsa.gride.environment.connections.ConnectionInfo;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.components.Figure;

import java.io.File;

public interface InOut {

  /** Return a connections for the name defined in Gride */
  ConnectionInfo connection(String name);

  /**
   * @return the file from the active tab or null if the active tab has never been saved
   */
  File scriptFile();

  /**
   *
   * @return the dir where the current script resides or the project dir if the active tab has never been saved
   */
  File scriptDir();

  /**
   *
   * @return the project dir (the root of the file tree)
   */
  File projectDir();

  void display(Node node, String... title);

  void display(Image img, String... title);
  void display(String fileName, String... title);
  void display(Chart chart, String... titleOpt);
  void display(Figure figure, String... titleOpt);

  void view(Table table, String... title);
  void view(String html, String... title);

  Stage getStage();


}
