package se.alipsa.gade.splash;

import javafx.application.Platform;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import javax.imageio.ImageIO;

/**
 * AWT-based splash screen that can be shown before JavaFX initializes.
 * Create an instance in {@code main()} to show the splash immediately,
 * then call {@link #close(Runnable)} from any thread once the main stage
 * is ready. The splash will remain visible for at least
 * {@code minDisplaySeconds} before closing. The {@code onClosed} callback
 * is always invoked on the JavaFX Application Thread.
 */
public class SplashScreen {

  private final JWindow window;
  private final long shownAtMillis;
  private final double minDisplaySeconds;

  public SplashScreen(double minDisplaySeconds) {
    this.minDisplaySeconds = minDisplaySeconds;

    // Build and show the window entirely on the EDT
    JWindow[] holder = new JWindow[1];
    try {
      SwingUtilities.invokeAndWait(() -> {
        JWindow w = new JWindow();

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        JLabel label = new JLabel(" Loading Gade, please wait...");
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 14f));
        content.add(label, BorderLayout.NORTH);

        try (InputStream is = Objects.requireNonNull(
            SplashScreen.class.getResourceAsStream("/image/logo.png"))) {
          BufferedImage logo = ImageIO.read(is);
          JLabel imageLabel = new JLabel(new ImageIcon(logo));
          content.add(imageLabel, BorderLayout.CENTER);
        } catch (IOException e) {
          // If the image can't be loaded, just show the text
        }

        w.setContentPane(content);
        w.pack();
        w.setLocationRelativeTo(null);
        w.setAlwaysOnTop(true);
        w.setVisible(true);
        holder[0] = w;
      });
    } catch (InterruptedException | InvocationTargetException e) {
      // If EDT setup fails, leave window null — close() will just run the callback
    }
    this.window = holder[0];
    this.shownAtMillis = System.currentTimeMillis();
  }

  public void close(Runnable onClosed) {
    long elapsed = System.currentTimeMillis() - shownAtMillis;
    long minMillis = (long) (minDisplaySeconds * 1000);
    long remaining = minMillis - elapsed;

    Runnable dispose = () -> {
      if (window != null) {
        SwingUtilities.invokeLater(() -> {
          window.setVisible(false);
          window.dispose();
        });
      }
      // Always run the callback on the JavaFX Application Thread
      Platform.runLater(onClosed);
    };

    if (remaining <= 0) {
      dispose.run();
    } else {
      // Swing Timer fires on the EDT; dispose handles thread-safety for both toolkits
      Timer timer = new Timer((int) remaining, e -> dispose.run());
      timer.setRepeats(false);
      timer.start();
    }
  }
}
