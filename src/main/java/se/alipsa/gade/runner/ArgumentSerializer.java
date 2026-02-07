package se.alipsa.gade.runner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles serialization and deserialization of arguments for remote GUI operations.
 * Converts complex objects (Matrix, Charts, Images) to JSON-compatible maps for
 * transport over the socket protocol.
 * <p>
 * This class uses reflection to check for optional dependencies (Matrix, JavaFX)
 * to work in both the main Gade process (with all dependencies) and external
 * runner processes (minimal classpath).
 * <p>
 * Note: This class uses System.err for logging instead of Log4j to avoid classpath
 * conflicts when running in external runtime processes (Gradle, Maven, etc.).
 */
public class ArgumentSerializer {

  // Check if optional dependencies are available
  private static final boolean MATRIX_AVAILABLE = isClassAvailable("se.alipsa.matrix.core.Matrix");
  private static final boolean JAVAFX_AVAILABLE = isClassAvailable("javafx.scene.Node");
  private static final boolean AWT_AVAILABLE = isClassAvailable("java.awt.image.BufferedImage");
  private static final boolean CONNECTION_INFO_AVAILABLE =
      isClassAvailable("se.alipsa.groovy.datautil.ConnectionInfo");

  private static boolean isClassAvailable(String className) {
    try {
      Class.forName(className);
      return true;
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      return false;
    }
  }

  /**
   * Serialize an argument for remote method invocation.
   *
   * @param arg The argument to serialize
   * @return Serialized form (primitives, maps, or strings)
   */
  public static Object serialize(Object arg) {
    if (arg == null) {
      return null;
    }

    // Primitives and simple types pass through
    if (arg instanceof String || arg instanceof Number || arg instanceof Boolean) {
      return arg;
    }

    // File - serialize as path (always available)
    if (arg instanceof File) {
      return serializeFile((File) arg);
    }

    // Collections and Maps pass through (protocol layer will handle)
    if (arg instanceof Collection || arg instanceof Map) {
      return arg;
    }

    // Check for optional types using reflection to avoid ClassNotFoundException
    String className = arg.getClass().getName();

    // Matrix - use existing typed CSV
    if (MATRIX_AVAILABLE && className.equals("se.alipsa.matrix.core.Matrix")) {
      return MatrixSerializer.serialize(arg);
    }

    // ConnectionInfo - use JSON serialization
    if (CONNECTION_INFO_AVAILABLE
        && className.equals("se.alipsa.groovy.datautil.ConnectionInfo")) {
      return ConnectionInfoSerializer.serialize(arg);
    }

    // JavaFX Node - render as PNG
    if (JAVAFX_AVAILABLE && className.startsWith("javafx.scene.")) {
      if (isJavaFXNode(arg)) {
        return JavaFXSerializer.serializeNode(arg);
      }
      if (isJavaFXImage(arg)) {
        return JavaFXSerializer.serializeImage(arg);
      }
    }

    // BufferedImage - encode as PNG
    if (AWT_AVAILABLE && className.equals("java.awt.image.BufferedImage")) {
      return ImageSerializer.serializeBufferedImage(arg);
    }

    // Fallback: convert to string
    System.err.println("ArgumentSerializer: Serializing unknown type as string: " + className);
    return arg.toString();
  }

  private static boolean isJavaFXNode(Object obj) {
    try {
      Class<?> nodeClass = Class.forName("javafx.scene.Node");
      return nodeClass.isInstance(obj);
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private static boolean isJavaFXImage(Object obj) {
    try {
      Class<?> imageClass = Class.forName("javafx.scene.image.Image");
      return imageClass.isInstance(obj);
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /**
   * Deserialize an argument received from remote method invocation.
   *
   * @param arg The serialized argument
   * @return Deserialized object
   */
  public static Object deserialize(Object arg) {
    if (arg == null) {
      return null;
    }

    // If it's a map with _type, try to deserialize
    if (arg instanceof Map) {
      Map<String, Object> map = (Map<String, Object>) arg;
      String type = (String) map.get("_type");

      if (type != null) {
        switch (type) {
          case "se.alipsa.matrix.core.Matrix":
            if (MATRIX_AVAILABLE) {
              return MatrixSerializer.deserialize(map);
            }
            break;
          case "se.alipsa.groovy.datautil.ConnectionInfo":
            if (CONNECTION_INFO_AVAILABLE) {
              return ConnectionInfoSerializer.deserialize(map);
            }
            break;
          case "image":
            if (JAVAFX_AVAILABLE) {
              return JavaFXSerializer.deserializeImage(map);
            }
            break;
          case "java.io.File":
            return deserializeFile(map);
          default:
            System.err.println("ArgumentSerializer: Unknown type for deserialization: " + type);
        }
      }
    }

    // Everything else passes through
    return arg;
  }

  // === File Serialization ===

  private static Map<String, Object> serializeFile(File file) {
    Map<String, Object> map = new HashMap<>();
    map.put("_type", "java.io.File");
    map.put("path", file.getAbsolutePath());
    return map;
  }

  private static File deserializeFile(Map<String, Object> map) {
    String path = (String) map.get("path");
    if (path == null) {
      throw new IllegalArgumentException("File map missing 'path' field");
    }
    return new File(path);
  }

  // === Matrix Serialization (lazy-loaded) ===

  static class MatrixSerializer {
    static Object serialize(Object matrixObj) {
      se.alipsa.matrix.core.Matrix matrix = (se.alipsa.matrix.core.Matrix) matrixObj;
      Map<String, Object> map = new HashMap<>();
      map.put("_type", "se.alipsa.matrix.core.Matrix");
      map.put("csv", matrix.toCsvString());
      return map;
    }

    static Object deserialize(Map<String, Object> map) {
      String csv = (String) map.get("csv");
      if (csv == null) {
        throw new IllegalArgumentException("Matrix map missing 'csv' field");
      }
      return se.alipsa.matrix.core.Matrix.builder().csvString(csv).build();
    }
  }

  // === ConnectionInfo Serialization (lazy-loaded) ===

  static class ConnectionInfoSerializer {
    static Object serialize(Object ciObj) {
      se.alipsa.groovy.datautil.ConnectionInfo ci =
          (se.alipsa.groovy.datautil.ConnectionInfo) ciObj;
      Map<String, Object> map = new HashMap<>();
      map.put("_type", "se.alipsa.groovy.datautil.ConnectionInfo");
      map.put("json", ci.asJson(false));
      return map;
    }

    @SuppressWarnings("unchecked")
    static Object deserialize(Map<String, Object> map) {
      String json = (String) map.get("json");
      if (json == null) {
        throw new IllegalArgumentException("ConnectionInfo map missing 'json' field");
      }
      Map<String, Object> parsed = (Map<String, Object>) new groovy.json.JsonSlurper().parseText(json);
      return new se.alipsa.groovy.datautil.ConnectionInfo(
          (String) parsed.get("name"),
          (String) parsed.get("dependency"),
          (String) parsed.get("driver"),
          (String) parsed.get("url"),
          (String) parsed.get("user"),
          (String) parsed.get("password")
      );
    }
  }

  // === JavaFX Serialization (lazy-loaded) ===

  static class JavaFXSerializer {
    static Object serializeNode(Object nodeObj) {
      try {
        javafx.scene.Node node = (javafx.scene.Node) nodeObj;
        javafx.scene.image.WritableImage writableImage = node.snapshot(null, null);
        java.awt.image.BufferedImage buffered = javafx.embed.swing.SwingFXUtils.fromFXImage(writableImage, null);
        return ImageSerializer.serializeBufferedImage(buffered);
      } catch (Exception e) {
        System.err.println("ArgumentSerializer: Failed to serialize Node");
        e.printStackTrace(System.err);
        throw new RuntimeException("Failed to serialize Node: " + e.getMessage(), e);
      }
    }

    static Object serializeImage(Object imageObj) {
      try {
        javafx.scene.image.Image image = (javafx.scene.image.Image) imageObj;
        java.awt.image.BufferedImage buffered = javafx.embed.swing.SwingFXUtils.fromFXImage(image, null);
        return ImageSerializer.serializeBufferedImage(buffered);
      } catch (Exception e) {
        System.err.println("ArgumentSerializer: Failed to serialize Image");
        e.printStackTrace(System.err);
        throw new RuntimeException("Failed to serialize Image: " + e.getMessage(), e);
      }
    }

    static Object deserializeImage(Map<String, Object> map) {
      try {
        String base64 = (String) map.get("data");
        if (base64 == null) {
          throw new IllegalArgumentException("Image map missing 'data' field");
        }

        byte[] imageBytes = Base64.getDecoder().decode(base64);
        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
        java.awt.image.BufferedImage buffered = javax.imageio.ImageIO.read(bais);

        return javafx.embed.swing.SwingFXUtils.toFXImage(buffered, null);
      } catch (IOException e) {
        System.err.println("ArgumentSerializer: Failed to deserialize Image");
        e.printStackTrace(System.err);
        throw new RuntimeException("Failed to deserialize Image: " + e.getMessage(), e);
      }
    }
  }

  // === Image Serialization (lazy-loaded) ===

  static class ImageSerializer {
    static Object serializeBufferedImage(Object imageObj) {
      try {
        java.awt.image.BufferedImage image = (java.awt.image.BufferedImage) imageObj;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", baos);
        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

        Map<String, Object> map = new HashMap<>();
        map.put("_type", "image");
        map.put("data", base64);
        map.put("width", image.getWidth());
        map.put("height", image.getHeight());
        return map;
      } catch (IOException e) {
        System.err.println("ArgumentSerializer: Failed to serialize BufferedImage");
        e.printStackTrace(System.err);
        throw new RuntimeException("Failed to serialize BufferedImage: " + e.getMessage(), e);
      }
    }
  }
}
