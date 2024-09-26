package se.alipsa.gade.environment.connections;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import se.alipsa.gade.Gade;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.groovy.datautil.ConnectionInfo;

import java.net.URL;
import java.util.Optional;

import static se.alipsa.gade.Constants.*;
import static se.alipsa.gade.Constants.ICON_HEIGHT;
import static se.alipsa.gade.environment.connections.ConnectionsTab.*;

public class ConnectionDialog extends Dialog<ConnectionInfo> {

  private final TextField name;
  private final TextField dependencyText;
  private final TextField driverText;
  private final TextField urlText;
  private final TextField userText;
  private PasswordField passwordField;
  public ConnectionDialog(ConnectionsTab connectionsTab) {
    HBox topInputPane = new HBox();
    HBox.setHgrow(topInputPane, Priority.ALWAYS);
    HBox middleInputPane = new HBox();
    HBox.setHgrow(middleInputPane, Priority.ALWAYS);
    HBox bottomInputPane = new HBox();
    HBox.setHgrow(bottomInputPane, Priority.ALWAYS);
    HBox buttonInputPane = new HBox();
    HBox.setHgrow(buttonInputPane, Priority.ALWAYS);

    VBox nameBox = new VBox();
    Label nameLabel = new Label("Connection name:");

    name = new TextField();
    name.setPrefWidth(300);
    nameBox.getChildren().addAll(nameLabel, name);
    HBox.setHgrow(nameBox, Priority.SOMETIMES);
    topInputPane.getChildren().add(nameBox);

    VBox userBox = new VBox();
    Label userLabel = new Label("User:");
    String user = connectionsTab.getUser();
    if (user != null) {
      user = connectionsTab.getPrefOrBlank(USER_PREF);
    }
    userText = new TextField();
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
    String dependency = connectionsTab.getDependency();
    if (dependency != null) {
      dependency = connectionsTab.getPrefOrBlank(DEPENDENCY_PREF);
    }
    dependencyText = new TextField(dependency);
    HBox.setHgrow(dependencyBox, Priority.SOMETIMES);
    dependencyBox.getChildren().addAll(dependencyLabel, dependencyText);
    middleInputPane.getChildren().add(dependencyBox);

    VBox driverBox = new VBox();
    Label driverLabel = new Label("Driver:");
    String driver = connectionsTab.getDriver();
    if (driver != null) {
      driver = connectionsTab.getPrefOrBlank(DRIVER_PREF);
    }
    driverText = new TextField(driver);
    HBox.setHgrow(driverBox, Priority.SOMETIMES);
    driverBox.getChildren().addAll(driverLabel, driverText);
    middleInputPane.getChildren().add(driverBox);

    VBox urlBox = new VBox();
    urlBox.setFillWidth(true);
    Label urlLabel = new Label("Url:");
    String url = connectionsTab.getUrl();
    if (url != null) {
      url = connectionsTab.getPrefOrBlank(URL_PREF);
    }
    urlText = new TextField(url);
    urlText.setPrefColumnCount(50);
    HBox urlTextBox = new HBox();
    urlTextBox.getChildren().add(urlText);
    HBox.setHgrow(urlTextBox, Priority.ALWAYS);
    urlBox.getChildren().addAll(urlLabel, urlTextBox);
    bottomInputPane.getChildren().add(urlBox);

    Image wizIMage = new Image("image/wizard.png", ICON_WIDTH, ICON_HEIGHT, true, true);
    ImageView wizImg =  new ImageView(wizIMage);
    Button wizardButton = new Button("Url Wizard", wizImg);
    wizardButton.setOnAction(a -> openUrlWizard(connectionsTab.getGui()));
    wizardButton.setTooltip(new Tooltip("create/update the url using the wizard"));
    buttonInputPane.setAlignment(Pos.CENTER);
    Insets btnInsets = new Insets(5, 10, 5, 10);
    wizardButton.setPadding(btnInsets);
    buttonInputPane.setSpacing(10);
    buttonInputPane.getChildren().addAll(wizardButton);

    setResizable(true);
    String styleSheetPath = connectionsTab.getGui().getPrefs().get(THEME, BRIGHT_THEME);
    URL styleSheetUrl = FileUtils.getResourceUrl(styleSheetPath);
    if (styleSheetUrl != null) {
      getDialogPane().getStylesheets().add(styleSheetUrl.toExternalForm());
    }
    VBox content = new VBox();
    content.setSpacing(5);
    content.getChildren().addAll(topInputPane, middleInputPane, bottomInputPane, buttonInputPane);
    getDialogPane().setContent(content);
    content.setPrefWidth(600);
    getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    setResultConverter(button -> button == ButtonType.OK ? createResult() : null);
  }

  private void openUrlWizard(Gade gui) {

    JdbcUrlWizardDialog dialog = new JdbcUrlWizardDialog(gui);
    Optional<ConnectionInfo> result = dialog.showAndWait();
    if (result.isEmpty()) {
      return;
    }
    ConnectionInfo ci = result.get();
    driverText.setText(ci.getDriver());
    dependencyText.setText(ci.getDependency());
    urlText.setText(ci.getUrl());
  }

  public ConnectionDialog(ConnectionInfo ci, ConnectionsTab connectionsTab) {
    this(connectionsTab);
    name.setText(ci.getName());
    userText.setText(ci.getUser());
    passwordField.setText(ci.getPassword());
    dependencyText.setText(ci.getDependency());
    driverText.setText(ci.getDriver());
    urlText.setText(ci.getUrl());
  }

  private ConnectionInfo createResult() {
    ConnectionInfo ci = new ConnectionInfo();
    ci.setName(name.getText());
    ci.setDriver(driverText.getText());
    ci.setDependency(dependencyText.getText());
    ci.setUser(userText.getText());
    ci.setPassword(passwordField.getText());
    ci.setUrl(urlText.getText());
    return ci;
  }

}
