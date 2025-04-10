package se.alipsa.gade.splash;

import java.util.List;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import se.alipsa.gade.utils.FileUtils;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Shows a "splash" screen while the main Application is starting up.
 * The duration of the display time can be modified by passing the number of seconds as an argument
 * to the main method
 */
public class SplashScreen extends Application {

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage primaryStage) {
    List<String> args = getParameters().getRaw();
    double timeout = 4;
    if (!args.isEmpty()) {
      timeout = Double.parseDouble(args.getFirst());
    }
    BorderPane root = new BorderPane();
    Label altToImage = new Label(" Loading Gade, please wait...");
    root.setTop(altToImage);

    Image logo = new Image(Objects.requireNonNull(FileUtils.getResourceUrl("image/logo.png")).toExternalForm());
    ImageView imageView = new ImageView(logo);
    root.setCenter(imageView);

    primaryStage.setTitle("Gade, a Groovy analytics IDE");
    primaryStage.getIcons().add(new Image(Objects.requireNonNull(FileUtils.getResourceUrl("image/logo.png")).toExternalForm()));
    Scene scene = new Scene(root);
    primaryStage.setScene(scene);
    primaryStage.show();

    PauseTransition delay = new PauseTransition(Duration.seconds(timeout));
    delay.setOnFinished( event -> {
      primaryStage.close();
      Platform.exit();
      // Allow some time before calling system exist so stop() can be used to do stuff if neeed
      Timer timer = new Timer();
      TimerTask task = new TimerTask() {
        public void run() {
          System.exit(0);
        }
      };
      timer.schedule(task, 250);
    } );
    delay.play();
  }
}
