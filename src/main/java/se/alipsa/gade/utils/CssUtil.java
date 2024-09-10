package se.alipsa.gade.utils;

import javafx.css.CssParser;
import javafx.css.Rule;
import javafx.css.Stylesheet;
import javafx.css.converter.ColorConverter;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class CssUtil {

  private static Logger log = LogManager.getLogger();

  public static Color parseColor(String property, Color... defaultColor) {
    CssParser parser = new CssParser();
    Color fallback = defaultColor.length > 0 ? defaultColor[0] : Color.TRANSPARENT;
    try {
      URL styleSheetUrl = new URI(Gade.instance().getStyleSheets().get(0)).toURL();
      log.info("Using stylesheet {}", styleSheetUrl);
      Stylesheet css = parser.parse(styleSheetUrl);
      final Rule rootRule = css.getRules().get(0); // .root
      return rootRule.getDeclarations().stream()
          .filter(d -> d.getProperty().equals(property))
          .findFirst()
          .map(d -> ColorConverter.getInstance().convert(d.getParsedValue(), null))
          .orElse(fallback);
    } catch (IOException | URISyntaxException ex) {
      log.warn(ex);
    }
    return fallback;
  }

  public static String toRgbString(Paint color) {
    if (color instanceof Color) {
      return toRgbString((Color)color);
    } else {
      return color.getClass().getName();
    }
  }
  public static String toRgbString(Color color) {
    return scale(color.getRed()) + ", " + scale(color.getGreen()) + ", " + scale(color.getBlue()) + ", " + scale(color.getOpacity());
  }

  public static String toRgbString(String val) {
    return "null".equals(val) ? val : toRgbString(Color.valueOf(val));
  }

  private static int scale(double val) {
    return (int)Math.round (val * 255);
  }
}
