package se.alipsa.gade.inout.viewer;

import static se.alipsa.gade.Constants.KEY_CODE_COPY;
import static se.alipsa.gade.inout.viewer.ViewHelper.createContextMenu;

import se.alipsa.groovy.datautil.TableUtil;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.text.Text;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Constants;
import se.alipsa.gade.Gade;
import se.alipsa.groovy.gmd.HtmlDecorator;
import se.alipsa.groovy.matrix.Grid;
import se.alipsa.groovy.matrix.Matrix;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Table;
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

public class ViewTab extends Tab {

  private static final Logger log = LogManager.getLogger();
  private static final List<String> NUMERIC_TYPES = List.of(
      "NUMBER", "BYTE", "SHORT", "INTEGER", "INT", "LONG", "BIGINTEGER",
      "FLOAT", "DOUBLE", "BIGDECIMAL"
  );

  private final TabPane viewPane;

  public ViewTab() {
    setText("Viewer");
    viewPane = new TabPane();
    setContent(viewPane);
    viewPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
  }

  public void viewTable(Table table, String... title) {
    List<String> types = new ArrayList<>();
    table.types().forEach(t -> types.add(t.name()));
    viewTable(table.columnNames(), TableUtil.toRowList(table), types, title);
  }

  public void viewTable(Matrix tableMatrix, String... title) {
    viewTable(tableMatrix.columnNames(), tableMatrix.rowList(), tableMatrix.columnTypeNames(), title);
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
      tableView.setOnKeyPressed(event -> {
        if (KEY_CODE_COPY.match(event)) {
          // Include header if all rows are selected
          boolean includeHeader = tableView.getSelectionModel().getSelectedCells().size() == rowList.size();
          if (includeHeader) {
            copySelectionToClipboard(tableView, headerList);
          } else {
            copySelectionToClipboard(tableView, null);
          }
        }
      });

      tableView.setRowFactory(tv -> {
        final TableRow<List<String>> row = new TableRow<>();
        final ContextMenu contextMenu = new ContextMenu();
        final MenuItem copyMenuItem = new MenuItem("copy");
        copyMenuItem.setOnAction(event -> copySelectionToClipboard(tv, null));
        final MenuItem copyWithHeaderMenuItem = new MenuItem("copy with header");
        copyWithHeaderMenuItem.setOnAction(event -> copySelectionToClipboard(tv, headerList));
        final MenuItem exportToCsvMenuItem = new MenuItem("export to csv");
        exportToCsvMenuItem.setOnAction(event -> exportToCsv(tv, headerList, title));

        contextMenu.getItems().addAll(copyMenuItem, copyWithHeaderMenuItem, exportToCsvMenuItem);
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


  @SuppressWarnings("rawtypes")
  private void copySelectionToClipboard(final TableView<?> table, List<String> headerList) {
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
        strb.append(cellData == null ? "" : cellData.toString());
      }
    }
    final ClipboardContent clipboardContent = new ClipboardContent();
    clipboardContent.putString(strb.toString());
    Clipboard.getSystemClipboard().setContent(clipboardContent);
  }

  private void exportToCsv(final TableView<?> table, List<String> headerList, String... title) {
    final Set<Integer> rows = new TreeSet<>();
    for (final TablePosition<?, ?> tablePosition : table.getSelectionModel().getSelectedCells()) {
      rows.add(tablePosition.getRow());
    }
    try {
      StringWriter sw = new StringWriter();
      CSVFormat format = CSVFormat.DEFAULT.builder()
          .setHeader(headerList.toArray(new String[0]))
          .build();
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
        // Clicking cancel and still have an action performed is not very good UX
        // TODO: Consider changing this to an explicit action (export csv -> to clipboard) instead
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
    WebView browser = new WebView();
    browser.setContextMenuEnabled(false);
    WebEngine webEngine = browser.getEngine();
    if (url.startsWith("http") || url.startsWith("file:")) {
      log.info("Opening {} in view tab", url);
      webEngine.load(url);
      createContextMenu(browser, url, true);
    } else {
      try {
        if (Paths.get(url).toFile().exists()) {
          String path = Paths.get(url).toUri().toURL().toExternalForm();
          log.info("Opening {} in view tab", path);
          webEngine.load(path);
          createContextMenu(browser, path, true);
        } else {
          log.info("url is not a http url nor a local path, assuming it is content...");
          webEngine.loadContent(url);
          createContextMenu(browser, url);
        }
      } catch (MalformedURLException | InvalidPathException e) {
        log.info("url is not a http url nor a local path, assuming it is content...");
        webEngine.loadContent(url);
        createContextMenu(browser, url);
      }
    }
    tab.setContent(browser);
    viewPane.getSelectionModel().select(tab);
  }

  public void viewHtmlWithBootstrap(String content, String... title) {
    Tab tab = new Tab();
    if (title.length > 0) {
      tab.setText(title[0]);
    }
    viewPane.getTabs().add(tab);
    WebView browser = new WebView();
    browser.setContextMenuEnabled(false);

    WebEngine webEngine = browser.getEngine();
    String html = HtmlDecorator.decorate(content, true);
    webEngine.loadContent(html);
    createContextMenu(browser, html);
    tab.setContent(browser);
    viewPane.getSelectionModel().select(tab);
  }
}
