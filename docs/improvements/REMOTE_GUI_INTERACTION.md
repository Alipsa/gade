# Remote GUI Interaction for External Runtimes

## Problem

Currently, the `io` object (GUI interactions) is blocked in external runtimes (Gradle, Maven, Custom) because they run in separate processes. Users see:

```
UnsupportedOperationException: 'io' is not available in external runtimes;
select the GADE runtime for GUI interactions
```

This prevents users from:
- Displaying plots/charts from external runtime scripts
- Using database connections
- Viewing data tables
- Showing prompts/dialogs

## Solution

Extend the existing JSON-RPC socket protocol to support remote GUI interactions. The runner process sends GUI requests to the main Gade process, which executes them and returns results.

## Architecture

### Multi-Repository Design

Since you maintain **gi-console**, **matrix library**, and **Gade**, we can design a clean layered solution:

```
┌─────────────────────────────────────────────────────────────────┐
│ gi-console (GuiInteraction base library)                       │
│ - RemoteSerializable interface                                  │
│ - RemoteSerializationRegistry (type → deserializer)            │
│ - RemoteSerializationHelper (serialize/deserialize)             │
└─────────────────────────────────────────────────────────────────┘
                                ▲
                                │ depends on
                                │
┌─────────────────────────────────────────────────────────────────┐
│ matrix library (Data structures & charts)                       │
│ - Matrix implements RemoteSerializable                          │
│ - Chart implements RemoteSerializable                           │
│ - MatrixXChart implements RemoteSerializable                    │
│ - MatrixRemoteInit registers deserializers                      │
└─────────────────────────────────────────────────────────────────┘
                                ▲
                                │ depends on
                                │
┌─────────────────────────────────────────────────────────────────┐
│ Gade (IDE application)                                          │
│                                                                  │
│  ┌──────────────────┐              ┌──────────────────┐        │
│  │ Main Process     │    Socket    │ Runner Process   │        │
│  │ RuntimeProcessor │◄────JSON────►│ GadeRunnerMain   │        │
│  │                  │              │                  │        │
│  │ handleGuiRequest │              │ RemoteGuiInt...  │        │
│  │   ↓              │              │   ↓              │        │
│  │ deserialize args │              │ serialize args   │        │
│  │   ↓              │              │   ↓              │        │
│  │ invoke io.xyz()  │              │ send gui_request │        │
│  │   ↓              │              │   ↓              │        │
│  │ serialize result │              │ await response   │        │
│  │   ↓              │              │   ↓              │        │
│  │ send gui_response│              │ deserialize      │        │
│  └──────────────────┘              └──────────────────┘        │
│                                                                  │
│  MatrixRemoteInit.init() - registers deserializers on startup   │
└─────────────────────────────────────────────────────────────────┘
```

### Example: io.view(matrix) Flow

```groovy
// Script in external Gradle runtime
io.view(myMatrix, "Sales Data")
```

**Flow:**

1. **RemoteGuiInteraction** intercepts call
2. **Serialize:** `myMatrix.toRemoteMap()` → JSON map
3. **Send:** `{"type":"gui_request","object":"io","method":"view","args":[{...},"Sales Data"]}`
4. **Main process** receives message
5. **Deserialize:** `Matrix.fromRemoteMap()` → Matrix object
6. **Execute:** `realIo.view(matrix, "Sales Data")` → Display table
7. **Response:** `{"type":"gui_response","result":null}`
8. **Runner** unblocks eval thread

## Protocol Extension

### New Message Types

#### 1. GUI Request (Runner → Gade)

```json
{
  "type": "gui_request",
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "object": "io",
  "method": "display",
  "args": ["<serialized chart>", "My Chart"]
}
```

Fields:
- `type`: "gui_request" (fixed)
- `id`: UUID for async correlation
- `object`: Name of GUI object ("io")
- `method`: Method name ("display", "view", "prompt", etc.)
- `args`: Array of serialized arguments

#### 2. GUI Response (Gade → Runner)

**Success:**
```json
{
  "type": "gui_response",
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "result": "<serialized result>"
}
```

**Error:**
```json
{
  "type": "gui_error",
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "error": "Connection 'mydb' not found"
}
```

## Library Enhancements Strategy

Since you maintain both the **matrix library** and **gi-fx/gi-console** (GuiInteraction), we can design a clean, integrated solution:

### Architecture

1. **gi-console** - Add remote serialization interface and base classes
2. **matrix library** - Implement serialization for Matrix, Chart, MatrixXChart
3. **Gade** - Use the standardized serialization protocol

This creates a clean separation: libraries define HOW they serialize, Gade just transports the data.

## gi-console Enhancements

### Add Remote Serialization Support

**New file:** `gi-console/src/main/java/se/alipsa/gi/remote/RemoteSerializable.java`

```java
package se.alipsa.gi.remote;

import java.util.Map;

/**
 * Interface for objects that can be serialized across process boundaries
 * for remote GUI interactions in Gade.
 */
public interface RemoteSerializable {

  /**
   * Serialize this object to a JSON-compatible map.
   * The map must contain a "_type" key identifying the class for deserialization.
   *
   * @return Map containing type info and serialized data
   */
  Map<String, Object> toRemoteMap();

  /**
   * Get the type identifier for deserialization.
   * Defaults to fully qualified class name.
   *
   * @return Type identifier
   */
  default String getRemoteType() {
    return getClass().getName();
  }
}
```

**New file:** `gi-console/src/main/java/se/alipsa/gi/remote/RemoteSerializationRegistry.java`

```java
package se.alipsa.gi.remote;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Registry for deserializers. Allows libraries to register their own
 * deserialization logic for custom types.
 */
public class RemoteSerializationRegistry {

  private static final Map<String, Function<Map<String, Object>, Object>> deserializers =
      new ConcurrentHashMap<>();

  /**
   * Register a deserializer for a type.
   *
   * @param typeName Fully qualified class name or type identifier
   * @param deserializer Function that converts map to object
   */
  public static void register(String typeName,
                              Function<Map<String, Object>, Object> deserializer) {
    deserializers.put(typeName, deserializer);
  }

  /**
   * Deserialize a map to an object using registered deserializers.
   *
   * @param map Serialized data (must contain "_type" key)
   * @return Deserialized object
   * @throws IllegalArgumentException if type not registered
   */
  public static Object deserialize(Map<String, Object> map) {
    String type = (String) map.get("_type");
    if (type == null) {
      throw new IllegalArgumentException("Map missing '_type' key");
    }

    Function<Map<String, Object>, Object> deserializer = deserializers.get(type);
    if (deserializer == null) {
      throw new IllegalArgumentException("No deserializer registered for type: " + type);
    }

    return deserializer.apply(map);
  }

  /**
   * Check if a deserializer is registered for a type.
   */
  public static boolean isRegistered(String type) {
    return deserializers.containsKey(type);
  }

  /**
   * Clear all registered deserializers (mainly for testing).
   */
  public static void clear() {
    deserializers.clear();
  }
}
```

**New file:** `gi-console/src/main/java/se/alipsa/gi/remote/RemoteSerializationHelper.java`

```java
package se.alipsa.gi.remote;

import java.util.Map;
import java.util.HashMap;

/**
 * Helper methods for remote serialization.
 */
public class RemoteSerializationHelper {

  /**
   * Serialize an argument for remote method invocation.
   * Handles primitives, RemoteSerializable objects, and fallback to toString().
   */
  public static Object serializeArg(Object arg) {
    if (arg == null) {
      return null;
    }

    // Primitives and simple types pass through
    if (arg instanceof String || arg instanceof Number || arg instanceof Boolean) {
      return arg;
    }

    // RemoteSerializable objects serialize themselves
    if (arg instanceof RemoteSerializable) {
      Map<String, Object> map = ((RemoteSerializable) arg).toRemoteMap();
      // Ensure _type is set
      if (!map.containsKey("_type")) {
        map.put("_type", ((RemoteSerializable) arg).getRemoteType());
      }
      return map;
    }

    // Collections pass through (Jackson will handle)
    if (arg instanceof java.util.Collection || arg instanceof Map) {
      return arg;
    }

    // Fallback: convert to string
    return arg.toString();
  }

  /**
   * Deserialize an argument received from remote method invocation.
   */
  public static Object deserializeArg(Object arg) {
    if (arg == null) {
      return null;
    }

    // If it's a map with _type, try to deserialize
    if (arg instanceof Map) {
      Map<String, Object> map = (Map<String, Object>) arg;
      if (map.containsKey("_type")) {
        try {
          return RemoteSerializationRegistry.deserialize(map);
        } catch (IllegalArgumentException e) {
          // Type not registered - return map as-is
          return arg;
        }
      }
    }

    // Everything else passes through
    return arg;
  }

  /**
   * Serialize result for sending back to caller.
   */
  public static Object serializeResult(Object result) {
    return serializeArg(result);  // Same logic
  }
}
```

## Matrix Library Enhancements

Since you maintain the matrix library, we can add native serialization support for the key types used with `io`:

### Types Requiring Serialization

From analysis of io.display() and io.view() usage:

1. **se.alipsa.matrix.core.Matrix** - Most common (used in io.view())
2. **se.alipsa.groovy.charts.Chart** - JavaFX charts (used in io.display())
3. **se.alipsa.matrix.xchart.abstractions.MatrixXChart** - XChart wrapper (used in io.display())

### Proposed Matrix Library Changes

#### 1. Add RemoteSerializable Interface

**New file:** `matrix-core/src/main/java/se/alipsa/matrix/remote/RemoteSerializable.java`

```java
package se.alipsa.matrix.remote;

import java.util.Map;

/**
 * Interface for objects that can be serialized for remote GUI operations.
 * Used by Gade to pass charts and tables across process boundaries.
 */
public interface RemoteSerializable {

  /**
   * Serialize this object to a JSON-compatible map.
   * @return Map containing type info and serialized data
   */
  Map<String, Object> toRemoteMap();

  /**
   * Get the type identifier for deserialization.
   * @return Fully qualified class name or type identifier
   */
  default String getRemoteType() {
    return getClass().getName();
  }
}
```

#### 2. Enhance Matrix Class

**Update:** `matrix-core/src/main/java/se/alipsa/matrix/core/Matrix.java`

```java
public class Matrix implements RemoteSerializable {

  // ... existing code ...

  @Override
  public Map<String, Object> toRemoteMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("_type", "se.alipsa.matrix.core.Matrix");

    // Use existing toCsvString method with header and types
    // This includes #name and #types comment lines!
    map.put("csv", toCsvString(true, true));

    return map;
  }

  /**
   * Deserialize from remote map.
   * Uses existing MatrixBuilder.csvString() to parse typed CSV.
   */
  public static Matrix fromRemoteMap(Map<String, Object> map) {
    String csv = (String) map.get("csv");

    // Use existing MatrixBuilder.csvString() - fully restores name, types, and data!
    return Matrix.builder().csvString(csv).build();
  }
}
```

**CSV Format (from toCsvString(true, true)):**
```csv
#name: employeeData
#types: int, String, BigDecimal
id, name, score
1, Alice, 95.5
2, Bob, 87.3
```

**Benefits of using typed CSV:**
- ✅ Reuses existing, tested serialization code
- ✅ No new parsing logic needed
- ✅ Full type fidelity (Integer, BigDecimal, LocalDate, etc.)
- ✅ Matrix name preserved in #name comment
- ✅ Type information in #types comment
- ✅ Compact and readable format
- ✅ Already handles edge cases (nulls, quotes, etc.)
- ✅ Single method call for serialize/deserialize

#### 3. Enhance Chart Class

**Update:** `matrix-charts/src/main/java/se/alipsa/groovy/charts/Chart.java`

```java
public abstract class Chart implements RemoteSerializable {

  // ... existing code ...

  @Override
  public Map<String, Object> toRemoteMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("_type", "se.alipsa.groovy.charts.Chart");
    map.put("chartType", this.getClass().getSimpleName());

    // Serialize as JavaFX snapshot PNG (Base64)
    javafx.scene.chart.Chart jfxChart = Plot.jfx(this);
    WritableImage image = jfxChart.snapshot(null, null);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", baos);
    String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

    map.put("imageData", base64);
    map.put("title", this.getTitle());

    return map;
  }

  /**
   * Deserialize from remote map.
   * Returns JavaFX chart node.
   */
  public static javafx.scene.chart.Chart fromRemoteMap(Map<String, Object> map) {
    String base64 = (String) map.get("imageData");
    byte[] imageData = Base64.getDecoder().decode(base64);
    ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
    BufferedImage buffered = ImageIO.read(bais);
    Image image = SwingFXUtils.toFXImage(buffered, null);

    // Wrap in ImageView inside a chart-like container
    ImageView imageView = new ImageView(image);
    StackPane pane = new StackPane(imageView);

    // Create a simple wrapper chart that displays the image
    return new javafx.scene.chart.Chart() {
      { getChildren().add(pane); }
    };
  }
}
```

#### 4. Enhance MatrixXChart Class

**Update:** `matrix-xchart/src/main/java/se/alipsa/matrix/xchart/abstractions/MatrixXChart.java`

```java
public abstract class MatrixXChart<T extends Chart<?, ?>>
    implements RemoteSerializable {

  // ... existing code ...

  @Override
  public Map<String, Object> toRemoteMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("_type", "se.alipsa.matrix.xchart.MatrixXChart");

    // Serialize as PNG (Base64)
    BufferedImage image = BitmapEncoder.getBufferedImage(xchart);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(image, "png", baos);
    String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

    map.put("imageData", base64);
    map.put("title", xchart.getTitle());

    return map;
  }

  /**
   * Deserialize from remote map.
   * Returns Swing JPanel with chart.
   */
  public static JPanel fromRemoteMap(Map<String, Object> map) {
    String base64 = (String) map.get("imageData");
    byte[] imageData = Base64.getDecoder().decode(base64);
    ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
    BufferedImage image = ImageIO.read(bais);

    // Create panel with image
    JPanel panel = new JPanel() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(image, 0, 0, this);
      }

      @Override
      public Dimension getPreferredSize() {
        return new Dimension(image.getWidth(), image.getHeight());
      }
    };

    return panel;
  }
}
```

#### 5. Register Deserializers on Startup

**New file:** `matrix-core/src/main/java/se/alipsa/matrix/remote/MatrixRemoteInit.java`

```java
package se.alipsa.matrix.remote;

import se.alipsa.gi.remote.RemoteSerializationRegistry;
import se.alipsa.matrix.core.Matrix;
import se.alipsa.groovy.charts.Chart;
import se.alipsa.matrix.xchart.abstractions.MatrixXChart;

/**
 * Registers matrix library deserializers.
 * Called automatically via ServiceLoader or explicitly by Gade.
 */
public class MatrixRemoteInit {

  private static boolean initialized = false;

  public static synchronized void init() {
    if (initialized) return;

    // Register Matrix deserializer
    RemoteSerializationRegistry.register(
        "se.alipsa.matrix.core.Matrix",
        Matrix::fromRemoteMap
    );

    // Register Chart deserializer
    RemoteSerializationRegistry.register(
        "se.alipsa.groovy.charts.Chart",
        Chart::fromRemoteMap
    );

    // Register MatrixXChart deserializer
    RemoteSerializationRegistry.register(
        "se.alipsa.matrix.xchart.MatrixXChart",
        MatrixXChart::fromRemoteMap
    );

    initialized = true;
  }
}
```

**Gade calls this on startup:**
```java
// In Gade.java constructor or static initializer
MatrixRemoteInit.init();  // Register matrix type deserializers
```

### Alternative: Data-Based Chart Serialization

Instead of serializing charts as images (which loses interactivity), we could serialize the underlying data:

```java
@Override
public Map<String, Object> toRemoteMap() {
  Map<String, Object> map = new HashMap<>();
  map.put("_type", "se.alipsa.groovy.charts.Chart");
  map.put("chartType", this.getClass().getSimpleName());
  map.put("title", getTitle());
  map.put("xLabel", getXAxisLabel());
  map.put("yLabel", getYAxisLabel());

  // Serialize series data
  List<Map<String, Object>> seriesData = new ArrayList<>();
  for (Series series : getSeries()) {
    Map<String, Object> s = new HashMap<>();
    s.put("name", series.getName());
    s.put("xValues", series.getXValues());
    s.put("yValues", series.getYValues());
    seriesData.add(s);
  }
  map.put("series", seriesData);

  return map;
}
```

**Recommendation:** Start with image-based serialization (simpler, works for all chart types), add data-based serialization later for specific chart types if needed.

## Implementation Steps

### Phase 0: Matrix Library Enhancements (1 day)

**Priority: HIGH** - Do this first to enable the rest

1. **Add RemoteSerializable interface** (15 min)
   - Create interface in matrix-core
   - Define toRemoteMap() and fromRemoteMap() contracts

2. **Enhance Matrix class** (30 min)
   - Implement RemoteSerializable
   - Add toRemoteMap() - calls existing toCsvString(true, true)
   - Add static fromRemoteMap() - calls existing MatrixBuilder.data()
   - Unit tests for serialization round-trip

3. **Enhance Chart class** (2 hours)
   - Implement RemoteSerializable
   - Add toRemoteMap() - serialize as Base64 PNG snapshot
   - Add static fromRemoteMap() - deserialize to ImageView wrapper
   - Unit tests

4. **Enhance MatrixXChart class** (2 hours)
   - Implement RemoteSerializable
   - Add toRemoteMap() - serialize as Base64 PNG
   - Add static fromRemoteMap() - deserialize to JPanel
   - Unit tests

5. **Publish matrix library snapshot** (30 min)
   - Version bump (e.g., 2.5.0-SNAPSHOT)
   - Publish to Maven Central or local repo
   - Update Gade's matrix dependency version

### 1. Create RemoteGuiInteraction Class

**File:** `src/main/java/se/alipsa/gade/runner/RemoteGuiInteraction.java`

```java
package se.alipsa.gade.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.GroovyObjectSupport;
import se.alipsa.gi.remote.RemoteSerializationHelper;

import java.io.BufferedWriter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Proxy for GUI interactions in external runtimes.
 * Routes method calls to main Gade process via socket.
 */
public class RemoteGuiInteraction extends GroovyObjectSupport {
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final long TIMEOUT_MS = 60000; // 60 seconds

  private final String objectName;
  private final BufferedWriter writer;
  private final ConcurrentHashMap<String, CompletableFuture<Object>> pending;

  public RemoteGuiInteraction(String objectName,
                              BufferedWriter writer,
                              ConcurrentHashMap<String, CompletableFuture<Object>> pending) {
    this.objectName = objectName;
    this.writer = writer;
    this.pending = pending;
  }

  @Override
  public Object invokeMethod(String method, Object args) {
    try {
      String id = UUID.randomUUID().toString();

      // Serialize arguments using gi-console helper
      Object[] argsArray = args instanceof Object[] ? (Object[]) args : new Object[]{args};
      List<Object> serializedArgs = new ArrayList<>();
      for (Object arg : argsArray) {
        serializedArgs.add(RemoteSerializationHelper.serializeArg(arg));
      }

      // Prepare request
      Map<String, Object> request = new HashMap<>();
      request.put("type", "gui_request");
      request.put("id", id);
      request.put("object", objectName);
      request.put("method", method);
      request.put("args", serializedArgs);

      // Register pending future
      CompletableFuture<Object> future = new CompletableFuture<>();
      pending.put(id, future);

      // Send request
      synchronized (writer) {
        mapper.writeValue(writer, request);
        writer.write("\n");
        writer.flush();
      }

      // Wait for response (blocks eval thread)
      Object result = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);

      // Deserialize result using gi-console helper
      return RemoteSerializationHelper.deserializeArg(result);

    } catch (TimeoutException e) {
      throw new RuntimeException("GUI operation timed out: " + method, e);
    } catch (Exception e) {
      throw new RuntimeException("GUI operation failed: " + method, e);
    }
  }
}
```

### 2. Update GadeRunnerMain

**Changes to:** `src/main/java/se/alipsa/gade/runner/GadeRunnerMain.java`

#### Add pending futures map:
```java
// At class level
private static final ConcurrentHashMap<String, CompletableFuture<Object>> guiPending =
  new ConcurrentHashMap<>();
```

#### Update ensureUnsupportedGuiInteractions():
```java
private static void ensureRemoteGuiInteractions(Binding binding, Object keys, BufferedWriter writer) {
  if (keys == null) return;
  if (!(keys instanceof Iterable<?> iterable)) return;

  Map<String, Object> variables = binding.getVariables();
  for (Object key : iterable) {
    String name = key == null ? "" : String.valueOf(key).trim();
    if (name.isEmpty()) continue;
    if (variables.containsKey(name)) continue;

    // Inject RemoteGuiInteraction instead of UnsupportedGuiInteraction
    binding.setVariable(name, new RemoteGuiInteraction(name, writer, guiPending));
  }
}
```

#### Add GUI response handler in main loop:
```java
// In main command loop, add new cases
case "gui_response" -> handleGuiResponse(cmd);
case "gui_error" -> handleGuiError(cmd);

// Handler methods
private static void handleGuiResponse(Map<String, Object> cmd) {
  String id = (String) cmd.get("id");
  CompletableFuture<Object> future = guiPending.remove(id);
  if (future != null) {
    future.complete(cmd.get("result"));
  }
}

private static void handleGuiError(Map<String, Object> cmd) {
  String id = (String) cmd.get("id");
  CompletableFuture<Object> future = guiPending.remove(id);
  if (future != null) {
    String error = (String) cmd.getOrDefault("error", "GUI operation failed");
    future.completeExceptionally(new RuntimeException(error));
  }
}
```

### 3. Update RuntimeProcessRunner

**Changes to:** `src/main/java/se/alipsa/gade/runtime/RuntimeProcessRunner.java`

#### Add GUI request handler in handleMessage():
```java
private void handleMessage(Map<String, Object> msg) {
  String type = (String) msg.get("type");
  switch (type) {
    case "hello" -> validateProtocolVersion(msg);
    case "out" -> console.appendFx((String) msg.getOrDefault("text", ""), false);
    case "err" -> console.appendWarningFx((String) msg.getOrDefault("text", ""));
    case "result", "bindings", "interrupted", "shutdown" -> complete(msg);
    case "error" -> completeExceptionally(msg);
    case "gui_request" -> handleGuiRequest(msg);  // NEW
  }
}

private void handleGuiRequest(Map<String, Object> msg) {
  String id = (String) msg.get("id");
  String objectName = (String) msg.get("object");
  String method = (String) msg.get("method");
  List<?> argsList = (List<?>) msg.get("args");

  Platform.runLater(() -> {
    try {
      // Get the actual GUI object (e.g., InOut)
      GuiInteraction guiObject = guiInteractions.get(objectName);
      if (guiObject == null) {
        sendGuiError(id, "GUI object not found: " + objectName);
        return;
      }

      // Deserialize arguments using gi-console helper
      Object[] args = argsList.stream()
          .map(RemoteSerializationHelper::deserializeArg)
          .toArray();

      // Invoke method via reflection
      Object result = invokeGuiMethod(guiObject, method, args);

      // Serialize result using gi-console helper
      Object serializedResult = RemoteSerializationHelper.serializeResult(result);

      // Send response
      Map<String, Object> response = new HashMap<>();
      response.put("type", "gui_response");
      response.put("id", id);
      response.put("result", serializedResult);
      send(response);

    } catch (Exception e) {
      log.error("GUI request failed: {}:{}", objectName, method, e);
      sendGuiError(id, e.getMessage());
    }
  });
}

private Object invokeGuiMethod(GuiInteraction guiObject, String method, Object[] args) {
  // Use reflection or MethodHandles to invoke method
  // Handle type conversions for serialized args
  // Return result
  throw new UnsupportedOperationException("TODO: implement reflection-based invocation");
}

private void sendGuiError(String id, String error) {
  try {
    send(Map.of("type", "gui_error", "id", id, "error", error));
  } catch (IOException e) {
    log.error("Failed to send GUI error response", e);
  }
}
```

### 4. Update Protocol Version

**File:** `src/main/java/se/alipsa/gade/runtime/ProtocolVersion.java`

```java
public static final String CURRENT = "1.1";  // Bump minor version
public static final int MAJOR = 1;
public static final int MINOR = 1;  // Changed from 0

// Add to compatibility method:
// Version 1.0 clients can still connect (major version matches)
// but won't support GUI interactions
```

### 5. Argument Serialization Strategy

**Challenge:** Complex objects (charts, tables) need serialization

**Options:**

1. **JSON Serialization** (simple types only)
   - Works for: strings, numbers, maps, lists
   - Doesn't work for: JavaFX nodes, charts, custom objects

2. **Object Handle References** (recommended)
   - Serialize object to temp file/memory in runner
   - Send handle ID to Gade
   - Gade deserializes from handle
   - Works for any serializable object

3. **Type-specific Handlers**
   - Define serialization for common types:
     - Charts → JSON spec
     - Tables → CSV/JSON
     - Images → Base64 PNG

**Recommended Implementation:**

```java
// In RemoteGuiInteraction
private Object serializeArg(Object arg) {
  if (arg == null) return null;
  if (arg instanceof String || arg instanceof Number || arg instanceof Boolean) {
    return arg;  // Primitive types pass through
  }

  // For complex objects: serialize to temp file and send path
  // Or use Java serialization + Base64
  // Or implement type-specific serialization

  return arg.toString();  // Fallback: string representation
}
```

## Testing Strategy

### Unit Tests

1. **RemoteGuiInteractionTest**: Verify request/response flow
2. **ProtocolVersionTest**: Verify backward compatibility
3. **SerializationTest**: Verify argument serialization

### Integration Tests

1. **External runtime with io.display()**: Verify chart display works
2. **External runtime with io.view()**: Verify table display works
3. **External runtime with io.prompt()**: Verify dialog interactions
4. **Timeout handling**: Verify long-running operations timeout gracefully

### Manual Testing

1. Create Gradle project
2. Run script with `io.display(chart)` in Gradle runtime
3. Verify chart appears in Gade GUI
4. Verify no exceptions thrown

## Performance Considerations

### Latency
- Each GUI call adds network roundtrip (local socket: ~1-5ms)
- Batch operations where possible
- Consider async variants for non-blocking operations

### Threading
- GUI requests block eval thread (by design - matches GADE runtime behavior)
- Gade executes on JavaFX thread (Platform.runLater)
- No deadlock risk - different threads

### Memory
- Serialized objects may consume memory
- Implement cleanup for temp files/handles
- Consider streaming for large datasets

## Future Enhancements

### 1. Async GUI Operations
```java
// Non-blocking variant
io.displayAsync(chart).whenComplete((result, error) -> {
  println "Chart displayed: $result"
})
```

### 2. Batch Operations
```java
// Single roundtrip for multiple operations
io.batch {
  display(chart1)
  display(chart2)
  view(table)
}
```

### 3. Streaming Large Data
```java
// Stream table rows instead of sending all at once
io.viewStream(largeTable, chunkSize: 1000)
```

## Migration Guide

### For Users

**Before:**
```groovy
// Had to switch to GADE runtime for GUI
io.display(myChart)  // ERROR in Gradle runtime
```

**After:**
```groovy
// Works in any runtime (Gradle, Maven, Custom)
io.display(myChart)  // Works everywhere!
```

**Behavioral Differences:**
- Slightly higher latency in external runtimes (network overhead)
- Same API, transparent to user
- Some complex object types may have serialization limitations

### For Developers

1. Update protocol version to 1.1
2. Test backward compatibility with old runners
3. Document serialization limitations
4. Add integration tests for common use cases

## Open Questions

1. **Serialization Strategy**: Which approach for complex objects?
   - Recommendation: Start with JSON for primitives, add object handles for complex types

2. **Timeout Values**: 60 seconds reasonable?
   - Recommendation: Make configurable, default 60s

3. **Error Handling**: How to handle GUI errors gracefully?
   - Recommendation: Return error message, don't crash runner

4. **Backward Compatibility**: Support old runners without GUI?
   - Recommendation: Yes - fall back to UnsupportedGuiInteraction if protocol < 1.1

## Implementation Effort

**Estimated Effort:** 2.5-3 days (distributed across 3 repositories)

**Reduced from initial 3-4 days** by reusing Matrix's existing typed CSV serialization (toCsvString/MatrixBuilder.data) instead of implementing custom JSON serialization.

### Phase 0A: gi-console Enhancements (0.5 days)

**Repository:** gi-console

- [ ] Create RemoteSerializable interface (15 min)
- [ ] Create RemoteSerializationRegistry (30 min)
- [ ] Create RemoteSerializationHelper (30 min)
- [ ] Unit tests for registry and helper (1 hour)
- [ ] Publish gi-console snapshot (15 min)

**Deliverable:** gi-console with remote serialization support

### Phase 0B: Matrix Library Enhancements (1 day)

**Repository:** matrix library

**Dependencies:** Requires gi-console from Phase 0A

- [ ] Update matrix-core to depend on gi-console (15 min)
- [ ] Enhance Matrix class with RemoteSerializable (30 min)
  - Implement toRemoteMap() - wraps toCsvString(true, true)
  - Implement fromRemoteMap() - wraps MatrixBuilder.data()
  - Unit tests
- [ ] Enhance Chart class with RemoteSerializable (2 hours)
  - Implement toRemoteMap() (Base64 PNG)
  - Implement fromRemoteMap() (ImageView wrapper)
  - Unit tests
- [ ] Enhance MatrixXChart class with RemoteSerializable (2 hours)
  - Implement toRemoteMap() (Base64 PNG)
  - Implement fromRemoteMap() (JPanel wrapper)
  - Unit tests
- [ ] Create MatrixRemoteInit for deserializer registration (30 min)
- [ ] Integration tests (1 hour)
- [ ] Publish matrix library snapshot (15 min)

**Deliverable:** Matrix library with remote serialization support

### Phase 1: Core Protocol (1 day)

**Repository:** Gade

**Dependencies:** Requires gi-console and matrix library from Phase 0
- [ ] RemoteGuiInteraction class
- [ ] Update GadeRunnerMain
- [ ] Update RuntimeProcessRunner
- [ ] Protocol version bump

### Phase 2: Serialization (0.5 days)
- [ ] Implement argument serialization
- [ ] Handle common types (charts, tables)
- [ ] Test round-trip serialization

### Phase 3: Testing (0.5-1 day)
- [ ] Unit tests
- [ ] Integration tests
- [ ] Manual testing

### Phase 4: Documentation (0.5 days)
- [ ] Update AGENTS.md
- [ ] Add examples
- [ ] Document limitations

## Success Criteria

- ✅ `io.display()` works in Gradle runtime
- ✅ `io.view()` works in Gradle runtime
- ✅ `io.prompt()` works in Gradle runtime
- ✅ All existing tests pass
- ✅ No performance regression in GADE runtime
- ✅ Backward compatible with protocol 1.0
- ✅ Clear error messages for unsupported types
