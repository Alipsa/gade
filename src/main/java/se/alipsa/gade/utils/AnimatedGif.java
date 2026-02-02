package se.alipsa.gade.utils;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.image.BufferedImage;

public class AnimatedGif extends Animation {

  private static final Logger log = LogManager.getLogger(AnimatedGif.class);

  public AnimatedGif(String filename, double durationMs) {

    GifDecoder d = new GifDecoder();
    d.read(filename);

    Image[] sequence = new Image[d.getFrameCount()];
    for (int i = 0; i < d.getFrameCount(); i++) {

      WritableImage wimg = null;
      BufferedImage bimg = d.getFrame(i);
      sequence[i] = SwingFXUtils.toFXImage(bimg, wimg);

    }
    log.debug("Found {} sequences in the gif", sequence.length);
    super.init(sequence, durationMs);
  }


}
