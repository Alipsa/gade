package se.alipsa.gride.environment.connections;

import groovy.lang.GroovyClassLoader;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.fxmisc.flowless.VirtualizedScrollPane;
import se.alipsa.gride.Constants;
import se.alipsa.gride.Gride;
import se.alipsa.gride.UnStyledCodeArea;
import se.alipsa.gride.code.CodeType;
import se.alipsa.gride.code.groovytab.GroovyTextArea;
import se.alipsa.gride.model.TableMetaData;
import se.alipsa.gride.utils.*;
import se.alipsa.maven.MavenUtils;
import tech.tablesaw.api.Table;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.*;
import java.sql.Driver;
import java.util.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import static se.alipsa.gride.Constants.*;
import static se.alipsa.gride.utils.QueryBuilder.*;

public class ConnectionsTab extends Tab {

  private static final String NAME_PREF = "ConnectionsTab.name";
  private static final String DEPENDENCY_PREF = "ConnectionsTab.dependency";
  private static final String DRIVER_PREF = "ConnectionsTab.driver";
  private static final String URL_PREF = "ConnectionsTab.url";
  private static final String USER_PREF = "ConnectionsTab.user";
  private static final String CONNECTIONS_PREF = "ConnectionsTab.Connections";
  private final BorderPane contentPane;
  private final Gride gui;
  private final ComboBox<String> name = new ComboBox<>();
  private TextField dependencyText;
  private TextField driverText;
  private TextField urlText;
  private TextField userText;
  private PasswordField passwordField;
  private final TableView<ConnectionInfo> connectionsTable = new TableView<>();

  private final TreeItemComparator treeItemComparator = new TreeItemComparator();

  private static final Logger log = LogManager.getLogger(ConnectionsTab.class);

  public ConnectionsTab(Gride gui) {
    setText("Connections");
    this.gui = gui;
    contentPane = new BorderPane();
    setContent(contentPane);

    VBox inputBox = new VBox();
    HBox topInputPane = new HBox();
    HBox middleInputPane = new HBox();
    HBox bottomInputPane = new HBox();
    HBox buttonInputPane = new HBox();
    inputBox.getChildren().addAll(topInputPane, middleInputPane, bottomInputPane, buttonInputPane);

    topInputPane.setPadding(FLOWPANE_INSETS);
    topInputPane.setSpacing(2);
    middleInputPane.setPadding(FLOWPANE_INSETS);
    middleInputPane.setSpacing(2);
    bottomInputPane.setPadding(FLOWPANE_INSETS);
    bottomInputPane.setSpacing(2);
    contentPane.setTop(inputBox);

    VBox nameBox = new VBox();
    Label nameLabel = new Label("Name:");

    name.getItems().addAll(getSavedConnectionNames());
    String lastUsedName = getPrefOrBlank(NAME_PREF);
    name.setEditable(true);
    if (!"".equals(lastUsedName)) {
      if (name.getItems().contains(lastUsedName)) {
        name.getSelectionModel().select(lastUsedName);
      } else {
        name.getItems().add(lastUsedName);
      }
    }
    name.getEditor().focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
      if (! isNowFocused) {
        name.setValue(name.getEditor().getText());
      }
    });
    name.setPrefWidth(300);
    name.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
      ConnectionInfo ci = getSavedConnection(name.getValue());
      dependencyText.setText(ci.getDependency());
      userText.setText(ci.getUser());
      driverText.setText(ci.getDriver());
      urlText.setText(ci.getUrl());
      passwordField.clear();
      passwordField.requestFocus();
      connectionsTable.getSelectionModel().select(connectionsTable.getItems().indexOf(ci));
    });
    nameBox.getChildren().addAll(nameLabel, name);
    topInputPane.getChildren().add(nameBox);
    HBox.setHgrow(nameBox, Priority.SOMETIMES);

    VBox userBox = new VBox();
    Label userLabel = new Label("User:");
    userText = new TextField(getPrefOrBlank(USER_PREF));
    HBox.setHgrow(userBox, Priority.SOMETIMES);
    userBox.getChildren().addAll(userLabel, userText);
    topInputPane.getChildren().add(userBox);

    VBox passwordBox = new VBox();
    Label passwordLabel = new Label("Password:");
    passwordField = new PasswordField();
    HBox.setHgrow(passwordBox, Priority.SOMETIMES);
    passwordBox.getChildren().addAll(passwordLabel, passwordField);
    topInputPane.getChildren().add(passwordBox);

    VBox dependencyBox = new VBox();
    Label dependencyLabel = new Label("Dependency:");
    dependencyText = new TextField(getPrefOrBlank(DEPENDENCY_PREF));
    HBox.setHgrow(dependencyBox, Priority.SOMETIMES);
    dependencyBox.getChildren().addAll(dependencyLabel, dependencyText);
    middleInputPane.getChildren().add(dependencyBox);

    VBox driverBox = new VBox();
    Label driverLabel = new Label("Driver:");
    driverText = new TextField(getPrefOrBlank(DRIVER_PREF));
    HBox.setHgrow(driverBox, Priority.SOMETIMES);
    driverBox.getChildren().addAll(driverLabel, driverText);
    middleInputPane.getChildren().add(driverBox);

    VBox urlBox = new VBox();
    Label urlLabel = new Label("Url:");
    urlText = new TextField(getPrefOrBlank(URL_PREF));
    urlBox.getChildren().addAll(urlLabel, urlText);
    HBox.setHgrow(urlBox, Priority.ALWAYS);
    bottomInputPane.getChildren().add(urlBox);

    Button newButton = new Button("New");
    newButton.setPadding(new Insets(7, 10, 7, 10));
    newButton.setOnAction(a -> {
      name.setValue("");
      dependencyText.clear();
      userText.clear();
      passwordField.clear();
      driverText.clear();
      urlText.clear();
    });

    Button deleteButton = new Button("Delete");
    deleteButton.setPadding(new Insets(7, 10, 7, 10));
    deleteButton.setOnAction(a -> {
      String connectionName = name.getValue();
      Preferences pref = gui.getPrefs().node(CONNECTIONS_PREF).node(connectionName);
      try {
        pref.removeNode();
      } catch (BackingStoreException e) {
        ExceptionAlert.showAlert("Failed to remove the connection from preferences", e);
      }
      gui.getCodeComponent().removeConnectionFromTabs(connectionName);
      connectionsTable.getItems().removeIf(c -> c.getName().equals(connectionName));
      name.getItems().remove(connectionName);
      name.setValue("");
      dependencyText.clear();
      userText.clear();
      passwordField.clear();
      driverText.clear();
      urlText.clear();
      name.requestFocus();
    });

    Button addButton = new Button("Add / Update Connection");
    addButton.setPadding(new Insets(7, 10, 7, 10));
    createConnectionTableView();
    contentPane.setCenter(connectionsTable);
    connectionsTable.setPlaceholder(new Label("No connections defined"));

    addButton.setOnAction(e -> {
      if (name.getValue() == null || name.getValue().isBlank()) {
        Alerts.info("Information missing", "No connection name provided, cannot add it");
        return;
      }
      String urlString = urlText.getText().toLowerCase();
      if (urlString.contains("mysql") && !urlString.contains("allowmultiqueries=true")) {
        String msg = "In MySQL you should set allowMultiQueries=true in the connection string to be able to execute multiple queries";
        log.warn(msg);
        Alerts.info("MySQL and multiple query statements", msg);
      }
      ConnectionInfo con = new ConnectionInfo(name.getValue(), dependencyText.getText(), driverText.getText(), urlText.getText(), userText.getText(), passwordField.getText());
      try {
        log.info("Connecting to " + urlString);
        Connection connection = connect(con);
        if (connection == null) {
          boolean dontSave = Alerts.confirm("Failed to establish connection", "Failed to establish connection to the database",
              "Do you still want to save this connection?");
          if (dontSave) {
            return;
          }
        }
        if (connection != null) {
          connection.close();
        }
        log.info("Connection created successfully, all good!");
      } catch (SQLException ex) {
        Exception exceptionToShow = ex;
        try {
          JdbcUrlParser.validate(driverText.getText(), urlText.getText());
        } catch (MalformedURLException exc) {
          exceptionToShow = exc;
        }
        ExceptionAlert.showAlert("Failed to connect to database: " + exceptionToShow, ex);
        return;
      }
      addConnection(con);
      saveConnection(con);
      connectionsTable.getSelectionModel().select(connectionsTable.getItems().indexOf(con));
    });
    /*VBox buttonBox = new VBox();
    buttonBox.setPadding(new Insets(10, 10, 0, 10));
    buttonBox.setSpacing(VGAP);
    buttonBox.getChildren().add(addButton);
    buttonBox.alignmentProperty().setValue(Pos.BOTTOM_CENTER);*/
    Image wizIMage = new Image("image/wizard.png", ICON_WIDTH, ICON_HEIGHT, true, true);
    ImageView wizImg =  new ImageView(wizIMage);
    Button wizardButton = new Button("Url Wizard", wizImg);
    wizardButton.setOnAction(this::openUrlWizard);
    wizardButton.setTooltip(new Tooltip("create/update the url using the wizard"));
    buttonInputPane.setAlignment(Pos.CENTER);
    Insets btnInsets = new Insets(5, 10, 5, 10);
    wizardButton.setPadding(btnInsets);
    buttonInputPane.setSpacing(10);
    buttonInputPane.getChildren().addAll(newButton, addButton, wizardButton, deleteButton);
  }

  private void addConnection(ConnectionInfo con) {
    log.debug("Add or update connection for {}", con.asJson());
    ConnectionInfo existing = connectionsTable.getItems().stream()
       .filter(c -> con.getName().equals(c.getName()))
       .findAny().orElse(null);
    if (existing == null) {
      connectionsTable.getItems().add(con);
    } else {
      existing.setUser(con.getUser());
      existing.setPassword(con.getPassword());
      existing.setDependency(con.getDependency());
      existing.setDriver(con.getDriver());
      existing.setUrl(con.getUrl());

    }
    if (name.getItems().stream().filter(c -> c.equals(con.getName())).findAny().orElse(null) == null) {
      name.getItems().add(con.getName());
      dependencyText.setText(con.getDependency());
      userText.setText(con.getUser());
      passwordField.setText(con.getPassword());
      driverText.setText(con.getDriver());
      urlText.setText(con.getUrl());
    }
    connectionsTable.refresh();
    gui.getCodeComponent().updateConnections();
  }

  private void openUrlWizard(ActionEvent actionEvent) {

    JdbcUrlWizardDialog dialog = new JdbcUrlWizardDialog(gui);
    Optional<ConnectionInfo> result = dialog.showAndWait();
    if (result.isEmpty()) {
      return;
    }
    ConnectionInfo ci = result.get();
    driverText.setText(ci.getDriver());
    urlText.setText(ci.getUrl());
  }

  private void createConnectionTableView() {
    TableColumn<ConnectionInfo, String> nameCol = new TableColumn<>("Name");
    nameCol.setCellValueFactory(
        new PropertyValueFactory<>("name")
    );
    nameCol.prefWidthProperty().bind(connectionsTable.widthProperty().multiply(0.2));
    connectionsTable.getColumns().add(nameCol);

    TableColumn<ConnectionInfo, String> driverCol = new TableColumn<>("Driver");
    driverCol.setCellValueFactory(
        new PropertyValueFactory<>("driver")
    );
    driverCol.prefWidthProperty().bind(connectionsTable.widthProperty().multiply(0.3));
    connectionsTable.getColumns().add(driverCol);

    TableColumn<ConnectionInfo, String> urlCol = new TableColumn<>("URL");
    urlCol.setCellValueFactory(
        new PropertyValueFactory<>("url")
    );
    urlCol.prefWidthProperty().bind(connectionsTable.widthProperty().multiply(0.5));
    connectionsTable.getColumns().add(urlCol);

    connectionsTable.setRowFactory(tableView -> {
      final TableRow<ConnectionInfo> row = new TableRow<>();
      final ContextMenu contextMenu = new ContextMenu();
      final MenuItem removeMenuItem = new MenuItem("remove connection");
      removeMenuItem.setOnAction(event -> tableView.getItems().remove(row.getItem()));
      final MenuItem deleteMenuItem = new MenuItem("delete connection permanently");
      deleteMenuItem.setOnAction(event -> {
        ConnectionInfo item = row.getItem();
        tableView.getItems().remove(item);
        deleteSavedConnection(item);
        name.getItems().remove(item.getName());
        tableView.refresh();
      });
      final MenuItem viewMenuItem = new MenuItem("view tables");
      viewMenuItem.setOnAction(event -> showConnectionMetaData(row.getItem()));
      final MenuItem viewDatabasesMenuItem = new MenuItem("view databases");
      viewDatabasesMenuItem.setOnAction(event -> showDatabases(row.getItem()));

      final MenuItem viewRcodeMenuItem = new MenuItem("show connection code");
      viewRcodeMenuItem.setOnAction(event -> showConnectionCode());

      contextMenu.getItems().addAll(viewMenuItem, viewDatabasesMenuItem, removeMenuItem, deleteMenuItem, viewRcodeMenuItem);
      row.contextMenuProperty().bind(
          Bindings.when(row.emptyProperty())
              .then((ContextMenu) null)
              .otherwise(contextMenu)
      );

      tableView.getSelectionModel().selectedIndexProperty().addListener(e -> {
        ConnectionInfo info = tableView.getSelectionModel().getSelectedItem();
        if (info != null) {
          name.setValue(info.getName());
          dependencyText.setText(info.getDependency());
          driverText.setText(info.getDriver());
          urlText.setText(info.getUrl());
          userText.setText(info.getUser());
          passwordField.setText(info.getPassword());
        }
      });
      return row;
    });
  }

  private void showConnectionCode() {
    ConnectionInfo info = connectionsTable.getSelectionModel().getSelectedItem();
    String code = createConnectionCode(info);
    displayTextInWindow("Groovy connection code for " + info.getName(), code, CodeType.GROOVY);
  }

  private String createConnectionCode(ConnectionInfo info) {
    StringBuilder code = QueryBuilder.baseQueryString(info, "select * from someTable");
    String rCode = code.toString();
    rCode = rCode.replace(DRIVER_VAR_NAME, "drv");
    rCode = rCode.replace(CONNECTION_VAR_NAME, "con");
    return rCode;
  }

  private String getPrefOrBlank(String pref) {
    return gui.getPrefs().get(pref, "");
  }

  private String[] getSavedConnectionNames() {
    try {
      return gui.getPrefs().node(CONNECTIONS_PREF).childrenNames();
    } catch (BackingStoreException e) {
      ExceptionAlert.showAlert("Failed to get saved connections", e);
    }
    return new String[]{};
  }

  private ConnectionInfo getSavedConnection(String name) {
    Preferences pref = gui.getPrefs().node(CONNECTIONS_PREF).node(name);
    ConnectionInfo c = new ConnectionInfo();
    c.setName(name);
    c.setDependency(pref.get(DEPENDENCY_PREF, ""));
    c.setDriver(pref.get(DRIVER_PREF, ""));
    c.setUrl(pref.get(URL_PREF, ""));
    c.setUser(pref.get(USER_PREF, ""));
    return c;
  }

  private void saveConnection(ConnectionInfo c) {
    // Save current
    setPref(NAME_PREF, name.getValue());
    setPref(DEPENDENCY_PREF, dependencyText.getText());
    setPref(DRIVER_PREF, driverText.getText());
    setPref(URL_PREF, urlText.getText());
    setPref(USER_PREF, userText.getText());
    // Save to list of defined connections
    Preferences pref = gui.getPrefs().node(CONNECTIONS_PREF).node(c.getName());
    pref.put(DEPENDENCY_PREF, c.getDependency());
    pref.put(DRIVER_PREF, c.getDriver());
    pref.put(URL_PREF, c.getUrl());
    if (c.getUser() != null) {
      pref.put(USER_PREF, c.getUser());
    }
  }

  private void deleteSavedConnection(ConnectionInfo c) {
    Preferences pref = gui.getPrefs().node(CONNECTIONS_PREF).node(c.getName());
    try {
      pref.removeNode();
    } catch (BackingStoreException e) {
      ExceptionAlert.showAlert("Failed to remove saved connection", e);
    }
  }

  private void setPref(String pref, String val) {
    gui.getPrefs().put(pref, val);
  }

  public Set<ConnectionInfo> getConnections() {
    return new TreeSet<>(connectionsTable.getItems());
  }

  /**
   * this is consistent for at least postgres, H2, sqlite and SQl server
   */
  private void showConnectionMetaData(ConnectionInfo con) {
    setWaitCursor();
    String sql;
    if (con.getDriver().equals(Constants.Driver.SQLLITE.getDriverClass())) {
      boolean hasTables = false;
      try {
        Connection jdbcCon = connect(con);
        if (jdbcCon == null) {
          Alerts.warn("Failed to connect", "Failed to establish a connection to SQLite");
          return;
        }
        ResultSet rs = jdbcCon.createStatement().executeQuery("select * from sqlite_master");
        if (rs.next()) hasTables = true;
        jdbcCon.close();
      } catch (SQLException e) {
        ExceptionAlert.showAlert("Failed to query sqlite_master", e);
      }
      if (hasTables) {
        sql = "SELECT \n" +
           "  m.name as TABLE_NAME \n" +
           ", m.type as TABLE_TYPE \n" +
           ", p.name as COLUMN_NAME\n" +
           ", p.cid as ORDINAL_POSITION\n" +
           ", case when p.[notnull] = 0 then 1 else 0 end as IS_NULLABLE\n" +
           ", p.type as DATA_TYPE\n" +
           ", 0 as CHARACTER_MAXIMUM_LENGTH\n" +
           ", 0 as NUMERIC_PRECISION\n" +
           ", 0 as NUMERIC_SCALE\n" +
           ", '' as COLLATION_NAME\n" +
           "FROM \n" +
           "  sqlite_master AS m\n" +
           "JOIN \n" +
           "  pragma_table_info(m.name) AS p";
      } else {
        setNormalCursor();
        Alerts.info("Empty database", "This sqlite database has no tables yet");
        return;
      }
    } else {
      sql = "select col.TABLE_NAME\n" +
         ", TABLE_TYPE\n" +
         ", COLUMN_NAME\n" +
         ", ORDINAL_POSITION\n" +
         ", IS_NULLABLE\n" +
         ", DATA_TYPE\n" +
         ", CHARACTER_MAXIMUM_LENGTH\n" +
         ", NUMERIC_PRECISION\n" +
         ", NUMERIC_SCALE\n" +
         ", COLLATION_NAME\n" +
         "from INFORMATION_SCHEMA.COLUMNS col\n" +
         "inner join INFORMATION_SCHEMA.TABLES tab " +
         "      on col.TABLE_NAME = tab.TABLE_NAME and col.TABLE_SCHEMA = tab.TABLE_SCHEMA\n" +
         "where TABLE_TYPE <> 'SYSTEM TABLE'\n" +
         "and tab.TABLE_SCHEMA not in ('SYSTEM TABLE', 'PG_CATALOG', 'INFORMATION_SCHEMA', 'pg_catalog', 'information_schema')";
    }

    runQueryInThread(sql, con);
    /*
    try {
      log.info("java.library.path={}", System.getProperty("java.library.path"));
      Table table = runQuery(sql, con);
      createTableTree(table, con);
    } catch (SQLException e) {
      ExceptionAlert.showAlert("Failed to run meta data query", e);
    }
     */
  }

  private void showDatabases(ConnectionInfo connectionInfo) {

    try(Connection con = connect(connectionInfo)) {

      if (con == null) {
        Alerts.warn("Failed to connect to db", "Failed to establish a connection to the database");
        return;
      }

      DatabaseMetaData meta = con.getMetaData();
      ResultSet res = meta.getCatalogs();
      List<String> dbList = new ArrayList<>();
      while (res.next()) {
        dbList.add(res.getString("TABLE_CAT"));
      }
      res.close();
      String content = String.join("\n", dbList);
      String title = "Databases for connection " + connectionInfo.getName();

      displayTextInWindow(title, content, CodeType.TXT);

    } catch (SQLException e) {
      String msg = gui.getConsoleComponent().createMessageFromEvalException(e);
      ExceptionAlert.showAlert(msg + e.getMessage(), e);
    }
  }

  private void displayTextInWindow(String title, String content, CodeType codeType) {
    UnStyledCodeArea ta;
    Text text = new Text(content);
    if (CodeType.GROOVY.equals(codeType)) {
      ta = new GroovyTextArea();
      text.setStyle("-fx-font-family: monospace;");
    } else {
      ta = new UnStyledCodeArea();
      ta.setStyle("-fx-font-family:" + Font.getDefault().getFamily());
      text.setStyle("-fx-font-family:" + Font.getDefault().getFamily());
    }

    ta.getStyleClass().add("txtarea");
    ta.setWrapText(false);
    ta.replaceText(content);

    //TODO: get the fontsize from the ta instead of the below!
    double fontSize = 8.5; // ta.getFont().getSize() does not exist

    double height = text.getLayoutBounds().getHeight() +  fontSize * 2;
    double prefHeight = Math.max(height, 100.0);
    prefHeight = prefHeight > 640  ? 640 : prefHeight;
    ta.setPrefHeight( prefHeight );

    double maxWidth = 0;
    for (String line : content.split("\n")) {
      double length = line.length() * fontSize;
      if (maxWidth < length) {
        maxWidth = length;
      }
    }

    double prefWidth = maxWidth < 150 ? 150 : maxWidth;
    prefWidth = prefWidth > 800 ? 800 : prefWidth;
    ta.setPrefWidth(prefWidth);

    ta.autosize();

    ta.setEditable(false);
    VirtualizedScrollPane<UnStyledCodeArea> scrollPane = new VirtualizedScrollPane<>(ta);
    createAndShowWindow(title, scrollPane);
  }

  Table runQuery(String sql, ConnectionInfo con) throws SQLException {
    try (Connection connection = connect(con)){
      ResultSet rs = connection.createStatement().executeQuery(sql);
      return Table.read().db(rs);
    }
  }

  void createTableTree(Table table, ConnectionInfo con) {
    String connectionName = con.getName();
    List<TableMetaData> metaDataList = new ArrayList<>();
    for (var row : table) {
      metaDataList.add(new TableMetaData(row));
    }
    setNormalCursor();
    TreeView<String> treeView = createMetaDataTree(metaDataList, con);
    createAndShowWindow(connectionName + " connection view", treeView);
  }

  void runQueryInThread(String sql, ConnectionInfo con) {
    Task<Table> task = new Task<>() {
      @Override
      public Table call() throws Exception {
        try (Connection connection = connect(con)){
          ResultSet rs = connection.createStatement().executeQuery(sql);
          return Table.read().db(rs);
        } catch (RuntimeException e) {
          // RuntimeExceptions (such as EvalExceptions is not caught so need to wrap all in an exception
          // this way we can get to the original one by extracting the cause from the thrown exception
          throw new Exception(e);
        }
      }
    };
    task.setOnSucceeded(e -> {
      try {
        createTableTree(task.get(), con);
      } catch (Exception ex) {
        setNormalCursor();
        ExceptionAlert.showAlert("Failed to create connection tree view", ex);
      }
    });

    task.setOnFailed(e -> {
      setNormalCursor();
      Throwable throwable = task.getException();
      Throwable ex = throwable.getCause();
      if (ex == null) {
        ex = throwable;
      }
      String msg = gui.getConsoleComponent().createMessageFromEvalException(ex);
      log.warn("Exception when running sql code {}", sql);
      ExceptionAlert.showAlert(msg + ex.getMessage(), ex);
    });
    Thread scriptThread = new Thread(task);
    scriptThread.setDaemon(false);
    scriptThread.start();
  }

  private void createAndShowWindow(String title, Parent view) {
    Scene dialog = new Scene(view);
    Stage stage = new Stage();
    stage.initStyle(StageStyle.DECORATED);
    stage.initModality(Modality.NONE);
    stage.setTitle(title);
    stage.setScene(dialog);
    stage.setAlwaysOnTop(true);
    stage.setResizable(true);
    GuiUtils.addStyle(gui, stage);
    stage.show();
    stage.requestFocus();
    stage.toFront();
  }

  public void setNormalCursor() {
    gui.setNormalCursor();
    contentPane.setCursor(Cursor.DEFAULT);
    connectionsTable.setCursor(Cursor.DEFAULT);
  }

  public void setWaitCursor() {
    gui.setWaitCursor();
    contentPane.setCursor(Cursor.WAIT);
    connectionsTable.setCursor(Cursor.WAIT);
  }

  private TreeView<String> createMetaDataTree(List<TableMetaData> table, ConnectionInfo con) {
    String connectionName = con.getName();
    TreeView<String> tree = new TreeView<>();
    TreeItem<String> root = new TreeItem<>(connectionName);
    tree.setRoot(root);
    Map<String, List<TableMetaData>> tableMap = table.stream()
        .collect(Collectors.groupingBy(TableMetaData::getTableName));
    tableMap.forEach((k, v) -> {
      TreeItem<String> tableName = new TreeItem<>(k);
      root.getChildren().add(tableName);
      v.forEach(c -> {
        TreeItem<String> column = new TreeItem<>(c.asColumnString());
        tableName.getChildren().add(column);
      });
      tableName.getChildren().sort(treeItemComparator);
    });
    root.getChildren().sort(treeItemComparator);
    root.setExpanded(true);
    tree.setOnKeyPressed(event -> {
      if (KEY_CODE_COPY.match(event)) {
        copySelectionToClipboard(tree);
      }
    });
    tree.setCellFactory(p -> new TableNameTreeCell(con));
    return tree;
  }

  private void copySelectionToClipboard(final TreeView<String> treeView) {
    TreeItem<String> treeItem = treeView.getSelectionModel().getSelectedItem();
    copySelectionToClipboard(treeItem);
  }

  private void copySelectionToClipboard(final TreeItem<String> treeItem) {
    final ClipboardContent clipboardContent = new ClipboardContent();
    String value = treeItem.getValue();
    int idx = value.indexOf(TableMetaData.COLUMN_META_START);
    if (idx > -1) {
      value = value.substring(0, idx);
    }
    clipboardContent.putString(value);
    Clipboard.getSystemClipboard().setContent(clipboardContent);
  }

  private static class TreeItemComparator implements Comparator<TreeItem<String>>, Serializable {

    private static final long serialVersionUID = -7997376258097396238L;

    @Override
    public int compare(TreeItem<String> fileTreeItem, TreeItem<String> t1) {
      return fileTreeItem.getValue().compareToIgnoreCase(t1.getValue());
    }
  }

  private final class TableNameTreeCell extends TreeCell<String> {
    private final ContextMenu tableRightClickMenu = new ContextMenu();
    private final ContextMenu columnRightClickMenu = new ContextMenu();

    TableNameTreeCell(ConnectionInfo con) {
      MenuItem copyItem = new MenuItem("copy");
      tableRightClickMenu.getItems().add(copyItem);
      copyItem.setOnAction( event -> copySelectionToClipboard(getTreeItem()) );

      MenuItem copyItem2 = new MenuItem("copy");
      columnRightClickMenu.getItems().add(copyItem2);
      copyItem2.setOnAction( event -> copySelectionToClipboard(getTreeItem()) );

      MenuItem sampleContent = new MenuItem("View 200 rows");
      tableRightClickMenu.getItems().add(sampleContent);
      sampleContent.setOnAction(event -> {
        String tableName = getTreeItem().getValue();
        try (Connection connection = connect(con)){
          if (connection == null) {
            Alerts.warn("Failed to connect to database", "Failed to establish a connection to the database");
            return;
          }
          try (Statement stm=connection.createStatement()){
            stm.setMaxRows(200);
            Table table;
            try (ResultSet rs = stm.executeQuery("SELECT * from " + tableName)) {
              rs.setFetchSize(200);
              table = Table.read().db(rs);
            }
            gui.getInoutComponent().viewTable(table, tableName);

          }
        } catch (SQLException e) {
          ExceptionAlert.showAlert("Failed to sample table", e);
        }
      });
    }


    @Override
    public void updateItem(String item, boolean empty) {
      super.updateItem(item, empty);
      if (empty) {
        setText(null);
        setGraphic(null);
      } else {
        setText(item);
        setGraphic(getTreeItem().getGraphic());
        if ( (!getTreeItem().isLeaf()) && (getTreeItem().getParent() != null) ) {
          setContextMenu(tableRightClickMenu);
        } else if (getTreeItem().isLeaf()) {
          setContextMenu(columnRightClickMenu);
        }
      }
    }
  }


  @SuppressWarnings("unchecked")
  public Connection connect(ConnectionInfo ci) throws SQLException {
    log.info("Connecting to {} using {}", ci.getUrl(), ci.getDependency());
    var gui = Gride.instance();
    Driver driver;

    try {
      MavenUtils mavenUtils = new MavenUtils();
      String[] dep = ci.getDependency().split(":");
      log.info("Resolving dependency {}", ci.getDependency());
      File jar = mavenUtils.resolveArtifact(dep[0], dep[1], null, "jar", dep[2]);
      URL url = jar.toURI().toURL();
      URL[] urls = new URL[]{url};
      log.info("Dependency url is {}", urls[0]);
      if (gui.dynamicClassLoader == null) {
        ClassLoader cl;
        cl = gui.getConsoleComponent().getClassLoader();
        gui.dynamicClassLoader = new GroovyClassLoader(cl);
      }

      if (Arrays.stream(gui.dynamicClassLoader.getURLs()).noneMatch(p -> p.equals(url))) {
        gui.dynamicClassLoader.addURL(url);
      }

    } catch (SettingsBuildingException | ArtifactResolutionException | MalformedURLException e) {
      Platform.runLater(() ->
          ExceptionAlert.showAlert(ci.getDriver() + " could not be loaded from dependency " + ci.getDependency(), e)
      );
      return null;
    }


    try {
      log.info("Attempting to load the class {}", ci.getDriver());
      Class<Driver> clazz = (Class<Driver>) gui.dynamicClassLoader.loadClass(ci.getDriver());
      log.info("Loaded driver from session classloader, instating the driver {}", ci.getDriver());
      try {
        driver = clazz.getDeclaredConstructor().newInstance();
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | NullPointerException e) {
        log.error("Failed to instantiate the driver: {}, clazz is {}", ci.getDriver(), clazz, e);
        Platform.runLater(() ->
            Alerts.showAlert("Failed to instantiate the driver",
                ci.getDriver() + " could not be loaded from dependency " + ci.getDependency(),
                Alert.AlertType.ERROR)
        );
        return null;
      }
    } catch (ClassCastException | ClassNotFoundException e) {
      Platform.runLater(() ->
          Alerts.showAlert("Failed to load driver",
              ci.getDriver() + " could not be loaded from dependency " + ci.getDependency(),
              Alert.AlertType.ERROR)
      );
      return null;
    }
    Properties props = new Properties();
    if ( urlContainsLogin(ci.getUrlSafe()) ) {
      log.info("Skipping specified user/password since it is part of the url");
    } else {
      if (ci.getUser() != null) {
        props.put("user", ci.getUser());
        if (ci.getPassword() != null) {
          props.put("password", ci.getPassword());
        }
      }
    }
    if (gui != null) {
      gui.setNormalCursor();
    }
    return driver.connect(ci.getUrl(), props);
  }

  public boolean urlContainsLogin(String url) {
    String safeLcUrl = url.toLowerCase();
    return ( safeLcUrl.contains("user") && safeLcUrl.contains("pass") ) || safeLcUrl.contains("@");
  }
}
