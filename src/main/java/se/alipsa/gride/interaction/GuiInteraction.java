package se.alipsa.gride.interaction;

import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import se.alipsa.gride.chart.Chart;
import se.alipsa.gride.environment.connections.ConnectionInfo;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.components.Figure;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.ExecutionException;

public interface GuiInteraction {


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

  String prompt(String title, String headerText, String message, String defaultValue) throws ExecutionException, InterruptedException;

  Object promptSelect(String title, String headerText, String message, List<Object> options, Object defaultValue) throws ExecutionException, InterruptedException;

  LocalDate promptDate(String title, String message, LocalDate defaultValue) throws ExecutionException, InterruptedException;

  YearMonth promptYearMonth(String title, String message, YearMonth from, YearMonth to, YearMonth initial) throws ExecutionException, InterruptedException;

  File chooseFile(String title, String initialDirectory, String description, String... extensions) throws ExecutionException, InterruptedException;

  File chooseDir(String title, String initialDirectory) throws ExecutionException, InterruptedException;

  Image readImage(String filePath) throws IOException;

  URL getResourceUrl(String resource);

  String getContentType(String fileName) throws IOException;

  boolean urlExists(String urlString, int timeout);

  String help();
}
