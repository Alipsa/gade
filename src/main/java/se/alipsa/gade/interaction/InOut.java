package se.alipsa.gade.interaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.embed.swing.SwingNode;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.knowm.xchart.XChartPanel;
import se.alipsa.gade.Gade;
import se.alipsa.gade.environment.connections.ConnectionHandler;
import se.alipsa.matrix.charts.Chart;
import se.alipsa.matrix.charts.Plot;
import se.alipsa.matrix.core.Row;
import se.alipsa.matrix.sql.MatrixSql;
import se.alipsa.groovy.resolver.*;
import se.alipsa.matrix.core.Matrix;
import se.alipsa.groovy.datautil.ConnectionInfo;
import se.alipsa.gade.model.centralsearch.CentralSearchResult;
import se.alipsa.gade.utils.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static se.alipsa.gade.utils.FileUtils.removeExt;

import javax.imageio.ImageIO;
import javax.swing.*;

public class InOut extends se.alipsa.gi.fx.InOut {

  private static final Logger log = LogManager.getLogger();
  private final Gade gui;
  private final ReadImage readImage;

  public InOut() {
    gui = Gade.instance();
    readImage = new ReadImage();
  }

  public Set<String> dbConnectionNames() {
    return gui.getEnvironmentComponent().getDefinedConnectionsNames();
  }

  /**
   * Create and save a connection in the Environments section, Connections tab
   */
  public void dbCreateConnection(String name, String dependency, String driver, String url, String user, String password) {
    ConnectionInfo ci = new ConnectionInfo(name, dependency, driver, url, user, password);
    if (dbConnection(name) != null) {
      Alerts.warnFx("Cannot create connection", "A connection named " + name + " already exists");
      return;
    }
    gui.getEnvironmentComponent().addConnection(ci);
  }

  public ConnectionInfo dbGetOrAddConnection(String name, String dependency, String driver, String url, String user, String password) {
    ConnectionInfo ci = dbConnection(name);
    if (ci != null) {
      return ci;
    }
    dbCreateConnection(name, dependency, driver, url, user, password);
    return dbConnection(name);
  }

  public ConnectionInfo dbConnection(String name) {
    return gui.getEnvironmentComponent().getDefinedConnections().stream()
        .filter(ci -> ci.getName().equals(name)).findAny().orElse(null);
  }

  public Connection dbConnect(String name) throws SQLException {
    ConnectionInfo ci = gui.getEnvironmentComponent().getDefinedConnections().stream()
        .filter(c -> c.getName().equals(name)).findAny().orElse(null);
    if (ci == null) {
      throw new RuntimeException("Connection " + name + " not found");
    }
    if (ci.getUrl() == null) {
      throw new RuntimeException("Connection url is missing");
    }
    return dbConnect(ci);
  }

  public Connection dbConnect(ConnectionInfo ci) throws SQLException {
    String url = ci.getUrl().toLowerCase();
    if (StringUtils.isBlank(ci.getPassword()) && !url.contains("passw") && !url.contains("integratedsecurity=true")) {
      String pwd = promptPassword("Password required", "Enter password to " + ci.getName() + " for " + ci.getUser());
      ci.setPassword(pwd);
    }
    return new ConnectionHandler(ci).connect();
  }

  @NotNull
  private String dbCreateUpdateSql(String tableName, Row row, String[] matchColumnName) {
    String sql = "update " + tableName + " set ";
    List<String> columnNames = new ArrayList<>(row.columnNames());
    columnNames.removeAll(List.of(matchColumnName));
    List<String> setValues = new ArrayList<>();
    columnNames.forEach(n -> setValues.add(n + " = " + quoteIfString(row, n)));
    sql += String.join(", ", setValues);
    sql += " where ";
    List<String> conditions = new ArrayList<>();
    for (String condition : matchColumnName) {
      conditions.add(condition + " = " + quoteIfString(row, condition));
    }
    sql += String.join(" and ", conditions);
    log.info("Executing update query: {}", sql);
    return sql;
  }

  private String quoteIfString(Row row, String columnName) {
    var value = row.getAt(columnName);
    if (value instanceof CharSequence || value instanceof Character) {
      return "'" + value + "'";
    }
    return String.valueOf(value);
  }

  public Matrix dbSelect(String connectionName, String sqlQuery) throws SQLException {
    if (!sqlQuery.trim().toLowerCase().startsWith("select ")) {
      sqlQuery = "select " + sqlQuery;
    }
    try (Connection con = dbConnect(connectionName);
         Statement stm = con.createStatement();
         ResultSet rs = stm.executeQuery(sqlQuery)) {
      return Matrix.builder().data(rs).build();
    }
  }

  public int dbUpdate(String connectionName, Matrix table, String... matchColumnName) throws SQLException {
    return dbExecuteBatchUpdate(table, dbConnect(connectionName), matchColumnName);
  }

  public int dbUpdate(ConnectionInfo ci, Matrix table, String... matchColumnName) throws SQLException {
    return dbExecuteBatchUpdate(table, dbConnect(ci), matchColumnName);
  }

  private int dbExecuteBatchUpdate(Matrix table, Connection connect, String[] matchColumnName) throws SQLException {
    try (Connection con = connect;
         Statement stm = con.createStatement()) {
      for (Row row : table) {
        stm.addBatch(dbCreateUpdateSql(table.getMatrixName(), row, matchColumnName));
      }
      int[] results = stm.executeBatch();
      return IntStream.of(results).sum();
    }
  }

  public boolean dbTableExists(String connectionName, String tableName) throws SQLException {
    try (Connection con = dbConnect(connectionName)) {
      return dbTableExists(con, tableName);
    }
  }

  public boolean dbTableExists(ConnectionInfo connectioInfo, String tableName) throws SQLException {
    try (Connection con = dbConnect(connectioInfo)) {
      return dbTableExists(con, tableName);
    }
  }

  public boolean dbTableExists(Connection con, String tableName) throws SQLException {
    var rs = con.getMetaData().getTables(null, null, tableName.toUpperCase(), null);
    return rs.next();
  }

  public boolean dbDropTable(String connectionName, String tableName) throws SQLException {
    try (MatrixSql sql = new MatrixSql(dbConnection(connectionName))) {
      if (sql.tableExists(tableName)) {
        var result = (Number) sql.dropTable(tableName);
        return result.intValue() > 0;
      }
      return false;
    }
  }

  public boolean dbDropTable(String connectionName, Matrix table) throws SQLException {
    return dbDropTable(connectionName, MatrixSql.tableName(table));
  }


  public void dbCreate(String connectionName, Matrix table, String... primaryKey) throws SQLException, ExecutionException, InterruptedException {
    dbCreate(dbConnection(connectionName), table, primaryKey);
  }

  public void dbCreate(String connectionName, String tableName, Matrix table, String... primaryKey) throws SQLException, ExecutionException, InterruptedException {
    dbCreate(dbConnection(connectionName), tableName, table, primaryKey);
  }

  /**
   * create table and insert the table data.
   *
   * @param connectionInfo the connection info defined in the Connections tab
   * @param table          the table to copy to the db
   * @param primaryKey     name(s) of the primary key columns
   */
  public void dbCreate(ConnectionInfo connectionInfo, Matrix table, String... primaryKey) throws SQLException {

    try (MatrixSql sql = new MatrixSql(connectionInfo)) {
      String tableName = MatrixSql.tableName(table);
      if (sql.tableExists(tableName)) {
        throw new SQLException("Table '" + tableName + "' already exists, cannot be created");
      }
      sql.create(table, primaryKey);
    }
  }

  public void dbCreate(ConnectionInfo connectionInfo, String tableName, Matrix table, String... primaryKey) throws SQLException {

    try (MatrixSql sql = new MatrixSql(connectionInfo)) {
      if (sql.tableExists(tableName)) {
        throw new SQLException("Table '" + tableName + "' already exists, cannot be created");
      }
      sql.create(tableName, table, primaryKey);
    }
  }

  /**
   * @param connectionName the name of the connection defined in the connection tab
   * @param sql            the sql string to execute
   * @return if the sql returns a result set, a Table containing the data is returned, else the number of rows affected is returned
   * @throws SQLException if there is something wrong with the sql
   */
  public Object dbExecuteSql(String connectionName, String sql) throws SQLException {
    try (Connection con = dbConnect(connectionName);
         Statement stm = con.createStatement()) {
      boolean hasResultSet = stm.execute(sql);
      if (hasResultSet) {
        return Matrix.builder().data(stm.getResultSet()).build();
      } else {
        return stm.getUpdateCount();
      }
    }
  }

  public Object dbExecuteSql(String connectionName, File projectFile) throws IOException,
      SQLException, ExecutionException, InterruptedException {
    String s = FileUtils.readContent(projectFile);
    return dbExecuteSql(connectionName, s);
  }

  public Object dbExecuteSql(String connectionName, String projectFile, @NotNull Map<String, Object> replacements, boolean... logSql) throws IOException,
      SQLException, ExecutionException, InterruptedException {

    String s = FileUtils.readContent(projectFile(projectFile));
    for (var entry : replacements.entrySet()) {
      Object value = entry.getValue();
      String strVal;
      if (value instanceof Collection<?> list) {
        var listValues = new ArrayList<String>();
        for (var val : list) {
          if (val instanceof CharSequence) {
            listValues.add("'" + val + "'");
          } else {
            listValues.add(String.valueOf(val));
          }
        }
        strVal = String.join(", ", listValues);
      } else {
        strVal = String.valueOf(value);
      }
      s = s.replace(entry.getKey(), strVal);
    }
    if (logSql.length > 0 && logSql[0]) {
      log.info("Executing SQL: {}", s);
    }
    return dbExecuteSql(connectionName, s);
  }

  /**
   * As this is called from the script engine which runs on a separate thread
   * any gui interaction must be performed in a Platform.runLater (not sure if this qualifies as gui interaction though)
   * TODO: If the error is not printed after extensive testing then remove the catch IllegalStateException block
   *
   * @return the file from the active tab or null if the active tab has never been saved
   */
  public File scriptFile() {

    try {
      return gui.getCodeComponent().getActiveTab().getFile();
    } catch (IllegalStateException e) {
      log.info("Not on javafx thread", e);
      final FutureTask<File> query = new FutureTask<>(() -> gui.getCodeComponent().getActiveTab().getFile());
      Platform.runLater(query);
      try {
        return query.get();
      } catch (InterruptedException | ExecutionException e1) {
        Platform.runLater(() -> ExceptionAlert.showAlert("Failed to get file from active tab", e1));
        return null;
      }
    }
  }

  public File scriptDir() {
    File file = gui.getCodeComponent().getActiveTab().getFile();
    if (file == null) {
      return projectDir();
    }
    return file.getParentFile();
  }

  public File projectDir() {
    return gui.getInoutComponent().projectDir();
  }

  public File projectDir(String subDir) {
    return new File(gui.getInoutComponent().projectDir(), subDir);
  }

  public File projectFile(String relativePath) {
    return new File(gui.getInoutComponent().projectDir(), relativePath);
  }


  public void display(Chart chart, String... titleOpt) {
    String title = titleOpt.length > 0 ? titleOpt[0] : removeExt(gui.getCodeComponent().getActiveScriptName());
    display(Plot.jfx(chart), false, title);
  }

  public void display(Node node, String... title) {
    display(node, node instanceof javafx.scene.chart.Chart, title);
  }

  public void display(Node node, boolean displayCopy, String... title) {
    final Node displayNode;
    if (displayCopy) {
      displayNode = DeepCopier.deepCopy(node);
    } else {
      displayNode = node;
    }
    Platform.runLater(() -> {
          var plotsTab = gui.getInoutComponent().getPlotsTab();

          plotsTab.showPlot(displayNode, title);
          SingleSelectionModel<Tab> selectionModel = gui.getInoutComponent().getSelectionModel();
          selectionModel.select(plotsTab);
        }
    );
  }

  public void display(Image img, String... title) {
    ImageView node = new ImageView(img);
    display(node, title);
  }

  public void display(JComponent swingComponent, String... title) {
    SwingNode swingNode = new SwingNode();
    swingNode.setContent(swingComponent);
    if (title.length == 0) {
      if (swingComponent instanceof XChartPanel<?> xChartPanel) {
        title = new String[]{xChartPanel.getChart().getTitle()};
      } else if (swingComponent.getClass().getPackageName().startsWith("org.knowm.xchart")) {
        // it comes from the gradle classloader
        try {
          String t = ReflectUtils
              .invoke(swingComponent, "getChart")
              .invoke("getTitle")
              .getResult(String.class);
          title = new String[]{t};
        } catch (Throwable e) {
          log.warn("Failed to invoke getTitle() method", e);
        }
      }
    }
    display(swingNode, title);
  }

  public void display(org.knowm.xchart.internal.chartpart.Chart<?, ?> xchart, String... title) {
    // We must play some tricks here otherwise swing will not be initialized in time
    SwingUtilities.invokeLater(() -> {
      var panel = new XChartPanel<>(xchart);
      Platform.runLater(() -> display(panel, title.length > 0 ? title[0] : xchart.getTitle()));
    });
  }

  public void display(se.alipsa.matrix.xchart.abstractions.MatrixXChart<?> chart, String... title) {
    //display(chart.exportSwing(), title); // Below is cleaner
    display(chart.getXChart(), title);
  }

  public void display(File file, String... title) {
    if (file == null || !file.exists()) {
      Alerts.warnFx("Cannot display image", "Failed to find " + file);
      return;
    }
    if (title.length == 0) {
      display(file.getAbsolutePath(), file.getName());
    } else {
      display(file.getAbsolutePath(), title);
    }
  }

  public void display(String fileName, String... title) {
    URL url = FileUtils.getResourceUrl(fileName);
    log.info("Reading image from " + url);
    if (url == null) {
      Alerts.warn("Cannot display image", "Failed to find " + fileName);
      return;
    }
    File file = new File(fileName);
    if (file.exists()) {
      try {
        String contentType = TikaUtils.instance().detectContentType(file);
        if ("image/svg+xml".equals(contentType)) {
          displaySvg(file, title);
          return;
        }
      } catch (IOException e) {
        ExceptionAlert.showAlert("Failed to detect image content type", e);
      }
    }
    Image img = new Image(url.toExternalForm());
    display(img, title);
  }

  public void displaySvg(File svg, String... title) {
    Platform.runLater(() -> {
      final WebView browser = new WebView();
      try {
        browser.getEngine().load(svg.toURI().toURL().toExternalForm());
      } catch (MalformedURLException e) {
        ExceptionAlert.showAlert("Failed to load svg file", e);
      }
      display(browser, title);
    });
  }

  public void displaySvg(String svg, String... title) {
    Platform.runLater(() -> {
      final WebView browser = new WebView();
      browser.getEngine().loadContent(svg);
      display(browser, title);
    });
  }

  public void view(Integer o, String... title) {
    view(Matrix.builder()
            .rows(List.of(List.of("Update count", o)))
            .build()
        , title.length > 0 ? title : new String[]{"Updated rows"});
  }

  public void view(List<List<?>> matrix, String... title) {
    gui.getInoutComponent().view(matrix, determineTitle(title));
  }

  public void view(Matrix tableMatrix, String... title) {
    String t = title.length > 0 ? title[0] : tableMatrix.getMatrixName();
    if (t == null) t = determineTitle(title);
    gui.getInoutComponent().view(tableMatrix, t);
  }

  private String determineTitle(String... title) {
    String tit = title.length > 0 ? title[0] : null;
    if (tit == null) {
      tit = gui.getCodeComponent().getActiveScriptName();
      int extIdx = tit.lastIndexOf('.');
      if (extIdx > 0) {
        tit = tit.substring(0, extIdx);
      }
    }
    return tit;
  }

  /**
   * For ease of portability between R (Ride)
   * and Groovy (Gade)
   *
   * @param table the tablesaw table to view
   * @param title the title for the view tab (optional)
   */
  public void View(Matrix table, String... title) {
    view(table, title);
  }

  // TODO rename to viewHtml or show
  public void view(String html, String... title) {
    gui.getInoutComponent().viewHtml(html, title);
  }

  public void viewMarkdown(String markdown, String... title) {
    gui.getInoutComponent().viewMarkdown(markdown, title);
  }

  // TODO rename to viewHtml or show
  public void view(File file, String... title) {
    if (file == null) {
      gui.getConsoleComponent().addWarning("view file", "File argument cannot be null", true);
      return;
    }
    gui.getInoutComponent().viewHtml(file.getAbsolutePath(), title);
  }

  public Stage getStage() {
    return gui.getStage();
  }

  public Image readImage(String filePath) throws IOException {
    return readImage.read(filePath);
  }

  public Image readImage(File file) throws IOException {
    return readImage.read(file);
  }

  public void save(se.alipsa.matrix.charts.Chart chart, File file) {
    double width = gui.getInoutComponent().getPlotsTab().getTabPane().getWidth();
    double height = gui.getInoutComponent().getPlotsTab().getTabPane().getHeight() - 20.0;
    save(chart, file, width, height, true);
  }

  public void save(se.alipsa.matrix.charts.Chart chart, File file, double width, double height) {
    save(chart, file, width, height, true);
  }

  /**
   * Save a chart to a png file
   *
   * @param chart        the chart to save
   * @param file         the file to save to
   * @param width        the intended width of the png image
   * @param height       the intended height of the png image
   * @param useGadeStyle style the chart with the same style as the current Gade style
   */
  public void save(se.alipsa.matrix.charts.Chart chart, File file, double width, double height, boolean useGadeStyle) {
    save(se.alipsa.matrix.charts.Plot.jfx(chart), file, width, height, useGadeStyle, false);
  }

  public void save(Region region, File file) {
    save(region, file, region.getWidth(), region.getHeight(), true);
  }

  public void save(Region region, File file, boolean useGadeStyle) {
    save(region, file, region.getWidth(), region.getHeight(), useGadeStyle);
  }

  public void save(Region region, File file, double width, double height) {
    save(region, file, width, height, true);
  }

  public void save(Parent region, File file, double width, double height, boolean useGadeStyle) {
    save(region, file, width, height, useGadeStyle, true);
  }

  public void save(Parent region, File file, double width, double height, boolean useGadeStyle, boolean displayCopy) {
    if (region == null) {
      Alerts.warnFx("Cannot save", "the javafx region to save was null, nothing to do!");
      return;
    }
    final Parent reg;
    try {
      // TODO putting a region in a Scene changes the region: make a deep clone of the region first
      if (displayCopy) {
        reg = DeepCopier.deepCopy(region);
      } else {
        reg = region;
      }

      log.info("saving region to " + file);
      List<WritableImage> img = new ArrayList<>();

      Scene scene = reg.getScene();
      final Scene finalScene;
      if (scene != null) {
        log.warn("This region is already bound to a screen, ignoring width and height parameters");
        finalScene = null;
      } else {
        log.info("Create scene");
        finalScene = new Scene(reg, width, height);
      }
      // Use countdown latch to make the method synchronous i.e. return once the file is saved
      CountDownLatch countDownLatch = new CountDownLatch(1);
      Platform.runLater(() -> {
        WritableImage snapshot;
        if (useGadeStyle) {
          log.info("Setting styles");
          if (finalScene != null) {
            finalScene.getStylesheets().addAll(gui.getStyleSheets());
          } else {
            reg.getStylesheets().addAll(gui.getStyleSheets());
          }
        }
        if (finalScene != null) {
          log.info("Getting a snapshot of the scene");
          snapshot = finalScene.snapshot(null);
        } else {
          log.info("Getting a snapshot of the region");
          snapshot = reg.snapshot(new SnapshotParameters(), null);
        }
        img.add(snapshot);
        countDownLatch.countDown();
      });
      try {
        countDownLatch.await();
        log.info("Writing the png file");
        ImageIO.write(SwingFXUtils.fromFXImage(img.get(0), null), "png", file);

        log.info("File write finished");
      } catch (InterruptedException e) {
        Platform.runLater(() -> ExceptionAlert.showAlert("Save was interrupted", e));
      } catch (IOException e) {
        log.warn("Failed to write to png file " + file, e);
        Platform.runLater(() -> ExceptionAlert.showAlert("Failed to save chart to file", e));
      }
    } catch (RuntimeException e) {
      Platform.runLater(() -> ExceptionAlert.showAlert("A runtime exception occurred when trying to save", e));
    }
  }

  public String help() {
    return "Inout: Providing interaction capabilities between Groovy Code and Gade\n" + helpText(InOut.class, false);
  }

  public void help(Class<?> clazz, String... title) {
    gui.getInoutComponent().viewHelp(title.length > 0 ? title[0] : clazz.getSimpleName(), helpText(clazz));
  }

  public void help(Object obj, String... title) {
    if (obj == null) {
      Alerts.warnFx("Cannot help", "Object is null, no help available");
      return;
    }
    gui.getInoutComponent().viewHelp(
        title.length > 0 ? title[0] : obj.getClass().getSimpleName(),
        helpText(obj)
    );
  }

  public String helpText(Class<?> clazz) {
    return helpText(clazz, true);
  }

  public String helpText(Class<?> clazz, boolean includeStatic) {
    StringBuilder sb = new StringBuilder(StringUtils.underLine(clazz.getName(), '-'));
    Method[] methods = clazz.getMethods();
    if (includeStatic) {
      Arrays.stream(methods)
          .filter(method -> Modifier.isStatic(method.getModifiers()))
          .forEach(method -> sb.append("static ").append(methodDescription(method)).append("\n"));
    }

    Arrays.sort(methods, Comparator.comparing(Method::getName));
    for (var method : methods) {
      if (Object.class.equals(method.getDeclaringClass())) {
        continue;
      }
      if (Modifier.isStatic(method.getModifiers())) {
        continue;
      }
      sb.append(methodDescription(method));
      sb.append("\n");
    }
    return sb.toString();
  }

  private CharSequence methodDescription(Method method) {
    StringBuilder sb = new StringBuilder();
    sb.append(method.getReturnType().getSimpleName()).append(" ");
    sb.append(method.getName()).append("(");
    Parameter[] params = method.getParameters();
    List<String> paramList = new ArrayList<>();
    for (Parameter param : params) {
      String name = param.getType().getSimpleName();
      if (param.isVarArgs()) {
        name = name.substring(0, name.length() - 2) + "...";
      }
      name += " " + param.getName();
      paramList.add(name);
    }
    sb.append(String.join(", ", paramList)).append(")");

    if (method.getExceptionTypes().length > 0) {
      sb.append(" throws ");
      List<String> exceptions = new ArrayList<>();
      for (Class<?> exc : method.getExceptionTypes()) {
        exceptions.add(exc.getSimpleName());
      }
      sb.append(String.join(", ", exceptions));
    }
    return sb;
  }

  public String helpText(Object obj) {
    if (obj == null) {
      return "Object is null, no help available";
    }
    return helpText(obj.getClass(), false)
        + "\n"
        + StringUtils.maxLengthString(obj.toString(), 300);
  }

  /**
   * Display javadoc in the help tab
   *
   * @param groupId    the group id e.g: org.knowm.xchart
   * @param artifactId the artifact id, e.g. xchart
   * @param version    the semantic version e.g. 3.8.1
   */
  public void javadoc(String groupId, String artifactId, String version) {
    String url = "https://javadoc.io/doc/" + groupId + "/" + artifactId + "/" + version;
    gui.getInoutComponent().viewHtml(
        url, artifactId
    );
  }

  /**
   * Display javadoc in the help tab
   *
   * @param groupId    the group id e.g: org.knowm.xchart
   * @param artifactId the artifact id, e.g. xchart
   */
  public void javadoc(String groupId, String artifactId) {
    javadoc(groupId, artifactId, "latest");
  }

  /**
   * Display javadoc in the help tab
   *
   * @param dependencyStringOrFcqn in the format groupId:artifactId:version, e.g. "org.knowm.xchart:xchart:3.8.1"
   *                               OR the fully qualified classname, e.g. "org.apache.commons.io.input.CharSequenceReader"
   */
  public void javadoc(String dependencyStringOrFcqn) throws IOException, URISyntaxException {
    if (dependencyStringOrFcqn == null) {
      return;
    }
    if (dependencyStringOrFcqn.contains(":")) {
      var dep = new Dependency(dependencyStringOrFcqn);
      javadoc(dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
    } else {
      javadocForFcqn(dependencyStringOrFcqn.substring(0, dependencyStringOrFcqn.lastIndexOf('.')),
          dependencyStringOrFcqn.substring(dependencyStringOrFcqn.lastIndexOf('.') + 1));
    }
  }

  public void javadoc(Object obj) throws IOException, URISyntaxException {
    javadoc(obj.getClass());
  }

  public void javadoc(Class<?> clazz) throws IOException, URISyntaxException {
    javadocForFcqn(clazz.getPackageName(), clazz.getSimpleName());
  }

  private void javadocForFcqn(String pkgName, String className) throws IOException, URISyntaxException {
    // https://search.maven.org/solrsearch/select?q=fc:tech.tablesaw.columns.Columnt&rows=20&wt=json
    var url = "https://search.maven.org/solrsearch/select?q=fc:" + pkgName + "." + className + "&rows=20&wt=json";
    var searchResult = new ObjectMapper().readValue(new URI(url).toURL(), CentralSearchResult.class);
    if (searchResult == null || searchResult.response == null || searchResult.response.docs == null || searchResult.response.docs.isEmpty()) {
      Alerts.warnFx("Failed to find suitable artifact", "The search result from maven central did not contain anything useful");
      return;
    }
    var doc = searchResult.response.docs.stream()
        .filter(d -> pkgName.startsWith(d.g))
        .findFirst()
        .orElse(null);

    String groupId;
    String artifactId;
    if (doc == null) {
      List<Object> groupIds = searchResult.response.docs.stream().map(d -> d.g + ":" + d.a)
          .distinct()
          .collect(Collectors.toList());

      var grpArt = promptSelect("Class " + className + " exists in many artifacts",
          "No obvious match found for " + className + ",\nselect the groupId:artifact you want to view",
          "groupId:artifact",
          groupIds,
          groupIds.get(0));
      if (grpArt == null || String.valueOf(grpArt).isBlank()) {
        return;
      }
      var ga = String.valueOf(grpArt).split(":");
      groupId = ga[0];
      artifactId = ga[1];
    } else {
      groupId = doc.g;
      artifactId = doc.a;
    }

    //https://javadoc.io/doc/tech.tablesaw/tablesaw-core/latest/tech/tablesaw/columns/Column.html

    String packageName = pkgName.replaceAll("\\.", "/");
    String jdocUrl = "https://javadoc.io/doc/" + groupId + "/" + artifactId + "/latest/"
        + packageName + "/" + className + ".html";
    log.info("Showing {} in help tab", jdocUrl);
    gui.getInoutComponent().viewHtml(jdocUrl, artifactId);
  }

  /**
   * This is an alternative to @Grab
   *
   * @param dependency the gradle short version string corresponding to the dependency
   */
  public void addDependency(String dependency) {
    DependencyResolver resolver = new DependencyResolver(gui.getConsoleComponent().getClassLoader());
    try {
      resolver.addDependency(dependency);
    } catch (ResolvingException e) {
      Platform.runLater(() -> ExceptionAlert.showAlert("Failed to add dependency " + dependency, e));
    }
  }

  public void addToClassPath(File dirOrJar) {
    try {
      gui.getConsoleComponent().getClassLoader().addURL(dirOrJar.toURI().toURL());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Dynamically create an input dialog with an arbitrary number of input parts e.g:
   * <code><pre>
   * Map creds = io.prompt( 'create user', [
   *   'username': [TextField, 'username'],
   *   'password': [PasswordField, 'password'],
   *   'pref': [ComboBox, 'drink preference', 'coffee', 'tea', 'beer', 'water']
   * ])
   * </pre></code>
   * The resulting map will contain values for each key e.g.
   * <code><pre>
   *   {password=123poi, pref=tea, username=pern}
   * </pre></code>
   * assuming the pref selected was tea and the username was pern
   * <code><pre>
   *  assert creds.username == 'pern'
   *  assert creds.pref == 'tea'
   * </pre></code>
   * @param title the dialog title
   * @param params a Map&lt;String, List&lt;Object&gt;&gt; the key is the name of the control, the first item in the list is the
   *               Node type (TextField, PasswordField, Combobox), the second list param is the Label text for the control
   *               subsequent elements are values for the Combobox.
   *
   * @return a Map of the keys supplied, and the user input value
   */
  Map<String, Object> prompt(String title, Map<String, List<Object>> params) {
    FutureTask<Map<String, Object>> task = new FutureTask<>(() -> {
      Dialog<Map<String, Object>> dialog = createDialog(title);
      Map<String, Node> elements = new HashMap<>();
      VBox vbox = (VBox) dialog.getDialogPane().getContent();
      params.forEach((paramName, args) -> {
            Class<?> type = (Class<?>) args.getFirst();
            Label label = new Label((String) args.get(1));
            HBox hBox = new HBox();
            hBox.setSpacing(5.0);
            hBox.setPadding(new Insets(5));
            hBox.getChildren().add(label);
            if (type == TextField.class) {
              TextField tf = new TextField();
              elements.put(paramName, tf);
              hBox.getChildren().add(tf);
            } else if (type == PasswordField.class) {
              PasswordField pf = new PasswordField();
              elements.put(paramName, pf);
              hBox.getChildren().add(pf);
            } else if (type == ComboBox.class) {
              List<Object> values = args.subList(2, args.size());
              ComboBox<Object> select = new ComboBox<>();
              select.getItems().addAll(values);
              elements.put(paramName, select);
              hBox.getChildren().add(select);
            } else {
              throw new IllegalArgumentException("There is no implementation for " + type + ", supported types are TextField, PasswordField, Combobox");
            }
            vbox.getChildren().add(hBox);
          }
      );
      dialog.setResultConverter(buttonType -> {
        Map<String, Object> map = new HashMap<>();
        if (buttonType == ButtonType.OK) {
          elements.forEach((k, v) -> {
            if (v instanceof TextField tf) {
              map.put(k, tf.getText());
            } else if (v instanceof ComboBox<?> cb) {
              map.put(k, cb.getValue());
            }
          });
        }
        return map;
      });
      return dialog.showAndWait().orElse(new HashMap<>());
    });
    Platform.runLater(task);
    try {
      return task.get();
    } catch (InterruptedException | ExecutionException | IllegalArgumentException e) {
      ExceptionAlert.showAlert("Failed to create prompt dialog", e);
      return null;
    }
  }

  private Dialog<Map<String, Object>> createDialog(String title) {
    Dialog<Map<String, Object>> dialog = new Dialog<>();
    dialog.setTitle(title);
    VBox content = new VBox();
    content.setPadding(new Insets(5));
    dialog.getDialogPane().setContent(content);
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    dialog.setResizable(true);
    dialog.getDialogPane().getScene().getWindow().sizeToScene();
    //if (styleSheetUrls != null) {
    //  dialog.getDialogPane().getStylesheets().addAll(styleSheetUrls)
    //}
    return dialog;
  }

  @Override
  public String toString() {
    return "Interaction capabilities, run io.help(io) for details";
  }
}
