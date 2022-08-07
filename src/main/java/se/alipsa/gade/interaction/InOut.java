package se.alipsa.gade.interaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.scene.Node;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import se.alipsa.gade.Gade;
import se.alipsa.gade.chart.Chart;
import se.alipsa.gade.chart.Plot;
import se.alipsa.gade.environment.connections.ConnectionInfo;
import se.alipsa.gade.model.Dependency;
import se.alipsa.gade.model.centralsearch.CentralSearchResult;
import se.alipsa.gade.utils.*;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Page;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static se.alipsa.gade.chart.DataType.isCharacter;
import static se.alipsa.gade.chart.DataType.sqlType;
import static se.alipsa.gade.utils.FileUtils.removeExt;

import javax.swing.*;

public class InOut implements GuiInteraction {

  private static final Logger log = LogManager.getLogger();
  private final Gade gui;
  private final Dialogs dialogs;
  private final ReadImage readImage;
  private final UrlUtil urlUtil;

  public InOut() {
    gui = Gade.instance();
    dialogs = new Dialogs(gui.getStage());
    readImage = new ReadImage();
    urlUtil = new UrlUtil();
  }

  public ConnectionInfo connection(String name) {
    return gui.getEnvironmentComponent().getConnections().stream()
        .filter(ci -> ci.getName().equals(name)).findAny().orElse(null);
  }

  public Connection connect(String name) throws SQLException, ExecutionException, InterruptedException {
    ConnectionInfo ci = gui.getEnvironmentComponent().getDefinedConnections().stream()
        .filter(c -> c.getName().equals(name)).findAny().orElse(null);
    if (ci == null) {
      throw new RuntimeException("Connection " + name + " not found");
    }
    if (ci.getUrl() == null) {
      throw new RuntimeException("Connection url is missing");
    }
    //ci = new ConnectionInfo(ci);
    String url = ci.getUrl().toLowerCase();
    if (StringUtils.isBlank(ci.getPassword()) && !url.contains("passw") && !url.contains("integratedsecurity=true")) {
      String pwd = promptPassword("Password required", "Enter password to " + name + " for " + ci.getUser());
      ci.setPassword(pwd);
    }
    return gui.getEnvironmentComponent().connect(ci);
  }
  
  public Table select(String connectionName, String sqlQuery) throws SQLException, ExecutionException, InterruptedException {
    if (!sqlQuery.trim().toLowerCase().startsWith("select ")) {
      sqlQuery = "select " + sqlQuery;
    }
    try(Connection con = connect(connectionName);
        Statement stm = con.createStatement();
        ResultSet rs = stm.executeQuery(sqlQuery)) {
      return Table.read().db(rs);
    }
  }

  public int update(String connectionName, String sqlQuery) throws SQLException, ExecutionException, InterruptedException {
    try(Connection con = connect(connectionName);
        Statement stm = con.createStatement()) {
      if (sqlQuery.trim().toLowerCase().startsWith("update ")) {
        return stm.executeUpdate(sqlQuery);
      } else {
        return stm.executeUpdate("update " + sqlQuery);
      }
    }
  }

  public int update(String connectionName, String tableName, Row row, String... matchColumnName) throws SQLException, ExecutionException, InterruptedException {
    String sql = createUpdateSql(tableName, row, matchColumnName);
    return update(connectionName, sql);
  }

  @NotNull
  private String createUpdateSql(String tableName, Row row, String[] matchColumnName) {
    String sql = "update " + tableName + " set ";
    List<String> columnNames = new ArrayList<>(row.columnNames());
    columnNames.removeAll(List.of(matchColumnName));
    List<String> setValues = new ArrayList<>();
    columnNames.forEach(n -> {
      setValues.add(n + " = " + quoteIfString(row, n));
    });
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
    ColumnType type = row.getColumnType(columnName);
    if (isCharacter(type)) {
      return "'" + row.getString(columnName) + "'";
    }
    return String.valueOf(row.getObject(columnName));
  }

  public int update(String connectionName, Table table, String... matchColumnName) throws SQLException, ExecutionException, InterruptedException {
    try(Connection con = connect(connectionName);
        Statement stm = con.createStatement()) {
      for (Row row : table) {
        stm.addBatch(createUpdateSql(table.name(), row, matchColumnName));
      }
      int[] results = stm.executeBatch();
      return IntStream.of(results).sum();
    }
  }

  /**
   * create table and insert the table data.
   *
   * @param connectionName the name of the connection defined in the Connections tab
   * @param table the table to copy to the db
   * @param primaryKey name(s) of the primary key columns
   */
  public void create(String connectionName, Table table, String... primaryKey) throws SQLException, ExecutionException, InterruptedException {
    String sql = "create table " + table.name() + "(\n";

    List<String> columns = new ArrayList<>();
    int i = 0;
    List<ColumnType> types = table.types();
    for (String name : table.columnNames()) {
      String column = name + " " + sqlType(types.get(i++));
      columns.add(column);
    }
    sql += String.join(",\n", columns);
    sql += "CONSTRAINT pk_" + table.name() + " PRIMARY KEY (" + String.join(", ", primaryKey) + ")";
    sql += "\n);";

    try(Connection con = connect(connectionName);
        Statement stm = con.createStatement()) {
      log.info("Creating table using DDL: {}", sql);
      stm.execute(sql);
      insert(con, table);
    }
  }

  public int insert(String connectionName, String sqlQuery) throws SQLException, ExecutionException, InterruptedException {
    if (sqlQuery.trim().toLowerCase().startsWith("insert into ")) {
      return update(connectionName, sqlQuery);
    } else {
      return update(connectionName, "insert into " + sqlQuery);
    }
  }

  public int insert(String connectionName, String tableName, Row row) throws SQLException, ExecutionException, InterruptedException {
    String sql = createInsertSql(tableName, row);
    log.info("Executing insert query: {}", sql);
    return insert(connectionName, sql);
  }

  private String createInsertSql(String tableName, Row row) {
    String sql = "insert into " + tableName + " ( ";
    List<String> columnNames = row.columnNames();
    sql += String.join(", ", columnNames);
    sql += " ) values ( ";

    List<String> values = new ArrayList<>();
    columnNames.forEach(n -> {
      values.add(quoteIfString(row, n));
    });
    sql += String.join(", ", values);
    sql += " ); ";
    return sql;
  }

  public int insert(String connectionName, Table table) throws SQLException, ExecutionException, InterruptedException {
    try(Connection con = connect(connectionName)) {
      return insert(con, table);
    }
  }

  public int insert(Connection con, Table table) throws SQLException {
    try(Statement stm = con.createStatement()) {
      for (Row row : table) {
        stm.addBatch(createInsertSql(table.name(), row));
      }
      int[] results = stm.executeBatch();
      return IntStream.of(results).sum();
    }
  }

  public int upsert(String connectionName, Row row, String... primaryKeyName) {
    throw new RuntimeException("Not yet implemented");
  }

  public int upsert(String connectionName, Table table, String... primaryKeyName) {
    throw new RuntimeException("Not yet implemented");
  }

  public int delete(String connectionName, String sqlQuery) throws SQLException, ExecutionException, InterruptedException {
    if (sqlQuery.trim().toLowerCase().startsWith("delete from ")) {
      return update(connectionName, sqlQuery);
    } else {
      return update(connectionName, "delete from " + sqlQuery);
    }
  }

  /**
   *
   * @param connectionName the name of the connection defined in the connection tab
   * @param sql the sql string to execute
   * @return if the sql returns a result set, a Table containing the data is returned, else the number of rows affected is returned
   * @throws SQLException if there is something wrong with the sql
   * @throws ExecutionException if it was not possible to connect
   * @throws InterruptedException if the thread was interrupted during execution
   */
  public Object executeSql(String connectionName, String sql) throws SQLException, ExecutionException, InterruptedException {
    try(Connection con = connect(connectionName);
        Statement stm = con.createStatement()) {
      boolean hasResultSet = stm.execute(sql);
      if (hasResultSet) {
        return Table.read().db(stm.getResultSet());
      } else {
        return stm.getUpdateCount();
      }
    }
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

  public File projectFile(String relativePath) {
    return new File(gui.getInoutComponent().projectDir(), relativePath);
  }

  public void display(Chart chart, String... titleOpt) {
    String title = titleOpt.length > 0 ? titleOpt[0] : removeExt(gui.getCodeComponent().getActiveScriptName());
    display(Plot.jfx(chart), title);
  }

  public void display(Figure figure, String... titleOpt) {
    String title = titleOpt.length > 0 ? titleOpt[0] : removeExt(gui.getCodeComponent().getActiveScriptName());
    Page page = Page.pageBuilder(figure, "target").build();
    String output = page.asJavascript();
    //viewHtml(output, title);
    Platform.runLater(() -> {
      WebView webView = new WebView();
      webView.getEngine().setJavaScriptEnabled(true);
      webView.getEngine().loadContent(output);
      display(webView, title);
    });
  }

  public void display(Node node, String... title) {
    Platform.runLater(() -> {
          var plotsTab = gui.getInoutComponent().getPlotsTab();
          plotsTab.showPlot(node, title);
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
    display(swingNode, title);
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
          Platform.runLater(() -> {
            final WebView browser = new WebView();
            browser.getEngine().load(url.toExternalForm());
            display(browser, title);
          });
          return;
        }
      } catch (IOException e) {
        ExceptionAlert.showAlert("Failed to detect image content type", e);
      }
    }
    Image img = new Image(url.toExternalForm());
    display(img, title);
  }

  public void view(Table table, String... title) {
    String tit = title.length > 0 ? title[0] : table.name();
    if (tit == null) {
      tit = gui.getCodeComponent().getActiveScriptName();
      int extIdx = tit.lastIndexOf('.');
      if (extIdx > 0) {
        tit = tit.substring(0, extIdx);
      }
    }
    gui.getInoutComponent().viewTable(table, tit);
  }

  public void view(String html, String... title) {
    gui.getInoutComponent().viewHtml(html, title);
  }

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

  public String prompt(String title, String headerText, String message, String defaultValue) throws ExecutionException, InterruptedException {
    return dialogs.prompt(title, headerText, message, defaultValue);
  }

  public String prompt(String title, String message) throws ExecutionException, InterruptedException {
    return dialogs.prompt(title, "", message, "");
  }
  public String prompt(String message) throws ExecutionException, InterruptedException {
    return dialogs.prompt("", "", message, "");
  }

  public String promptPassword(String title, String message) throws ExecutionException, InterruptedException {
    return dialogs.promptPassword(title, message);
  }

  public Object promptSelect(String title, String headerText, String message, List<Object> options, Object defaultValue) throws ExecutionException, InterruptedException {
    return dialogs.promptSelect(title, headerText, message, options, defaultValue);
  }

  public LocalDate promptDate(String title, String message, LocalDate defaultValue) throws ExecutionException, InterruptedException {
    return dialogs.promptDate(title, message, defaultValue);
  }

  public YearMonth promptYearMonth(String title, String message, YearMonth from, YearMonth to, YearMonth initial) throws ExecutionException, InterruptedException {
    return dialogs.promptYearMonth(title, message, from, to, initial);
  }

  public YearMonth promptYearMonth(String message) throws ExecutionException, InterruptedException {
    return dialogs.promptYearMonth(message);
  }

  public File chooseFile(String title, String initialDirectory, String description, String... extensions) throws ExecutionException, InterruptedException {
    return dialogs.chooseFile(title, initialDirectory, description, extensions);
  }

  public File chooseDir(String title, String initialDirectory) throws ExecutionException, InterruptedException {
    return dialogs.chooseDir(title, initialDirectory);
  }

  public Image readImage(String filePath) throws IOException {
    return readImage.read(filePath);
  }

  public Image readImage(File file) throws IOException {
    return readImage.read(file.toURI().toURL().toExternalForm());
  }

  public String getContentType(String fileName) throws IOException {
    return readImage.getContentType(fileName);
  }

  public boolean urlExists(String urlString, int timeout) {
    return urlUtil.exists(urlString, timeout);
  }

  @Override
  public String help() {
    return """
        Inout: Providing interaction capabilities between Groovy Code and Gade 
                
        void display(Node node, String... title)
           display an image in the Plot tab
           @param node the Node to display
           @param title an optional title for the component displaying the node
         
        void display(Image img, String... title)
           display an image in the Plot tab     
           @param img the Image to display
           @param title an optional title for the component displaying the image

        void display(String fileName, String... title)
          display an image in the Plot tab
          @param fileName the file name of the image to display
          @param title an optional title for the component displaying the image    
              
        void display(Chart chart, String... titleOpt)
          Show the chart in the plots tab
                
        void display(Figure figure, String... titleOpt)
          Show the figure in the plots tab
          
        void display(JComponent swingPanel, String... titleOpt)
          Show a swing component in the plots tab, useful for swing chart libraries e.g. xchart
                
        void view(Table table, String... title)
          display data in the Viewer tab
          @param table the tablesaw Table to show
          @param title an optional title for the component displaying the table
                
        void view(String html, String... title)
          display html in the Viewer tab        
          @param html a String or similar with the html content to view or a path or url to a file containing the html
          @param title an optional title for the component displaying the html

        Stage getStage()
          Allows Dialogs and similar in external packages to interact with Ride
          @return the primary stage
         
        public Connection connect(String name)
          Connect to a database defined in the Connections tab. 
           
        ConnectionInfo connection(String name)
          Return a connection info (object containing the info) for the name defined in the Connections tab.            
                 
        Table select(String connectionName, String sqlQuery)
          Convenient way to query a database using a connection defined in the Connections tab.
                  
        public int update(String connectionName, String sqlQuery) 
          Convenient way to run an update query using a connection defined in the Connections tab. 
            
        public int insert(String connectionName, String sqlQuery) 
          Convenient way to run an insert query using a connection defined in the Connections tab.  
              
        public int delete(String connectionName, String sqlQuery) 
          Convenient way to run a delete query using a connection defined in the Connections tab.  
          
        File scriptFile()
          return the file from the active tab or null if the active tab has never been saved
                
        File scriptDir()
          return the dir where the current script resides
          or the project dir if the active tab has never been saved
                
        File projectDir()
          return the project dir (the root of the file tree)    
          
        String prompt(String title, String headerText, String message, String defaultValue)
           prompt for text input
           
        String prompt(String message)   
           quick prompt for text input
           
        String promptPassword(String title, String message)
          prompt for a password (text shown as ***)   
           
        Object promptSelect(String title, String headerText, String message, List<Object> options, Object defaultValue)
            prompt user to pick from a list of values
            
        LocalDate promptDate(String title, String message, LocalDate defaultValue)
            prompt user for a date. If outputformat is null, the format will be yyyy-MM-dd
            
        YearMonth promptYearMonth(String title, String message, YearMonth from, YearMonth to, YearMonth initial)
            prompt user for a yearMonth (yyyy-MM)
            
        public YearMonth promptYearMonth(String message)
            quick prompt for a YearMonth
            
        File chooseFile(String title, String initialDirectory, String description, String... extensions)
            asks user to select a file
            Returns the java.io.File selected by the user
            
        File chooseDir(String title, String initialDirectory)
            asks user to select a dir
            Return the java.io.File pointing to the directory chosen    
                
        Image readImage(String filePath)
            create an javafx Image from the url/path specified           
            
        String getContentType(String fileName)
            makes an educated guess of the content type for the filePath specified    
            
        boolean urlExists(String urlString, int timeout)
          attempts to connect to the url specified with a HEAD request to see if it is there or not. 
          
        void help(Class<?> clazz, String... title)
          shows useful info about the class i.e. available methods in the help tab.
                
        void help(Object obj, String... title)
          shows useful info about the object i.e. the object type, available methods and toString content in the hep tab.
          
        void javadoc(String groupId, String artifactId)
          displays javadoc for the latest version fot the artifact in the help tab   
        
        void javadoc(String groupId, String artifactId, String version)
          displays javadoc in the help tab for the version of the artifact specified     
          
        void javadoc(String dependencyString)
          displays javadoc in the help tab for the version of the artifact specified   
          dependencyString is in the format groupId:artifactId:version e.g. "org.knowm.xchart:xchart:3.8.1"  

        void javadoc(Class clazz)
          displays javadoc in the help tab for class specified, looks up the class in maven central search and tries to
          make a somewhat educated guess for which artifact it should be                
        """;
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
      if ( Modifier.isStatic(method.getModifiers())) {
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
        name = name.substring(0, name.length() -2) + "...";
      }
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
   * @param groupId the group id e.g: org.knowm.xchart
   * @param artifactId the artifact id, e.g. xchart
   * @param version the semantic version e.g. 3.8.1
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
   * @param groupId the group id e.g: org.knowm.xchart
   * @param artifactId the artifact id, e.g. xchart
   */
  public void javadoc(String groupId, String artifactId) {
    javadoc(groupId, artifactId, "latest");
  }

  /**
   * Display javadoc in the help tab
   * @param dependencyStringOrFcqn in the format groupId:artifactId:version, e.g. "org.knowm.xchart:xchart:3.8.1"
   *                               OR the fully qualified classname, e.g. "org.apache.commons.io.input.CharSequenceReader"
   */
  public void javadoc(String dependencyStringOrFcqn) throws IOException {
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

  public void javadoc(Class<?> clazz) throws IOException {
    javadocForFcqn(clazz.getPackageName(), clazz.getSimpleName());
  }
  private void javadocForFcqn(String pkgName, String className) throws IOException {
    // https://search.maven.org/solrsearch/select?q=fc:tech.tablesaw.columns.Columnt&rows=20&wt=json
    var url = "https://search.maven.org/solrsearch/select?q=fc:" + pkgName + "." + className + "&rows=20&wt=json";
    var searchResult = new ObjectMapper().readValue(new URL(url), CentralSearchResult.class);
    if (searchResult == null || searchResult.response == null || searchResult.response.docs == null || searchResult.response.docs.isEmpty()) {
      Alerts.warn("Failed to find suitable artifact", "The search result from maven central did not contain anything useful");
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
      try {
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
      } catch (ExecutionException | InterruptedException e) {
        throw new RuntimeException(e);
      }
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


  @Override
  public String toString() {
    return "Interaction capabilities, run io.help(io) for details";
  }
}