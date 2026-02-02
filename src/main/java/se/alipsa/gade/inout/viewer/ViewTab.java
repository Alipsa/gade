package se.alipsa.gade.inout.viewer;

import static se.alipsa.gade.Constants.KEY_CODE_COPY;
import static se.alipsa.gade.inout.viewer.ViewHelper.createContextMenu;

import java.util.StringJoiner;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.text.Text;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import netscape.javascript.JSObject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Constants;
import se.alipsa.gade.Gade;
import se.alipsa.gmd.core.HtmlDecorator;
import se.alipsa.matrix.core.Grid;
import se.alipsa.matrix.core.Matrix;
import se.alipsa.gade.utils.*;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ViewTab extends Tab {

  private static final Logger log = LogManager.getLogger(ViewTab.class);
  private static final List<String> NUMERIC_TYPES = List.of(
      "NUMBER", "BYTE", "SHORT", "INTEGER", "INT", "LONG", "BIGINTEGER",
      "FLOAT", "DOUBLE", "BIGDECIMAL"
  );

  // Keep a static reference to avoid aggressive cleanup by GC
  private static WebView browser;

  private final TabPane viewPane;

  public ViewTab() {
    setText("Viewer");
    viewPane = new TabPane();
    setContent(viewPane);
    viewPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
  }

  /*
  public void viewTable(Table table, String... title) {
    List<String> types = new ArrayList<>();
    table.types().forEach(t -> types.add(t.name()));
    viewTable(table.columnNames(), TableUtil.toRowList(table), types, title);
  }*/

  public void viewTable(Matrix tableMatrix, String... title) {
    viewTable(tableMatrix.columnNames(), tableMatrix.rowList(), tableMatrix.typeNames(), title);
  }

  public void viewTable(Grid grid, String... title) {
    // assume uniform format
    String type = "STRING";
    if (grid.getAt(0,0) instanceof Number) {
      type = "NUMBER";
    }
    List<String> typeList = new ArrayList<>();
    List<String> headerList = new ArrayList<>();
    for (int i = 0; i < grid.getAt(0).size(); i++) {
      typeList.add(type);
      headerList.add("c" + i+1);
    }
    viewTable(headerList, grid.getRowList(), typeList, title);
  }

  public void viewTable(List<String> headerList, List<List<Object>> rowList, List<String> columnTypes, String... title) {
    try {

      NumberFormat numberFormatter = NumberFormat.getInstance();
      numberFormatter.setGroupingUsed(false);

      TableView<List<String>> tableView = new TableView<>();
      tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
      tableView.getSelectionModel().setCellSelectionEnabled(true);
      tableView.setOnMouseClicked(click -> {
        if (click.getClickCount() == 2) {
          copySelectionToClipboard(tableView, headerList);
          Gade.instance().getConsoleComponent().addOutput("copied selection to clipboard", "", true, false);
        }
      });
      tableView.setOnKeyPressed(event -> {
        if (KEY_CODE_COPY.match(event)) {
          copySelectionToClipboard(tableView, headerList);
        }
      });

      tableView.setRowFactory(tv -> {
        final TableRow<List<String>> row = new TableRow<>();
        final ContextMenu contextMenu = new ContextMenu();
        final MenuItem copyCellsItem = new MenuItem("copy selection");
        copyCellsItem.setOnAction(event -> copySelectionToClipboard(tv, headerList));
        final MenuItem copyMenuItem = new MenuItem("copy row(s)");
        copyMenuItem.setOnAction(event -> copyRowSelectionToClipboard(tv, null));
        final MenuItem copyWithHeaderMenuItem = new MenuItem("copy row(s) with header");
        copyWithHeaderMenuItem.setOnAction(event -> copyRowSelectionToClipboard(tv, headerList));
        final MenuItem exportToCsvMenuItem = new MenuItem("export row(s) to csv");
        exportToCsvMenuItem.setOnAction(event -> exportRowsToCsv(tv, headerList, title));

        contextMenu.getItems().addAll(copyCellsItem, copyMenuItem, copyWithHeaderMenuItem, exportToCsvMenuItem);
        row.contextMenuProperty().bind(
            Bindings.when(row.emptyProperty())
                .then((ContextMenu) null)
                .otherwise(contextMenu)
        );
        return row;
      });

      for (int i = 0; i < headerList.size(); i++) {
        final int j = i;
        String colName = String.valueOf(headerList.get(i));
        TableColumn<List<String>, String> col = new TableColumn<>();
        if (shouldRightAlign(columnTypes.get(i))) {
          col.setStyle("-fx-alignment: CENTER-RIGHT;");
        }
        Label colLabel = new Label(colName);
        colLabel.setTooltip(new Tooltip(columnTypes.get(i)));
        col.setGraphic(colLabel);
        col.setPrefWidth(new Text(colName).getLayoutBounds().getWidth() * 1.25 + 12.0);

        tableView.getColumns().add(col);
        col.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get(j)));
      }
      ObservableList<List<String>> data = FXCollections.observableArrayList();
      for (List<?> row : rowList) {
        List<String> obsRow = new ArrayList<>();
        for (Object obj : row) {
          if (obj instanceof Number) {
            obsRow.add(numberFormatter.format(obj));
          } else {
            obsRow.add(String.valueOf(obj));
          }
        }
        data.add(obsRow);
      }
      tableView.setItems(data);
      Tab tab = new Tab();
      String tabTitle = " (" + rowList.size() + " rows)";
      if (title.length > 0) {
        tabTitle = title[0] + tabTitle;
      }
      tab.setText(tabTitle);
      tab.setContent(tableView);
      viewPane.getTabs().add(tab);
      SingleSelectionModel<Tab> selectionModel = viewPane.getSelectionModel();
      selectionModel.select(tab);
    } catch (RuntimeException e) {
      ExceptionAlert.showAlert("Failed to view table", e);
    }
  }

  private boolean shouldRightAlign(String type) {
    if (type == null) return false;
    boolean isNumeric = false;
    // All tablesaw numbers columns are NUMBER, we already cover that in NUMERIC_TYPES
    if (NUMERIC_TYPES.contains(type.toUpperCase())) {
      isNumeric = true;
    }
    return isNumeric;
  }

  private void copySelectionToClipboard(final TableView<?> table, List<String> headerList) {
    int rowCount = table.getItems().size();
    int columnCount = table.getColumns().size();
    int totalCells = rowCount * columnCount;

    // If everything is selected, defer to the more suitable way for pasting to a spreadsheet
    if (table.getSelectionModel().getSelectedCells().size() == totalCells) {
      copyRowSelectionToClipboard(table, headerList);
      return;
    }

    StringBuilder strb = new StringBuilder();
    int previousRow = -1;
    StringJoiner joiner = new StringJoiner("\t");

    for (TablePosition<?, ?> tablePosition : table.getSelectionModel().getSelectedCells()) {
      int currentRow = tablePosition.getRow();
      if (currentRow != previousRow && previousRow != -1) {
        strb.append(joiner).append('\n');
        joiner = new StringJoiner("\t");
      }

      String value = (String) tablePosition.getTableColumn().getCellData(currentRow);
      joiner.add(value == null ? "" : value);
      previousRow = currentRow;
    }

    strb.append(joiner);

    ClipboardContent clipboardContent = new ClipboardContent();
    clipboardContent.putString(strb.toString());
    Clipboard.getSystemClipboard().setContent(clipboardContent);
  }

  @SuppressWarnings("rawtypes")
  private void copyRowSelectionToClipboard(final TableView<?> table, List<String> headerList) {
    final Set<Integer> rows = new TreeSet<>();
    for (final TablePosition tablePosition : table.getSelectionModel().getSelectedCells()) {
      rows.add(tablePosition.getRow());
    }
    final StringBuilder strb = new StringBuilder();
    if (headerList != null) {
      strb.append(String.join("\t", headerList)).append("\n");
    }
    boolean firstRow = true;
    for (final Integer row : rows) {
      if (!firstRow) {
        strb.append('\n');
      }
      firstRow = false;
      boolean firstCol = true;
      for (final TableColumn<?, ?> column : table.getColumns()) {
        if (!firstCol) {
          strb.append('\t');
        }
        firstCol = false;
        final Object cellData = column.getCellData(row);
        String cellValue = cellData == null ? "" : cellData.toString();

        // Check for line breaks (or tabs, just in case) and enclose in double quotes
        if (cellValue.contains("\t") || cellValue.contains("\"") || cellValue.matches("(?s).*\\s.*")){
          // If the cell value itself contains double quotes, they must be escaped by doubling them
          cellValue = cellValue.trim().replace("\"", "\"\"");
          // Enclose the modified cell value in double quotes
          strb.append('"').append(cellValue).append('"');
        } else {
          // Otherwise, append the cell value directly
          strb.append(cellValue);
        }
      }
    }
    final ClipboardContent clipboardContent = new ClipboardContent();
    clipboardContent.putString(strb.toString());
    Clipboard.getSystemClipboard().setContent(clipboardContent);
  }

  private void exportRowsToCsv(final TableView<?> table, List<String> headerList, String... title) {
    final Set<Integer> rows = new TreeSet<>();
    for (final TablePosition<?, ?> tablePosition : table.getSelectionModel().getSelectedCells()) {
      rows.add(tablePosition.getRow());
    }
    try {
      StringWriter sw = new StringWriter();
      CSVFormat format = CSVFormat.DEFAULT.builder()
          .setHeader(headerList.toArray(new String[0]))
          .get();
      CSVPrinter prn = new CSVPrinter(sw, format);
      List<String> rowValues = new ArrayList<>(headerList.size());
      for (final Integer row : rows) {
        for (final TableColumn<?, ?> column : table.getColumns()) {
          final Object cellData = column.getCellData(row);
          rowValues.add(cellData == null ? null : String.valueOf(cellData).trim());
        }
        prn.printRecord(rowValues);
        rowValues.clear();
      }
      FileChooser fc = new FileChooser();
      fc.setTitle("Save CSV File");
      String initialFileName = (title.length == 0 ? "gadeExport" : title[0])
          .replace("*", "").replace(" ", "");
      if (initialFileName.endsWith(".")) {
        initialFileName = initialFileName + "csv";
      } else {
        initialFileName = initialFileName + ".csv";
      }
      Gade gui = Gade.instance();
      String dir = gui.getPrefs().get(Constants.PREF_LAST_EXPORT_DIR,gui.getInoutComponent().projectDir().getAbsolutePath());
      fc.setInitialDirectory(new File(dir));
      fc.setInitialFileName(initialFileName);
      fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("CSV", "*.csv"));
      File outFile = fc.showSaveDialog(gui.getStage());
      if (outFile == null ) {
        // v1.1 UX IMPROVEMENT: Add explicit "Copy to Clipboard" button instead of auto-copying on cancel.
        // Current behavior: Cancelling file save automatically copies CSV to clipboard as fallback.
        final ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(sw.toString());
        Clipboard.getSystemClipboard().setContent(clipboardContent);
        Alerts.info("Export to CSV", "File export cancelled, CSV copied to clipboard!");
      } else {
        FileUtils.writeToFile(outFile, sw.toString());
        gui.getPrefs().put(Constants.PREF_LAST_EXPORT_DIR, outFile.getCanonicalFile().getParentFile().getAbsolutePath());
      }
    } catch (IOException e) {
      ExceptionAlert.showAlert("Failed to create csv", e);
    }
  }

  public void viewHtml(String url, String... title) {
    if (url == null) {
      log.warn("url is null, nothing to view");
      return;
    }
    url = url.trim();
    Tab tab = new Tab();
    if (title.length > 0) {
      tab.setText(title[0]);
    } else {
      tab.setText(FileUtils.baseName(url));
    }
    tab.setTooltip(new Tooltip(url));
    viewPane.getTabs().add(tab);
    browser = new WebView();
    browser.setContextMenuEnabled(false);
    tab.setContent(browser);

    WebEngine webEngine = browser.getEngine();
    webEngine.setJavaScriptEnabled(true);
    webEngine.setOnError(eh -> log.warn(eh.getMessage(), eh.getException()));

    AtomicBoolean loadUrl = new AtomicBoolean(false);
    AtomicReference<String> pathUrl = new AtomicReference<>(url);

    webEngine.getLoadWorker().stateProperty().addListener( (observable, oldValue, newValue) -> {
      //System.out.println(newValue);
      if (newValue == Worker.State.RUNNING) {
        var window = (JSObject) webEngine.executeScript("window");
        window.setMember("java", new JsBridge());
        webEngine.executeScript("console.log = function(message) { java.log(message); }");
        webEngine.executeScript("window.onerror = function(msg, url, line, col, error) { return java.onError(msg, url, line, col, error);};");
      } else if (newValue == Worker.State.SUCCEEDED) {
        createContextMenu(browser, pathUrl.get(), loadUrl.get());
        //document finished loading
        viewPane.getSelectionModel().select(tab);
      } else if (newValue == Worker.State.FAILED) {
        log.warn("Failed to load content in view tab");
      }
    });
    if (url.startsWith("http") || url.startsWith("file:")) {
      log.info("Opening {} in view tab", url);
      loadUrl.set(true);
      webEngine.load(url);
    } else {
      try {
        if (Paths.get(url).toFile().exists()) {
          String path = Paths.get(url).toUri().toURL().toExternalForm();
          log.info("Opening {} in view tab", path);
          pathUrl.set(path);
          loadUrl.set(true);
          webEngine.load(path);
        } else {
          log.info("url is not a http url nor a local path, assuming it is content...");
          webEngine.loadContent(url);
        }
      } catch (MalformedURLException | InvalidPathException e) {
        log.info("{}, url is not a http url nor a local path, assuming it is content...", e.toString());
        webEngine.loadContent(url);
      }
    }
  }

  public void viewHtmlWithBootstrap(String content, String... title) {
    Tab tab = new Tab();
    if (title.length > 0) {
      tab.setText(title[0]);
    }
    viewPane.getTabs().add(tab);
    browser = new WebView();
    browser.setContextMenuEnabled(false);

    WebEngine webEngine = browser.getEngine();
    webEngine.setOnError(eh -> log.warn(eh.getMessage(), eh.getException()));
    String html = HtmlDecorator.decorate(content, true);
    webEngine.setJavaScriptEnabled(true);
    webEngine.getLoadWorker().stateProperty().addListener( (observable, oldValue, newValue) -> {
      //System.out.println(newValue);
      if (newValue == Worker.State.RUNNING) {
        var window = (JSObject) webEngine.executeScript("window");
        window.setMember("java", new JsBridge());
        webEngine.executeScript("console.log = function(message) { java.log(message); }");
        webEngine.executeScript("window.onerror = function(msg, url, line, col, error) { return java.onError(msg, url, line, col, error);};");
      } else if (newValue == Worker.State.SUCCEEDED) {
        createContextMenu(browser, html);
        tab.setContent(browser);
        viewPane.getSelectionModel().select(tab);
      } else if (newValue == Worker.State.FAILED) {
        log.warn("Failed to load content in view tab");
      }
    });
    webEngine.loadContent(html);
  }
}
