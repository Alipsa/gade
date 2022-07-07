package se.alipsa.gride.environment.connections;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gride.Gride;
import se.alipsa.gride.utils.FileUtils;
import se.alipsa.gride.utils.GuiUtils;
import se.alipsa.gride.utils.IntField;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static se.alipsa.gride.Constants.*;
import static se.alipsa.gride.Constants.MavenRepositoryUrl.MAVEN_CENTRAL;
import static se.alipsa.gride.utils.MavenRepoLookup.getLatestArtifact;
import static se.alipsa.gride.utils.MavenRepoLookup.getLatestArtifactShortString;

public class JdbcUrlWizardDialog extends Dialog<ConnectionInfo> {
  private static final Logger log = LogManager.getLogger();

  private final Gride gui;

  private final ComboBox<String> driver = new ComboBox<>();
  private final TextField dependency = new TextField();
  private final TextField server = new TextField();
  private final IntField port = new IntField(0, 65535, 5432);
  private final TextField database = new TextField();
  private final TextArea url = new TextArea();
  private final HBox connectionMethodsBox = new HBox();
  private final ComboBox<String> connectMethods = new ComboBox<>();
  private final VBox optionsBox = new VBox();
  private final List<String> options = new ArrayList<>();
  private String initialOptionsDelimiter = "?";
  String subsequentOptionsDelimiter = "&";

  private static final int TF_LENGTH = 250;

  private String urlTemplate = "";

  public JdbcUrlWizardDialog(Gride gui) {
    this.gui = gui;

    setTitle("Create Connection URL Wizard");
    GuiUtils.addStyle(gui, this);

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(10, 15, 10, 10));
    getDialogPane().setContent(grid);

    getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    //getDialogPane().lookupButton(ButtonType.OK).setDisable(true);

    int rowIndex = 0;

    driver.getItems().addAll(
        Driver.POSTGRES.getDriverClass(),
        Driver.MYSQL.getDriverClass(),
        Driver.MARIADB.getDriverClass(),
        Driver.H2.getDriverClass(),
        Driver.SQLSERVER.getDriverClass(),
        Driver.SQLLITE.getDriverClass(),
        Driver.FIREBIRD.getDriverClass(),
        Driver.DERBY.getDriverClass(),
        Driver.ORACLE.getDriverClass()
    );

    driver.getEditor().focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
      if (!isNowFocused) {
        driver.setValue(driver.getEditor().getText());
      }
    });
    driver.setOnAction(this::addDefaultsForDriver);

    grid.add(new Label("Driver: "), 0, ++rowIndex);
    grid.add(driver, 1, rowIndex);

    grid.add(new Label("Dependency: "), 0, ++rowIndex);
    HBox dependencyGroup = new HBox();
    dependencyGroup.getChildren().add(dependency);
    Button latestVersionButton = new Button("Fetch Latest");
    latestVersionButton.setOnAction(a -> dependency.setText(
      getLatestArtifactShortString(dependency.getText(), MAVEN_CENTRAL.baseUrl)
    ));
    dependencyGroup.getChildren().add(latestVersionButton);
    HBox.setHgrow(dependency, Priority.ALWAYS);
    grid.add(dependencyGroup, 1, rowIndex);

    grid.add(new Label("Connection type"), 0, ++rowIndex);
    connectionMethodsBox.getChildren().add(connectMethods);
    grid.add(connectionMethodsBox, 1, rowIndex);
    connectMethods.setDisable(true);

    server.setPrefWidth(TF_LENGTH);
    grid.add(new Label("Server: "), 0, ++rowIndex);
    grid.add(server, 1, rowIndex);
    server.setOnAction(e -> updateUrl());
    server.focusedProperty().addListener((arg0, oldPropertyValue, newPropertyValue) -> {
      if (!newPropertyValue) {
        updateUrl();
      }
    });

    grid.add(new Label("Port: "), 0, ++rowIndex);
    grid.add(port, 1, rowIndex);
    port.focusedProperty().addListener((arg0, oldPropertyValue, newPropertyValue) -> {
      if (!newPropertyValue) {
        updateUrl();
      }
    });

    grid.add(new Label("Database: "), 0, ++rowIndex);
    grid.add(database, 1, rowIndex);
    database.focusedProperty().addListener((arg0, oldPropertyValue, newPropertyValue) -> {
      if (!newPropertyValue) {
        updateUrl();
      }
    });

    grid.add(new Label("Options: "), 0, ++rowIndex);
    optionsBox.setSpacing(5);
    grid.add(optionsBox, 1, rowIndex);

    grid.add(new Label("Url: "), 0, ++rowIndex);
    grid.add(url, 1, rowIndex);
    url.setPrefRowCount(2);
    url.setEditable(false);


    setResizable(true);
    String styleSheetPath = gui.getPrefs().get(THEME, BRIGHT_THEME);
    URL styleSheetUrl = FileUtils.getResourceUrl(styleSheetPath);
    if (styleSheetUrl != null) {
      getDialogPane().getStylesheets().add(styleSheetUrl.toExternalForm());
    }

    setResultConverter(button -> button == ButtonType.OK ? createResult() : null);
  }

  private void addMariaDbSpecifics() {
    port.setValue(3306);
  }

  private void addMysqlSpecifics() {
    port.setValue(3306);
    urlTemplate = "jdbc:mysql://{server}:{port}/{database}";
  }

  private void addDerbySpecifics() {
    port.setValue(1527);
    urlTemplate = "jdbc:derby://{server}:{port}/{database}";
  }

  private void addFirebirdSpecifics() {
    port.setValue(3050);
    urlTemplate = "jdbc:firebirdsql://{server}:{port}/{database}";
  }

  private void addSqlServerSpecifics() {

    initialOptionsDelimiter = ";";
    subsequentOptionsDelimiter = ";";
    port.setValue(1433);
    urlTemplate = "jdbc:sqlserver://{server}:{port};databaseName={database}";
    HBox firstRow = new HBox();
    firstRow.setSpacing(15);
    optionsBox.getChildren().add(firstRow);
    CheckboxOption readOnlyCbo = new CheckboxOption("Read-only");
    readOnlyCbo.setOnAction(a -> {
      if (readOnlyCbo.isSelected()) {
        options.add("applicationIntent=ReadOnly");
      } else {
        options.remove("applicationIntent=ReadOnly");
      }
      updateUrl();
    });
    firstRow.getChildren().add(readOnlyCbo);

    CheckboxOption integratedSecurity = new CheckboxOption("Integrated security");
    integratedSecurity.setOnAction(a -> {
      if (integratedSecurity.isSelected()) {
        options.add("integratedsecurity=true");
      } else {
        options.remove("integratedsecurity=true");
      }
      updateUrl();
    });
    firstRow.getChildren().add(integratedSecurity);

    HBox secondRow = new HBox();
    secondRow.setSpacing(15);
    optionsBox.getChildren().add(secondRow);
    CheckboxOption encrypt = new CheckboxOption("encrypt");
    encrypt.setOnAction(a -> {
      if (encrypt.isSelected()) {
        options.add("encrypt=true");
      } else {
        options.remove("encrypt=true");
      }
      updateUrl();
    });
    secondRow.getChildren().add(encrypt);

    CheckboxOption trustServerCertificate = new CheckboxOption("Trust Server Certificate");
    trustServerCertificate.setOnAction(a -> {
      if (trustServerCertificate.isSelected()) {
        options.add("trustServerCertificate=true");
      } else {
        options.remove("trustServerCertificate=true");
      }
      updateUrl();
    });
    secondRow.getChildren().add(trustServerCertificate);
  }

  private void addPostgresSpecifics() {
    port.setValue(5432);
    urlTemplate = "jdbc:postgresql://{server}:{port}/{database}";
    CheckboxOption readOnlyCbo = new CheckboxOption("Read-only");
    readOnlyCbo.setOnAction(a -> {
      if (readOnlyCbo.isSelected()) {
        options.add("readOnly=true");
      } else {
        options.remove("readOnly=true");
      }
      updateUrl();
    });
    optionsBox.getChildren().add(readOnlyCbo);
    ComboboxOption sslMode = new ComboboxOption("sslmode", "", "disable", "allow", "prefer", "require", "verify-ca ", "verify-full");
    sslMode.setOnAction(a -> {
      options.removeIf(p -> p.startsWith("sslmode="));
      if (!"".equals(sslMode.getValue())) {
        options.add("sslmode=" + sslMode.getValue());
      }
      updateUrl();
    });
    optionsBox.getChildren().add(sslMode);
  }

  private void addH2Specifics() {
    urlTemplate = "jdbc:h2:{connectMethod}://{server}:{port}/{database}";
    connectMethods.setDisable(false);
    connectMethods.getItems().add("tcp");
    connectMethods.getItems().add("mem");
    connectMethods.getItems().add("file");
    connectMethods.setOnAction(a -> {
      String value = connectMethods.getValue();
      if ("tcp".equals(value)) {
        urlTemplate = "jdbc:h2:{connectMethod}://{server}:{port}/{database}";
        port.setDisable(false);
        port.setValue(9092);
        server.setText("localhost");
        server.setDisable(false);
      } else {
        if ("mem".equals(value)) {
          urlTemplate = "jdbc:h2:{connectMethod}:{database}";
        } else if ("file".equals(value)) {
          urlTemplate = "jdbc:h2:{connectMethod}:{path}/{database}";
          Button browseButton = new Button("...");
          connectionMethodsBox.getChildren().add(browseButton);
          browseButton.setOnAction(action -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choose H2 database file");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("h2 database file", "*.mv.db", "*.h2.db"));
            File dbFile = fc.showOpenDialog(gui.getStage());
            if (dbFile != null && dbFile.exists()) {
              String path = dbFile.getParent();
              String dbName = dbFile.getName();
              database.setText(dbName.substring(0, dbName.length() - ".mv.db".length()));
              urlTemplate = urlTemplate.replace("{path}", path);
              updateUrl();
            }
          });
        }
        port.setText("");
        port.setDisable(true);
        server.setText("");
        server.setDisable(true);
      }
      if (urlTemplate != null && value != null) {
        urlTemplate = urlTemplate.replace("{connectMethod}", value);
        updateUrl();
      }
    });
    connectMethods.setValue("tcp");
    Label readOnly = new Label("Read-only");
    CheckBox cb = new CheckBox();
    cb.setOnAction(a -> {
      if (cb.isSelected()) {
        options.add("ACCESS_MODE_DATA=r");
      } else {
        options.remove("ACCESS_MODE_DATA=r");
      }
      updateUrl();
    });
    HBox hBox = new HBox(cb, readOnly);
    hBox.setSpacing(5);
    optionsBox.getChildren().add(hBox);
  }

  private void addSqlLiteSpecifics() {
    port.clear();
    server.clear();
    urlTemplate = "jdbc:sqlite:{database}";
  }

  private void addOracleSpecifics() {
    urlTemplate = "jdbc:oracle:thin:@{server}:{port}:{database}";
    port.setValue(1521);
  }

  private void addDefaultsForDriver(ActionEvent actionEvent) {
    options.clear();
    optionsBox.getChildren().clear();
    connectionMethodsBox.getChildren().clear();
    connectionMethodsBox.getChildren().add(connectMethods);
    connectMethods.setDisable(true);
    connectMethods.getItems().clear();
    port.setDisable(false);
    server.setDisable(false);

    String driverName = driver.getValue();
    Driver driver = Driver.fromClass(driverName);
    server.setText("localhost");
    database.setText("mydatabase");
    initialOptionsDelimiter = "?";
    subsequentOptionsDelimiter = "?";
    dependency.setText(driver.getDependency());
    switch (driver) {
      case POSTGRES -> addPostgresSpecifics();
      case SQLSERVER -> addSqlServerSpecifics();
      case MARIADB -> addMariaDbSpecifics();
      case MYSQL -> addMysqlSpecifics();
      case DERBY -> addDerbySpecifics();
      case FIREBIRD -> addFirebirdSpecifics();
      case H2 -> addH2Specifics();
      case SQLLITE -> addSqlLiteSpecifics();
      case ORACLE -> addOracleSpecifics();
      default -> {
        server.setText("unknown");
        port.setValue(1025);
        database.setText("");
      }
    }
    updateUrl();
    getDialogPane().getScene().getWindow().sizeToScene();
  }

  private void updateUrl() {
    String urlString = urlTemplate
        .replace("{server}", server.getText())
        .replace("{port}", port.getText())
        .replace("{database}", database.getText());
    if (options.size() > 0) {
      urlString += initialOptionsDelimiter + String.join(subsequentOptionsDelimiter, options);
    }
    urlString = urlString.replace('\\', '/');
    url.setText(urlString);
  }

  private ConnectionInfo createResult() {
    ConnectionInfo ci = new ConnectionInfo();
    ci.setDriver(driver.getValue());
    ci.setUrl(url.getText());
    return ci;
  }
}
