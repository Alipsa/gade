package se.alipsa.gride.interaction;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gride.Gride;
import se.alipsa.gride.chart.Chart;
import se.alipsa.gride.chart.Plot;
import se.alipsa.gride.environment.connections.ConnectionInfo;
import se.alipsa.gride.utils.*;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import static se.alipsa.gride.utils.FileUtils.removeExt;

public class InOut implements GuiInteraction {

  private static final Logger log = LogManager.getLogger();
  private final Gride gui;
  private final Dialogs dialogs;
  private final ReadImage readImage;
  private final UrlUtil urlUtil;

  public InOut() {
    gui = Gride.instance();
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
  
  public Table query(String connectionName, String sqlQuery) throws SQLException, ExecutionException, InterruptedException {
    try(Connection con = connect(connectionName);
        Statement stm = con.createStatement();
        ResultSet rs = stm.executeQuery(sqlQuery)) {
      return Table.read().db(rs);
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

  public Stage getStage() {
    return gui.getStage();
  }

  public String prompt(String title, String headerText, String message, String defaultValue) throws ExecutionException, InterruptedException {
    return dialogs.prompt(title, headerText, message, defaultValue);
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

  public String getContentType(String fileName) throws IOException {
    return readImage.getContentType(fileName);
  }

  public boolean urlExists(String urlString, int timeout) {
    return urlUtil.exists(urlString, timeout);
  }

  @Override
  public String help() {
    return """
        Inout: Providing interaction capabilities between Groovy Code and Gride 
                
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
                 
        Table query(String connectionName, String sqlQuery)
          Convenient way to query a database using a connection defined in the Connections tab.
                   
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
          
        String help(Class<?> clazz)
          returns a String with useful info about the class i.e. available methods.
        
        String help(Object obj)
          returns a String with useful info about the object i.e. the object type, available methods and toString content.   
        """;
  }

  public String help(Class<?> clazz) {
    return help(clazz, true);
  }

  public String help(Class<?> clazz, boolean includeStatic) {
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

  public String help(Object obj) {
    if (obj == null) {
      return "Object is null, no help available";
    }
    return help(obj.getClass(), false)
        + "\n"
        + StringUtils.maxLengthString(obj.toString(), 300);
  }

  @Override
  public String toString() {
    return "Interaction capabilities, run io.help() for details";
  }
}
