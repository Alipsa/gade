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
import se.alipsa.gride.utils.Alerts;
import se.alipsa.gride.utils.ExceptionAlert;
import se.alipsa.gride.utils.FileUtils;
import se.alipsa.gride.utils.TikaUtils;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Page;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import static se.alipsa.gride.utils.FileUtils.removeExt;

public class Inout implements GuiInteraction {

  private static final Logger log = LogManager.getLogger();
  private final Gride gui;
  private final Dialogs dialogs;
  private final ReadImage readImage;
  private final UrlUtil urlUtil;

  public Inout() {
    gui = Gride.instance();
    dialogs = new Dialogs(gui.getStage());
    readImage = new ReadImage();
    urlUtil = new UrlUtil();
  }

  @Override
  public ConnectionInfo connection(String name) {
    return gui.getEnvironmentComponent().getConnections().stream()
        .filter(ci -> ci.getName().equals(name)).findAny().orElse(null);
  }

  /**
   * As this is called from the script engine which runs on a separate thread
   * any gui interaction must be performed in a Platform.runLater (not sure if this qualifies as gui interaction though)
   * TODO: If the error is not printed after extensive testing then remove the catch IllegalStateException block
   *
   * @return the file from the active tab or null if the active tab has never been saved
   */
  @Override
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

  @Override
  public File scriptDir() {
    File file = gui.getCodeComponent().getActiveTab().getFile();
    if (file == null) {
      return projectDir();
    }
    return file.getParentFile();
  }

  @Override
  public File projectDir() {
    return null;
  }

  @Override
  public void display(Chart chart, String... titleOpt) {
    String title = titleOpt.length > 0 ? titleOpt[0] : removeExt(gui.getCodeComponent().getActiveScriptName());
    display(Plot.jfx(chart), title);
  }

  @Override
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


  @Override
  public void display(Node node, String... title) {
    Platform.runLater(() -> {
          var plotsTab = gui.getInoutComponent().getPlotsTab();
          plotsTab.showPlot(node, title);
          SingleSelectionModel<Tab> selectionModel = gui.getInoutComponent().getSelectionModel();
          selectionModel.select(plotsTab);
        }
    );
  }

  @Override
  public void display(Image img, String... title) {
    ImageView node = new ImageView(img);
    display(node, title);
  }

  @Override
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

  @Override
  public void view(Table table, String... title) {
    String tit = title.length > 0 ? title[0] : table.name();
    if (tit == null) {
      tit = gui.getCodeComponent().getActiveScriptName();
      int extIdx = tit.lastIndexOf('.');
      if (extIdx > 0) {
        tit = tit.substring(0, extIdx);
      }
    }
    showInViewer(table, tit);
  }

  @Override
  public void view(String html, String... title) {
    Platform.runLater(() -> {
      viewer.viewHtml(html, title);
      gui.getInoutComponent().getSelectionModel().select(viewer);
    });
  }

  @Override
  public Stage getStage() {
    return gui.getStage();
  }

  @Override
  public String prompt(String title, String headerText, String message, String defaultValue) throws ExecutionException, InterruptedException {
    return dialogs.prompt(title, headerText, message, defaultValue);
  }

  @Override
  public Object promptSelect(String title, String headerText, String message, List<Object> options, Object defaultValue) throws ExecutionException, InterruptedException {
    return dialogs.promptSelect(title, headerText, message, options, defaultValue);
  }

  @Override
  public LocalDate promptDate(String title, String message, LocalDate defaultValue) throws ExecutionException, InterruptedException {
    return dialogs.promptDate(title, message, defaultValue);
  }

  @Override
  public YearMonth promptYearMonth(String title, String message, YearMonth from, YearMonth to, YearMonth initial) throws ExecutionException, InterruptedException {
    return dialogs.promptYearMonth(title, message, from, to, initial);
  }

  @Override
  public File chooseFile(String title, String initialDirectory, String description, String... extensions) throws ExecutionException, InterruptedException {
    return dialogs.chooseFile(title, initialDirectory, description, extensions);
  }

  @Override
  public File chooseDir(String title, String initialDirectory) throws ExecutionException, InterruptedException {
    return dialogs.chooseDir(title, initialDirectory);
  }

  @Override
  public Image readImage(String filePath) throws IOException {
    return readImage.read(filePath);
  }

  @Override
  public URL getResourceUrl(String resource) {
    return readImage.getResourceUrl(resource);
  }

  @Override
  public String getContentType(String fileName) throws IOException {
    return readImage.getContentType(fileName);
  }

  @Override
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
         
        ConnectionInfo connection(String name)
          Return a connections for the name defined in Gride.
                
        File scriptFile()
          return the file from the active tab or null if the active tab has never been saved
                
        File scriptDir()
          return the dir where the current script resides
          or the project dir if the active tab has never been saved
                
        File projectDir()
          return the project dir (the root of the file tree)    
          
        String prompt(String title, String headerText, String message, String defaultValue)
           prompt for text input
           
        Object promptSelect(String title, String headerText, String message, List<Object> options, Object defaultValue)
            prompt user to pick from a list of values
            
        LocalDate promptDate(String title, String message, LocalDate defaultValue)
            prompt user for a date. If outputformat is null, the format will be yyyy-MM-dd
            
        YearMonth promptYearMonth(String title, String message, YearMonth from, YearMonth to, YearMonth initial)
            prompt user for a yearMonth (yyyy-MM)
            
        File chooseFile(String title, String initialDirectory, String description, String... extensions)
            asks user to select a file
            Returns the java.io.File selected by the user
            
        File chooseDir(String title, String initialDirectory)
            asks user to select a dir
            Return the java.io.File pointing to the directory chosen    
        
        Image readImage(String filePath)
            create an javafx Image from the url/path specified
            
        URL getResourceUrl(String resource)
            create an url to the path specified
            
        String getContentType(String fileName)
            makes an educated guess of the content type for the filePath specified    
            
        boolean urlExists(String urlString, int timeout)
          attempts to connect to the url specified with a HEAD request to see if it is there or not.     
        """;
  }

  @Override
  public String toString() {
    return "The Gride InOutComponent, run inout.help() for details";
  }
}
