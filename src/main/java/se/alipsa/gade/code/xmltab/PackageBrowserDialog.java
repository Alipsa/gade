package se.alipsa.gade.code.xmltab;

import static se.alipsa.gade.Constants.FLOWPANE_INSETS;
import static se.alipsa.groovy.resolver.MavenRepoLookup.metaDataUrl;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;
import se.alipsa.gade.Constants;
import se.alipsa.gade.Gade;
import se.alipsa.gade.utils.GuiUtils;
import se.alipsa.groovy.resolver.MavenRepoLookup;

import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;

/*
   --------------------------
   |    top input, action   |
   --------------------------
   |                        |
   |    center textarea     |
   |                        |
   --------------------------
   |    bottom, usage hint  |
   --------------------------
 */
public class PackageBrowserDialog extends Dialog<Void> {

   private final Gade gui;
   private final TextArea textArea = new TextArea();
   private final TextField artifactField;
   private final TextField groupField;
   private final ComboBox<Constants.MavenRepositoryUrl> repoCombo;
   private Stage browserStage = null;
   
   private static final Logger log = LogManager.getLogger(PackageBrowserDialog.class);

   public PackageBrowserDialog(Gade gui) {
      initOwner(gui.getStage());
      setResizable(true);
      getDialogPane().setPrefWidth(800);
      this.gui = gui;
      setTitle("Search for package info");
      getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
      GuiUtils.addStyle(gui, this);
      BorderPane borderPane = new BorderPane();
      getDialogPane().setContent(borderPane);

      HBox topPane = new HBox();
      topPane.setPadding(FLOWPANE_INSETS);
      borderPane.setTop(topPane);

      Label groupLabel = new Label("Group name");
      groupLabel.setPadding(FLOWPANE_INSETS);
      topPane.getChildren().add(groupLabel);

      groupField = new TextField("");
      groupField.setPrefWidth(150);
      groupField.setPadding(FLOWPANE_INSETS);
      topPane.getChildren().add(groupField);

      Label artifactLabel = new Label("Artifact name");
      artifactLabel.setPadding(FLOWPANE_INSETS);
      topPane.getChildren().add(artifactLabel);

      artifactField = new TextField();
      artifactField.setPrefWidth(150);
      artifactField.setPadding(FLOWPANE_INSETS);
      topPane.getChildren().add(artifactField);

      Button searchButton = new Button("Search");
      searchButton.setPadding(FLOWPANE_INSETS);
      searchButton.setOnAction(this::lookupArtifact);
      topPane.getChildren().add(searchButton);

      repoCombo = new ComboBox<>();
      HBox.setMargin(repoCombo, new Insets(0,5,0,10));
      repoCombo.getItems().addAll(Constants.MavenRepositoryUrl.MAVEN_CENTRAL);
      repoCombo.getSelectionModel().select(Constants.MavenRepositoryUrl.MAVEN_CENTRAL);
      repoCombo.setOnAction(e -> {
         if (repoCombo.getSelectionModel().getSelectedItem().equals(Constants.MavenRepositoryUrl.MAVEN_CENTRAL)) {
            groupField.setText("");
         }
      });
      topPane.getChildren().add(repoCombo);

      textArea.setPrefColumnCount(40);
      textArea.setPrefRowCount(8);
      borderPane.setCenter(textArea);

      Label hintLabel = new Label("Hint: copy useful text from the search result before closing the dialog.");
      borderPane.setBottom(hintLabel);

      setOnCloseRequest(eh -> {
         if (browserStage != null) {
            browserStage.close();
         }
         close();
      });
   }

   private void lookupArtifact(ActionEvent actionEvent) {
      String group = groupField.getText().trim();
      String artifact = artifactField.getText().trim();
      Constants.MavenRepositoryUrl mavenRepositoryUrl = repoCombo.getSelectionModel().getSelectedItem();
      String baseUrl = mavenRepositoryUrl.baseUrl;

      try {
         String version = MavenRepoLookup.fetchLatestArtifact(group, artifact, baseUrl).getVersion();

         String sb = "Latest version is:" +
             "\n<dependency>" +
             "\n\t" + "<groupId>" + group + "</groupId>" +
             "\n\t" + "<artifactId>" + artifact + "</artifactId>" +
             "\n\t" + "<version>" + version + "</version>" +
             "\n</dependency>\n\n";

         textArea.setText(sb);

      } catch (RuntimeException e) {
         log.info("Failed to get metadata from {}, opening search browser", metaDataUrl(group, artifact, baseUrl));
         openMavenSearchBrowser();
      }
   }

   private void openMavenSearchBrowser() {
      gui.setWaitCursor();
      WebView browser = new WebView();
      WebEngine webEngine = browser.getEngine();
      BorderPane borderPane = new BorderPane();
      borderPane.setCenter(browser);
      String cssPath = gui.getStyleSheets().get(0);
      webEngine.setUserStyleSheetLocation(cssPath);
      browser.getStylesheets().addAll(gui.getStyleSheets());
      Scene scene = new Scene(borderPane, 1280, 800);
      Platform.runLater(() -> {
         browser.setCursor(Cursor.WAIT);
         scene.setCursor(Cursor.WAIT);
      });
      browserStage = new Stage();
      browserStage.initModality(Modality.NONE);
      browserStage.setTitle("Artifact not found, showing repository search...");
      browserStage.setScene(scene);
      browserStage.sizeToScene();
      browserStage.show();
      String group = groupField.getText().trim();
      String artifact = artifactField.getText().trim();
      String url = "https://mvnrepository.com/search?q=" + group + "+" + artifact;
      webEngine.load(url);
      browserStage.toFront();
      browserStage.requestFocus();
      browserStage.setAlwaysOnTop(false);
      browser.setCursor(Cursor.DEFAULT);
      scene.setCursor(Cursor.DEFAULT);
      gui.setNormalCursor();
   }
}
