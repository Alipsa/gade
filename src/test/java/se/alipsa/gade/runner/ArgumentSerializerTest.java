package se.alipsa.gade.runner;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.alipsa.matrix.core.Matrix;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import se.alipsa.groovy.datautil.ConnectionInfo;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ArgumentSerializerTest {

  @BeforeAll
  static void initJavaFX() {
    // Initialize JavaFX toolkit for headless testing
    try {
      new javafx.embed.swing.JFXPanel(); // Forces JavaFX platform initialization
    } catch (Exception e) {
      // Already initialized or headless mode active
    }
  }

  @Test
  void serializeNull() {
    Object result = ArgumentSerializer.serialize(null);
    assertNull(result, "Null should serialize to null");
  }

  @Test
  void deserializeNull() {
    Object result = ArgumentSerializer.deserialize(null);
    assertNull(result, "Null should deserialize to null");
  }

  @Test
  void serializePrimitiveString() {
    String input = "Hello, World!";
    Object result = ArgumentSerializer.serialize(input);
    assertEquals(input, result, "String should pass through unchanged");
  }

  @Test
  void serializePrimitiveNumber() {
    Integer input = 42;
    Object result = ArgumentSerializer.serialize(input);
    assertEquals(input, result, "Integer should pass through unchanged");

    Double inputDouble = 3.14159;
    Object resultDouble = ArgumentSerializer.serialize(inputDouble);
    assertEquals(inputDouble, resultDouble, "Double should pass through unchanged");
  }

  @Test
  void serializePrimitiveBoolean() {
    Boolean input = true;
    Object result = ArgumentSerializer.serialize(input);
    assertEquals(input, result, "Boolean should pass through unchanged");
  }

  @Test
  void serializeCollection() {
    List<String> input = Arrays.asList("a", "b", "c");
    Object result = ArgumentSerializer.serialize(input);
    assertEquals(input, result, "Collection should pass through unchanged");
  }

  @Test
  void serializeMap() {
    Map<String, Object> input = new HashMap<>();
    input.put("key", "value");
    input.put("number", 123);
    Object result = ArgumentSerializer.serialize(input);
    assertEquals(input, result, "Map should pass through unchanged");
  }

  @Test
  void serializeUnknownType() {
    Object input = new Object() {
      @Override
      public String toString() {
        return "CustomObject";
      }
    };
    Object result = ArgumentSerializer.serialize(input);
    assertEquals("CustomObject", result, "Unknown type should convert to string");
  }

  @Test
  void serializeMatrixRoundTrip() {
    // Create a Matrix with typed data (data includes header row)
    Matrix matrix = Matrix.builder()
        .matrixName("TestMatrix")
        .columnNames("Name", "Age", "Score")
        .rows(Arrays.asList(
            Arrays.asList("Alice", 25, 95.5),
            Arrays.asList("Bob", 30, 87.3)
        ))
        .types(Arrays.asList(String.class, Integer.class, Double.class))
        .build();

    // Serialize
    Object serialized = ArgumentSerializer.serialize(matrix);
    assertNotNull(serialized, "Serialized matrix should not be null");
    assertTrue(serialized instanceof Map, "Serialized matrix should be a Map");

    Map<String, Object> map = (Map<String, Object>) serialized;
    assertEquals("se.alipsa.matrix.core.Matrix", map.get("_type"), "Type should be Matrix");
    assertTrue(map.containsKey("csv"), "Should contain CSV data");

    String csv = (String) map.get("csv");
    assertNotNull(csv, "CSV should not be null");
    assertTrue(csv.contains("#name: TestMatrix"), "CSV should contain matrix name");
    assertTrue(csv.contains("#types:"), "CSV should contain types");
    assertTrue(csv.contains("Name,Age,Score"), "CSV should contain header");
    assertTrue(csv.contains("Alice,25,95.5"), "CSV should contain data row 1");
    assertTrue(csv.contains("Bob,30,87.3"), "CSV should contain data row 2");

    // Deserialize
    Object deserialized = ArgumentSerializer.deserialize(serialized);
    assertNotNull(deserialized, "Deserialized object should not be null");
    assertTrue(deserialized instanceof Matrix, "Deserialized object should be Matrix");

    Matrix resultMatrix = (Matrix) deserialized;
    assertEquals("TestMatrix", resultMatrix.getMatrixName(), "Matrix name should match");
    assertEquals(3, resultMatrix.columnCount(), "Should have 3 columns");
    assertEquals(2, resultMatrix.rowCount(), "Should have 2 data rows (excluding header)");
    assertEquals("Alice", resultMatrix.getAt(0, "Name"), "First row name should match");
    assertEquals(Integer.valueOf(25), resultMatrix.getAt(0, "Age"), "First row age should match");
  }

  @Test
  void deserializeMatrixMissingCsvField() {
    Map<String, Object> map = new HashMap<>();
    map.put("_type", "se.alipsa.matrix.core.Matrix");
    // Missing "csv" field

    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> ArgumentSerializer.deserialize(map),
        "Should throw when CSV field is missing"
    );
    assertTrue(ex.getMessage().contains("csv"), "Error message should mention missing CSV");
  }

  @Test
  void serializeBufferedImage() throws Exception {
    // Create a simple BufferedImage
    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D g = image.createGraphics();
    g.setColor(java.awt.Color.BLUE);
    g.fillRect(0, 0, 100, 100);
    g.dispose();

    // Serialize
    Object serialized = ArgumentSerializer.serialize(image);
    assertNotNull(serialized, "Serialized image should not be null");
    assertTrue(serialized instanceof Map, "Serialized image should be a Map");

    Map<String, Object> map = (Map<String, Object>) serialized;
    assertEquals("image", map.get("_type"), "Type should be image");
    assertTrue(map.containsKey("data"), "Should contain base64 data");
    assertEquals(100, map.get("width"), "Width should match");
    assertEquals(100, map.get("height"), "Height should match");

    // Verify base64 data is valid
    String base64 = (String) map.get("data");
    assertNotNull(base64, "Base64 data should not be null");
    assertTrue(base64.length() > 0, "Base64 data should not be empty");
  }

  @Test
  void serializeJavaFXImage() throws Exception {
    // Create a simple JavaFX Image
    WritableImage fxImage = new WritableImage(50, 50);
    for (int y = 0; y < 50; y++) {
      for (int x = 0; x < 50; x++) {
        fxImage.getPixelWriter().setColor(x, y, Color.RED);
      }
    }

    // Serialize
    Object serialized = ArgumentSerializer.serialize(fxImage);
    assertNotNull(serialized, "Serialized FX image should not be null");
    assertTrue(serialized instanceof Map, "Serialized FX image should be a Map");

    Map<String, Object> map = (Map<String, Object>) serialized;
    assertEquals("image", map.get("_type"), "Type should be image");
    assertTrue(map.containsKey("data"), "Should contain base64 data");
    assertEquals(50, map.get("width"), "Width should match");
    assertEquals(50, map.get("height"), "Height should match");
  }

  @Test
  void deserializeImageToFx() throws Exception {
    // Create a valid PNG image
    BufferedImage buffered = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D g = buffered.createGraphics();
    g.setColor(java.awt.Color.GREEN);
    g.fillRect(0, 0, 30, 30);
    g.dispose();

    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    ImageIO.write(buffered, "png", baos);
    String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

    // Create image map
    Map<String, Object> map = new HashMap<>();
    map.put("_type", "image");
    map.put("data", base64);
    map.put("width", 30);
    map.put("height", 30);

    // Deserialize
    Object deserialized = ArgumentSerializer.deserialize(map);
    assertNotNull(deserialized, "Deserialized image should not be null");
    assertTrue(deserialized instanceof Image, "Deserialized object should be JavaFX Image");

    Image fxImage = (Image) deserialized;
    assertEquals(30, (int) fxImage.getWidth(), "Width should match");
    assertEquals(30, (int) fxImage.getHeight(), "Height should match");
  }

  @Test
  void deserializeImageMissingDataField() {
    Map<String, Object> map = new HashMap<>();
    map.put("_type", "image");
    map.put("width", 100);
    map.put("height", 100);
    // Missing "data" field

    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> ArgumentSerializer.deserialize(map),
        "Should throw when data field is missing"
    );
    assertTrue(ex.getMessage().contains("data"), "Error message should mention missing data");
  }

  @Test
  void serializeJavaFXNode() throws Exception {
    // Create a simple JavaFX Node (Rectangle in a Pane)
    Rectangle rect = new Rectangle(20, 20, Color.YELLOW);
    Pane pane = new Pane(rect);
    pane.setPrefSize(40, 40);

    // Serialize on FX application thread
    final Object[] serialized = new Object[1];
    final Exception[] exception = new Exception[1];

    javafx.application.Platform.runLater(() -> {
      try {
        serialized[0] = ArgumentSerializer.serialize(pane);
      } catch (Exception e) {
        exception[0] = e;
      }
    });

    // Wait for FX thread to complete (simple busy wait for test)
    Thread.sleep(500);

    if (exception[0] != null) {
      throw exception[0];
    }

    assertNotNull(serialized[0], "Serialized node should not be null");
    assertTrue(serialized[0] instanceof Map, "Serialized node should be a Map");

    Map<String, Object> map = (Map<String, Object>) serialized[0];
    assertEquals("image", map.get("_type"), "Type should be image (Node rendered to image)");
    assertTrue(map.containsKey("data"), "Should contain base64 data");
  }

  @Test
  void deserializeUnknownType() {
    Map<String, Object> map = new HashMap<>();
    map.put("_type", "unknown.Type");
    map.put("someField", "someValue");

    // Should pass through unchanged when type is unknown
    Object result = ArgumentSerializer.deserialize(map);
    assertEquals(map, result, "Unknown type should pass through unchanged");
  }

  @Test
  void deserializePlainMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("key1", "value1");
    map.put("key2", 123);
    // No _type field

    // Should pass through unchanged
    Object result = ArgumentSerializer.deserialize(map);
    assertEquals(map, result, "Plain map should pass through unchanged");
  }

  @Test
  void deserializePrimitives() {
    assertEquals("test", ArgumentSerializer.deserialize("test"), "String should pass through");
    assertEquals(42, ArgumentSerializer.deserialize(42), "Integer should pass through");
    assertEquals(3.14, ArgumentSerializer.deserialize(3.14), "Double should pass through");
    assertEquals(true, ArgumentSerializer.deserialize(true), "Boolean should pass through");
  }

  @Test
  void serializeFileRoundTrip() {
    // Create a File object with an absolute path
    File file = new File("/tmp/test-file.txt");

    // Serialize
    Object serialized = ArgumentSerializer.serialize(file);
    assertNotNull(serialized, "Serialized file should not be null");
    assertTrue(serialized instanceof Map, "Serialized file should be a Map");

    Map<String, Object> map = (Map<String, Object>) serialized;
    assertEquals("java.io.File", map.get("_type"), "Type should be java.io.File");
    assertTrue(map.containsKey("path"), "Should contain path field");
    assertEquals(file.getAbsolutePath(), map.get("path"), "Path should match absolute path");

    // Deserialize
    Object deserialized = ArgumentSerializer.deserialize(serialized);
    assertNotNull(deserialized, "Deserialized object should not be null");
    assertTrue(deserialized instanceof File, "Deserialized object should be File");

    File resultFile = (File) deserialized;
    assertEquals(file.getAbsolutePath(), resultFile.getAbsolutePath(), "File paths should match");
  }

  @Test
  void deserializeFileMissingPathField() {
    Map<String, Object> map = new HashMap<>();
    map.put("_type", "java.io.File");
    // Missing "path" field

    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> ArgumentSerializer.deserialize(map),
        "Should throw when path field is missing"
    );
    assertTrue(ex.getMessage().contains("path"), "Error message should mention missing path");
  }

  // === ConnectionInfo Serialization Tests ===

  @Test
  void serializeConnectionInfoRoundTrip() {
    ConnectionInfo ci = new ConnectionInfo(
        "testDb", "com.h2database:h2:2.1.214", "org.h2.Driver",
        "jdbc:h2:mem:test", "sa", "secret");

    // Serialize
    Object serialized = ArgumentSerializer.serialize(ci);
    assertNotNull(serialized, "Serialized ConnectionInfo should not be null");
    assertTrue(serialized instanceof Map, "Serialized ConnectionInfo should be a Map");

    Map<String, Object> map = (Map<String, Object>) serialized;
    assertEquals("se.alipsa.groovy.datautil.ConnectionInfo", map.get("_type"),
        "Type should be ConnectionInfo");
    assertTrue(map.containsKey("json"), "Should contain json data");

    String json = (String) map.get("json");
    assertNotNull(json, "JSON should not be null");
    assertTrue(json.contains("testDb"), "JSON should contain connection name");
    assertTrue(json.contains("org.h2.Driver"), "JSON should contain driver");

    // Deserialize
    Object deserialized = ArgumentSerializer.deserialize(serialized);
    assertNotNull(deserialized, "Deserialized object should not be null");
    assertTrue(deserialized instanceof ConnectionInfo,
        "Deserialized object should be ConnectionInfo");

    ConnectionInfo result = (ConnectionInfo) deserialized;
    assertEquals("testDb", result.getName(), "Name should match");
    assertEquals("com.h2database:h2:2.1.214", result.getDependency(), "Dependency should match");
    assertEquals("org.h2.Driver", result.getDriver(), "Driver should match");
    assertEquals("jdbc:h2:mem:test", result.getUrl(), "URL should match");
    assertEquals("sa", result.getUser(), "User should match");
    assertEquals("secret", result.getPassword(), "Password should match");
  }

  @Test
  void serializeConnectionInfoWithNullPassword() {
    ConnectionInfo ci = new ConnectionInfo(
        "noPwd", "", "org.h2.Driver",
        "jdbc:h2:mem:test", "sa", null);

    // Serialize
    Object serialized = ArgumentSerializer.serialize(ci);
    assertNotNull(serialized);

    // Deserialize
    Object deserialized = ArgumentSerializer.deserialize(serialized);
    assertTrue(deserialized instanceof ConnectionInfo);

    ConnectionInfo result = (ConnectionInfo) deserialized;
    assertEquals("noPwd", result.getName(), "Name should match");
    assertNull(result.getPassword(), "Password should be null");
  }

  @Test
  void deserializeConnectionInfoMissingJsonField() {
    Map<String, Object> map = new HashMap<>();
    map.put("_type", "se.alipsa.groovy.datautil.ConnectionInfo");
    // Missing "json" field

    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> ArgumentSerializer.deserialize(map),
        "Should throw when json field is missing"
    );
    assertTrue(ex.getMessage().contains("json"), "Error message should mention missing json");
  }
}
