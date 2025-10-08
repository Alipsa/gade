package se.alipsa.gade.environment.connections;

import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
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
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.jetbrains.annotations.NotNull;
import se.alipsa.gade.Gade;
import se.alipsa.gade.UnStyledCodeArea;
import se.alipsa.gade.code.CodeType;
import se.alipsa.gade.code.groovytab.GroovyTextArea;
import se.alipsa.gade.model.TableMetaData;
import se.alipsa.gade.utils.*;
import se.alipsa.groovy.datautil.ConnectionInfo;
import se.alipsa.matrix.core.Matrix;
import se.alipsa.matrix.core.Row;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import static se.alipsa.gade.Constants.*;
import static se.alipsa.gade.utils.QueryBuilder.*;

public class ConnectionsTab extends Tab {

  static final String NAME_PREF = "ConnectionsTab.name";
  static final String DEPENDENCY_PREF = "ConnectionsTab.dependency";
  static final String DRIVER_PREF = "ConnectionsTab.driver";
  static final String URL_PREF = "ConnectionsTab.url";
  static final String USER_PREF = "ConnectionsTab.user";
  static final String CONNECTIONS_PREF = "ConnectionsTab.Connections";
  private final BorderPane contentPane;
  private final Gade gui;
  private final ComboBox<ConnectionInfo> name = new ComboBox<>();
  private TextField urlText;
  private TextField userText;
  private PasswordField passwordField;
  private final TableView<ConnectionInfo> connectionsTable = new TableView<>();

  private final TreeItemComparator treeItemComparator = new TreeItemComparator();

  private static final Logger log = LogManager.getLogger(ConnectionsTab.class);

  public ConnectionsTab(Gade gui) {
    setText("Connections");
    this.gui = gui;
    contentPane = new BorderPane();
    setContent(contentPane);

    VBox inputBox = new VBox();
    HBox topInputPane = new HBox();
    HBox bottomInputPane = new HBox();
    HBox buttonInputPane = new HBox();
    inputBox.getChildren().addAll(topInputPane, bottomInputPane, buttonInputPane);

    topInputPane.setPadding(FLOWPANE_INSETS);
    topInputPane.setSpacing(2);
    bottomInputPane.setPadding(FLOWPANE_INSETS);
    bottomInputPane.setSpacing(2);
    contentPane.setTop(inputBox);

    VBox nameBox = new VBox();
    Label nameLabel = new Label("Name:");

    String lastUsedName = getPrefOrBlank(NAME_PREF);
    for (String connectionName : getSavedConnectionNames()) {
      ConnectionInfo ci = getSavedConnection(connectionName);
      name.getItems().add(ci);
      if (ci.getName().equals(lastUsedName)) {
        name.setValue(ci);
      }
    }
    name.setPrefWidth(300);
    name.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
      ConnectionInfo ci = name.getValue()  ;
      if (ci == null) {
        return;
      }
      userText.setText(ci.getUser());
      urlText.setText(ci.getUrl());
      userText.setEditable(!ci.getUrl().contains("user"));
      passwordField.setEditable(!ci.getUrl().contains("password"));
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

    VBox urlBox = new VBox();
    Label urlLabel = new Label("Url:");
    urlText = new TextField(getPrefOrBlank(URL_PREF));
    urlText.setEditable(false);
    userText.setEditable(!urlText.getText().contains("user"));
    passwordField.setEditable(!urlText.getText().contains("password"));
    urlBox.getChildren().addAll(urlLabel, urlText);
    HBox.setHgrow(urlBox, Priority.ALWAYS);
    bottomInputPane.getChildren().add(urlBox);

    Button addButton = getAddConnectionButton();
    Button editConnectionButton = getEditConnectionButton();
    Button newButton = getNewConnectionButton();
    Button deleteButton = getDeleteConnectionButton(gui);

    createConnectionTableView();
    contentPane.setCenter(connectionsTable);
    connectionsTable.setPlaceholder(new Label("No connections defined"));

    buttonInputPane.setSpacing(10);
    buttonInputPane.getChildren().addAll(addButton, editConnectionButton, newButton, deleteButton);
  }

  @NotNull
  private Button getNewConnectionButton() {
    Button newButton = new Button("New");
    newButton.setPadding(new Insets(7, 10, 7, 10));
    newButton.setOnAction(a -> {
      var dialog = new ConnectionDialog(this);
      var result = dialog.showAndWait();
      if (result.isPresent()) {
        ConnectionInfo ci = result.get();
        name.getItems().add(ci);
        name.setValue(ci);
        userText.setText(ci.getUser());
        passwordField.setText(ci.getPassword());
        urlText.setText(ci.getUrl());
      }
    });
    return newButton;
  }

  @NotNull
  private Button getEditConnectionButton() {
    Button editConnectionButton = new Button("Edit");
    editConnectionButton.setPadding(new Insets(7, 10, 7, 10));
    editConnectionButton.setOnAction( a -> {
      ConnectionInfo existing = name.getValue();
      ConnectionDialog dialog = new ConnectionDialog(existing, this);
      var result = dialog.showAndWait();
      if (result.isPresent()) {
        ConnectionInfo ci = result.get();
        name.getItems().remove(existing);
        name.getItems().add(ci);
        name.setValue(ci);
      }
    });
    return editConnectionButton;
  }

  @NotNull
  private Button getAddConnectionButton() {
    Button addButton = new Button("Activate Connection");
    addButton.setPadding(new Insets(7, 10, 7, 10));
    addButton.setOnAction(e -> {
      if (name.getValue() == null) {
        Alerts.info("Information missing", "No connection name provided, cannot add it");
        return;
      }
      String urlString = urlText.getText().toLowerCase();
      if (urlString.contains("mysql") && !urlString.contains("allowmultiqueries=true")) {
        String msg = "In MySQL you should set allowMultiQueries=true in the connection string to be able to execute multiple queries";
        log.warn(msg);
        Alerts.info("MySQL and multiple query statements", msg);
      }
      ConnectionInfo con = name.getValue();
      if (!passwordField.getText().isEmpty()) {
        con.setPassword(passwordField.getText());
      }
      if (validateAndAddConnection(con)) return;
      connectionsTable.getSelectionModel().select(connectionsTable.getItems().indexOf(con));
    });
    return addButton;
  }

  @NotNull
  private Button getDeleteConnectionButton(Gade gui) {
    Button deleteButton = new Button("Delete");
    deleteButton.setPadding(new Insets(7, 10, 7, 10));
    deleteButton.setOnAction(a -> {
      boolean isConfirmed = Alerts.confirm("Are you sure?", "Delete " + name.getValue().getName(), "Completely remove the " + name.getValue() + " connection?");
      if (!isConfirmed) {
        return;
      }
      String connectionName = name.getValue().getName();
      Preferences pref = gui.getPrefs().node(CONNECTIONS_PREF).node(connectionName);
      try {
        pref.removeNode();
      } catch (BackingStoreException e) {
        ExceptionAlert.showAlert("Failed to remove the connection from preferences", e);
      }
      gui.getCodeComponent().removeConnectionFromTabs(connectionName);
      connectionsTable.getItems().removeIf(c -> c.getName().equals(connectionName));
      name.getItems().remove(name.getValue());
      name.setValue(null);
      userText.clear();
      passwordField.clear();
      urlText.clear();
      name.requestFocus();
    });
    return deleteButton;
  }

  /**
   * Validate the connection and add it to the list of connections.
   *
   * @param con the connection to validate
   * @return true if the connection was validated and added, false otherwise
   */
  public boolean validateAndAddConnection(ConnectionInfo con) {
    log.info("Connecting to " + con.getUrl());
    ConnectionHandler ch = new ConnectionHandler(con);
    boolean canConnect = ch.verifyConnection();
    if (!canConnect) {
      boolean dontSave = Alerts.confirm("Failed to establish connection", "Failed to establish connection to the database",
          "Do you still want to save this connection?");
      if (dontSave) {
        return false;
      }
    }
    log.info("Connection established successfully, all good!");
    addConnection(con);
    saveConnection(con);
    return true;
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
    if (name.getItems().stream().filter(c -> c.equals(con)).findAny().orElse(null) == null) {
      name.getItems().add(con);
      userText.setText(con.getUser());
      passwordField.setText(con.getPassword());
      urlText.setText(con.getUrl());
    }
    connectionsTable.refresh();
    gui.getCodeComponent().updateConnections();
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
      removeMenuItem.setOnAction(event -> {
        var item = row.getItem();
        tableView.getItems().remove(item);
        gui.getCodeComponent().removeConnectionFromTabs(item.getName());
      });
      final MenuItem deleteMenuItem = new MenuItem("delete connection permanently");
      deleteMenuItem.setOnAction(event -> {
        ConnectionInfo item = row.getItem();
        tableView.getItems().remove(item);
        deleteSavedConnection(item);
        name.getItems().remove(item.getName());
        tableView.refresh();
        gui.getCodeComponent().removeConnectionFromTabs(item.getName());
      });
      final MenuItem viewMenuItem = new MenuItem("view tables");
      viewMenuItem.setOnAction(event -> showConnectionMetaData(row.getItem()));
      final MenuItem viewDatabasesMenuItem = new MenuItem("view databases");
      viewDatabasesMenuItem.setOnAction(event -> showDatabases(row.getItem()));

      final MenuItem viewCodeMenuItem = new MenuItem("show connection code");
      viewCodeMenuItem.setOnAction(event -> showConnectionCode());

      contextMenu.getItems().addAll(viewMenuItem, viewDatabasesMenuItem, removeMenuItem, deleteMenuItem, viewCodeMenuItem);
      row.contextMenuProperty().bind(
          Bindings.when(row.emptyProperty())
              .then((ContextMenu) null)
              .otherwise(contextMenu)
      );

      tableView.getSelectionModel().selectedIndexProperty().addListener(e -> {
        ConnectionInfo info = tableView.getSelectionModel().getSelectedItem();
        if (info != null) {
          name.setValue(info);
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

  String getPrefOrBlank(String pref) {
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
    setPref(NAME_PREF, name.getValue().getName());
    setPref(DEPENDENCY_PREF, c.getDependency());
    setPref(DRIVER_PREF, c.getDriver());
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

  /**
   * @return a Sorted Set of copies of the active connections in the connectionsTable
   */
  public SortedSet<ConnectionInfo> getConnections() {
    return connectionsTable.getItems().stream().map(ConnectionInfo::new).collect(Collectors.toCollection(TreeSet::new));
  }

  /**
   * Get all the defined connections.
   *
   * @return a Set of copies of all the connections defined in Gade (saved in the profile)
   */
  public Set<ConnectionInfo> getDefinedConnections() {
    Set<ConnectionInfo> definedConnections = getConnections();
    // add the ones not active (HashSet contract does not add if element is already present)
    for (String name : getSavedConnectionNames()) {
      boolean isNew = definedConnections.add(getSavedConnection(name));
      if (isNew) {
        log.debug("adding saved connection for {}", name);
      } else {
        log.debug("{} already existed in active connections", name);
      }
    }
    return definedConnections;
  }

  /**
   * this is consistent for at least postgres, H2, sqlite and SQl server
   */
  private void showConnectionMetaData(ConnectionInfo con) {
    setWaitCursor();

    Task<Matrix> task = new Task<>() {
      @Override
      public Matrix call() throws Exception {
          ConnectionHandler ch = new ConnectionHandler(con);
          return ch.getConnectionMetadata();
      }
    };
    task.setOnSucceeded(e -> {
      try {
        createTableTree(task.get(), con);
        setNormalCursor();
      } catch (Throwable ex) {
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
      log.warn("Exception when running metadata sql", throwable);
      String msg = gui.getConsoleComponent().createMessageFromEvalException(ex);
      ExceptionAlert.showAlert(msg + ex.getMessage(), ex);
    });
    Thread scriptThread = new Thread(task);
    scriptThread.setDaemon(false);
    scriptThread.start();
  }

  private void showDatabases(ConnectionInfo connectionInfo) {
    try {
      ConnectionHandler ch = new ConnectionHandler(connectionInfo);
      List<String> dbList = ch.getDatabases();
      String content = String.join("\n", dbList);
      String title = "Databases for connection " + connectionInfo.getName();
      displayTextInWindow(title, content, CodeType.TXT);
    } catch (ConnectionException e) {
      String msg = gui.getConsoleComponent().createMessageFromEvalException(e.getCause());
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

  Matrix runQuery(String sql, ConnectionInfo con) throws ConnectionException {
    ConnectionHandler ch = new ConnectionHandler(con);
    return ch.query(sql);
  }

  void createTableTree(Matrix table, ConnectionInfo con) {
    String connectionName = con.getName();
    List<TableMetaData> metaDataList = new ArrayList<>();
    for (Row row : table) {
      metaDataList.add(new TableMetaData(row));
    }
    setNormalCursor();
    TreeView<String> treeView = createMetaDataTree(metaDataList, con);
    createAndShowWindow(connectionName + " connection view", treeView);
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

  /**
   * Set the cursor to default
   */
  public void setNormalCursor() {
    gui.setNormalCursor();
    contentPane.setCursor(Cursor.DEFAULT);
    connectionsTable.setCursor(Cursor.DEFAULT);
  }

  /**
   * Set the cursor to wait (timeglass)
   */
  public void setWaitCursor() {
    gui.setWaitCursor();
    contentPane.setCursor(Cursor.WAIT);
    connectionsTable.setCursor(Cursor.WAIT);
  }

  private TreeView<String> createMetaDataTree(List<TableMetaData> tableMetaDataList, ConnectionInfo con) {
    String connectionName = con.getName();
    TreeView<String> tree = new TreeView<>();
    TreeItem<String> root = new TreeItem<>(connectionName);
    tree.setRoot(root);

    Map<String, List<TableMetaData>> schemaMap = tableMetaDataList.stream()
        .collect(Collectors.groupingBy(TableMetaData::getSchemaName));

    schemaMap.forEach((schemaName, schemaTables) -> {
      TreeItem<String> schema = new TreeItem<>(schemaName);
      root.getChildren().add(schema);
      var tableGroup = schemaTables.stream().collect(Collectors.groupingBy(TableMetaData::getTableName));
      tableGroup.forEach((tableName, tableMetadataList) -> {
        TreeItem<String> tableNode = new TreeItem<>(tableName);
        schema.getChildren().add(tableNode);
        tableMetadataList.forEach( columnRows -> {
          TreeItem<String> columnNode = new TreeItem<>(columnRows.asColumnString());
          tableNode.getChildren().add(columnNode);
        });
        tableNode.getChildren().sort(treeItemComparator);
      });
      schema.getChildren().sort(treeItemComparator);
      schema.setExpanded(true);
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

  private String getSqlNodeValue(final TreeItem<String> treeItem) {
    String columnName = treeItem.getValue();
    int idx = columnName.indexOf(TableMetaData.COLUMN_META_START);
    if (idx > -1) {
      // it's a column
      columnName = columnName.substring(0, idx);
    } else {
      var parent = treeItem.getParent();
      if (parent != null) {
        if (parent.getParent() != null) {
          // it's a table, add the schema name
          columnName = treeItem.getParent().getValue() + "." + columnName;
        }
      }
    }
    return columnName;
  }

  private void copySelectionToClipboard(final TreeItem<String> treeItem) {
    final ClipboardContent clipboardContent = new ClipboardContent();
    String columnName = getSqlNodeValue(treeItem);
    clipboardContent.putString(columnName);
    Clipboard.getSystemClipboard().setContent(clipboardContent);
  }

  /**
   * Get the URL string of the connection.
   *
   * @return the url string
   */
  public String getUrl() {
    return urlText.getText();
  }

  public String getDriver() {
    return name.getValue().getDriver();
  }

  public String getDependency() {
    return name.getValue().getDependency();
  }

  public String getUser() {
    return userText.getText();
  }

  private static class TreeItemComparator implements Comparator<TreeItem<String>>, Serializable {

    @Serial
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
        String tableName = getSqlNodeValue(getTreeItem());
        try {
          ConnectionHandler ch = new ConnectionHandler(con);
          Matrix table = ch.query("SELECT * from " + tableName, 200);
          gui.getInoutComponent().viewTable(table, tableName);
        } catch (ConnectionException e) {
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

  public Gade getGui() {
    return gui;
  }
}
