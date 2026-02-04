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

Create a **proxy implementation** of `GuiInteraction` (`RemoteInOut`) that forwards method calls via the existing JSON-RPC socket protocol to the main Gade process where the real `InOut` implementation lives.

## Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│ gi-console                                                       │
│ - GuiInteraction interface (or AbstractInOut base class)        │
│ - Defines: display(), view(), prompt(), etc.                    │
└─────────────────────────────────────────────────────────────────┘
                                ▲
                                │ implements
                                │
┌─────────────────────────────────────────────────────────────────┐
│ Gade                                                             │
│                                                                  │
│  Main Process                    External Runtime Process       │
│  ┌────────────────┐              ┌────────────────┐            │
│  │ InOut          │              │ RemoteInOut    │            │
│  │ (real impl)    │              │ (proxy impl)   │            │
│  │                │    Socket    │                │            │
│  │ display(chart) │◄────JSON────►│ display(chart) │            │
│  │ view(matrix)   │              │ view(matrix)   │            │
│  │ prompt(...)    │              │ prompt(...)    │            │
│  └────────────────┘              └────────────────┘            │
│         ▲                                 │                     │
│         │                                 │                     │
│         │                                 ▼                     │
│  RuntimeProcessor                  User Script:                │
│  handleGuiRequest()                io.display(chart)           │
│  - Deserialize args                                             │
│  - Call realInOut.display()                                     │
│  - Serialize result                                             │
│  - Send response                                                │
└─────────────────────────────────────────────────────────────────┘
```

### Key Components

1. **RemoteInOut** (`se.alipsa.gade.runner.RemoteInOut`)
   - Implements `GuiInteraction` (or extends `AbstractInOut`)
   - Proxies all method calls via socket protocol
   - Handles argument serialization
   - Injected as `io` in external runtimes

2. **InOut** (`se.alipsa.gade.interaction.InOut`)
   - Real implementation with JavaFX GUI
   - Used in main Gade process
   - Called by RuntimeProcessor when receiving gui_request

3. **No changes to gi-console or matrix library**
   - gi-console remains generic and protocol-agnostic
   - matrix library doesn't need serialization methods
   - All serialization logic lives in Gade

### Example: io.view(matrix) Flow

```groovy
// User script in external Gradle runtime
io.view(myMatrix, "Sales Data")
```

**Step-by-step:**

1. **User calls:** `io.view(myMatrix, "Sales Data")`
   - `io` is a `RemoteInOut` instance in external runtime

2. **RemoteInOut.view()** intercepts:
   - Serializes arguments (Matrix → typed CSV string)
   - Sends gui_request message via socket
   - Blocks waiting for response

3. **RuntimeProcessor** in main Gade receives gui_request:
   - Deserializes arguments (CSV string → Matrix object)
   - Calls: `realInOut.view(matrix, "Sales Data")`
   - Table appears in Gade GUI

4. **Response sent back:**
   - Result serialized (view returns void → null)
   - gui_response sent via socket

5. **RemoteInOut** unblocks and returns to user script

## Protocol Extension

### New Message Types

#### 1. GUI Request (Runner → Gade)

```json
{
  "type": "gui_request",
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "method": "view",
  "args": [
    {
      "_type": "se.alipsa.matrix.core.Matrix",
      "csv": "#name: Sales\n#types: String, Integer\nMonth,Revenue\nJan,1000\nFeb,1200\n"
    },
    "Sales Data"
  ]
}
```

Fields:
- `type`: "gui_request" (fixed)
- `id`: UUID for async correlation
- `method`: Method name ("display", "view", "prompt", etc.)
- `args`: Array of serialized arguments

#### 2. GUI Response (Gade → Runner)

**Success:**
```json
{
  "type": "gui_response",
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "result": null
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

## Serialization Strategy

All serialization logic lives in Gade - no changes to gi-console or matrix library.

### Supported Types

**Matrix (`se.alipsa.matrix.core.Matrix`):**
- **Serialize:** Call `matrix.toCsvString(true, true)` to get typed CSV
- **Deserialize:** Call `Matrix.builder().csvString(csv).build()`
- **Format:** CSV with `#name:` and `#types:` comment lines

**Charts (JavaFX Chart, XChart, MatrixXChart):**
- **Serialize:** Render to BufferedImage, encode as Base64 PNG
- **Deserialize:** Decode Base64, wrap in ImageView
- **Format:** `{"_type": "chart", "imageData": "base64..."}`

**Primitives:**
- String, Integer, Boolean, null: Pass through as-is
- No serialization needed

### Handling Type-Specific Method Overloads

**Challenge:** InOut has many type-specific overloads like `display(Chart chart)`, `display(XChart xchart)`, etc.

**Solution:** Convert to generic types before serializing (mirrors what real InOut does internally):

```java
// Type-specific overloads in RemoteInOut
@Override
public void display(Chart chart, String... title) {
    // Convert Chart → JavaFX Node (same as real InOut)
    Node node = Plot.jfx(chart);
    display(node, title);  // Delegate to generic overload
}

@Override
public void display(org.knowm.xchart.internal.chartpart.Chart xchart, String... title) {
    // Convert XChart → BufferedImage
    BufferedImage image = BitmapEncoder.getBufferedImage(xchart);
    display(image, title);  // Delegate to Image overload
}

@Override
public void display(Node node, String... title) {
    // Generic Node handler - serialize as PNG
    sendGuiRequest("display", serializeNode(node), title);
}

@Override
public void display(Image image, String... title) {
    // Generic Image handler - serialize as PNG
    sendGuiRequest("display", serializeImage(image), title);
}
```

**Benefits:**
- All chart types → PNG images (simple, works everywhere)
- Reuses existing conversion logic (Plot.jfx, BitmapEncoder)
- No need to add serialization to chart libraries
- Consistent serialization format

**Trade-off:** Charts are rendered to images, losing interactivity. This is acceptable for remote runtimes since the GUI is in a different process anyway.

### Implementation Classes

#### ArgumentSerializer (Gade)

**File:** `src/main/java/se/alipsa/gade/runner/ArgumentSerializer.java`

```java
package se.alipsa.gade.runner;

import se.alipsa.matrix.core.Matrix;
import java.util.*;

public class ArgumentSerializer {

  public static Object serialize(Object arg) {
    if (arg == null) return null;

    // Primitives pass through
    if (arg instanceof String || arg instanceof Number || arg instanceof Boolean) {
      return arg;
    }

    // Matrix - use existing typed CSV
    if (arg instanceof Matrix) {
      Matrix matrix = (Matrix) arg;
      Map<String, Object> map = new HashMap<>();
      map.put("_type", "se.alipsa.matrix.core.Matrix");
      map.put("csv", matrix.toCsvString(true, true));
      return map;
    }

    // JavaFX Chart - serialize as PNG
    if (arg instanceof javafx.scene.chart.Chart) {
      return serializeChartAsPng(arg);
    }

    // XChart - serialize as PNG
    if (arg instanceof org.knowm.xchart.internal.chartpart.Chart) {
      return serializeXChartAsPng(arg);
    }

    // Collections pass through
    if (arg instanceof Collection || arg instanceof Map) {
      return arg;
    }

    // Fallback: toString
    return arg.toString();
  }

  public static Object deserialize(Object arg) {
    if (arg == null) return null;

    // Check if it's a serialized object map
    if (arg instanceof Map) {
      Map<String, Object> map = (Map<String, Object>) arg;
      String type = (String) map.get("_type");

      if ("se.alipsa.matrix.core.Matrix".equals(type)) {
        String csv = (String) map.get("csv");
        return Matrix.builder().csvString(csv).build();
      }

      if ("chart".equals(type)) {
        return deserializeChartFromPng(map);
      }
    }

    // Everything else passes through
    return arg;
  }

  private static Map<String, Object> serializeChartAsPng(Object chart) {
    // Render chart to BufferedImage
    // Encode as Base64 PNG
    // Return map with _type and imageData
  }

  private static Object deserializeChartFromPng(Map<String, Object> map) {
    // Decode Base64 to BufferedImage
    // Wrap in ImageView or SwingNode
  }
}
```

## Implementation Steps

### Phase 1: Create RemoteInOut (2-3 hours)

**File:** `src/main/java/se/alipsa/gade/runner/RemoteInOut.java`

This class extends `AbstractInOut` (or implements `GuiInteraction`) and proxies all methods:

```java
package se.alipsa.gade.runner;

import se.alipsa.gi.console.AbstractInOut;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.util.*;
import java.util.concurrent.*;

public class RemoteInOut extends AbstractInOut {

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final long TIMEOUT_MS = 60000; // 60 seconds

  private final BufferedWriter writer;
  private final ConcurrentHashMap<String, CompletableFuture<Object>> pending;

  public RemoteInOut(BufferedWriter writer,
                     ConcurrentHashMap<String, CompletableFuture<Object>> pending) {
    this.writer = writer;
    this.pending = pending;
  }

  @Override
  public void display(Object chart, String... title) {
    sendGuiRequest("display", chart, title.length > 0 ? title[0] : null);
  }

  @Override
  public void view(Object matrix, String... title) {
    sendGuiRequest("view", matrix, title.length > 0 ? title[0] : null);
  }

  @Override
  public Object prompt(String title, Map<String, List<Object>> params) {
    return sendGuiRequest("prompt", title, params);
  }

  // ... implement all other GuiInteraction methods ...

  private Object sendGuiRequest(String method, Object... args) {
    try {
      String id = UUID.randomUUID().toString();

      // Serialize arguments
      List<Object> serializedArgs = new ArrayList<>();
      for (Object arg : args) {
        serializedArgs.add(ArgumentSerializer.serialize(arg));
      }

      // Create request
      Map<String, Object> request = new HashMap<>();
      request.put("type", "gui_request");
      request.put("id", id);
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

      // Wait for response
      Object result = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
      return ArgumentSerializer.deserialize(result);

    } catch (TimeoutException e) {
      throw new RuntimeException("GUI operation timed out: " + method, e);
    } catch (Exception e) {
      throw new RuntimeException("GUI operation failed: " + method, e);
    }
  }
}
```

### Phase 2: Update GadeRunnerMain (1 hour)

Replace `UnsupportedGuiInteraction` with `RemoteInOut`:

```java
// In handleEval method, replace:
// ensureUnsupportedGuiInteractions(binding, bindings.get(GUI_INTERACTION_KEYS));

// With:
ensureRemoteGuiInteractions(binding, bindings.get(GUI_INTERACTION_KEYS), writer, guiPending);

private static void ensureRemoteGuiInteractions(
    Binding binding,
    Object keys,
    BufferedWriter writer,
    ConcurrentHashMap<String, CompletableFuture<Object>> pending) {

  if (keys == null) return;
  if (!(keys instanceof Iterable<?> iterable)) return;

  Map<String, Object> variables = binding.getVariables();
  for (Object key : iterable) {
    String name = key == null ? "" : String.valueOf(key).trim();
    if (name.isEmpty()) continue;
    if (variables.containsKey(name)) continue;

    // Inject RemoteInOut instead of UnsupportedGuiInteraction
    binding.setVariable(name, new RemoteInOut(writer, pending));
  }
}
```

Add GUI response handlers:

```java
// In main command loop, add:
case "gui_response" -> handleGuiResponse(cmd);
case "gui_error" -> handleGuiError(cmd);

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

### Phase 3: Update RuntimeProcessRunner (2-3 hours)

Add GUI request handler:

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
  String method = (String) msg.get("method");
  List<?> argsList = (List<?>) msg.get("args");

  Platform.runLater(() -> {
    try {
      // Get the real InOut instance
      InOut realInOut = gui.getInOut();  // Assumes gui has getInOut() method

      // Deserialize arguments
      Object[] args = argsList.stream()
          .map(ArgumentSerializer::deserialize)
          .toArray();

      // Invoke method via reflection
      Object result = invokeMethod(realInOut, method, args);

      // Serialize result
      Object serializedResult = ArgumentSerializer.serialize(result);

      // Send response
      Map<String, Object> response = new HashMap<>();
      response.put("type", "gui_response");
      response.put("id", id);
      response.put("result", serializedResult);
      send(response);

    } catch (Exception e) {
      log.error("GUI request failed: {}", method, e);
      sendGuiError(id, e.getMessage());
    }
  });
}

private Object invokeMethod(InOut inOut, String methodName, Object[] args)
    throws Exception {
  // Use reflection to find and invoke method
  // Handle method overloading by matching argument types
  // Return result (may be null for void methods)
}

private void sendGuiError(String id, String error) {
  try {
    send(Map.of("type", "gui_error", "id", id, "error", error));
  } catch (IOException e) {
    log.error("Failed to send GUI error response", e);
  }
}
```

### Phase 4: Update Protocol Version (15 min)

**File:** `src/main/java/se/alipsa/gade/runtime/ProtocolVersion.java`

```java
public static final String CURRENT = "1.1";  // Bump from 1.0
public static final int MAJOR = 1;
public static final int MINOR = 1;  // Increment
```

## Testing Strategy

### Unit Tests

1. **ArgumentSerializer:**
   - Serialize/deserialize Matrix (round-trip)
   - Serialize chart as PNG
   - Handle primitives
   - Handle null values

2. **RemoteInOut:**
   - Method calls create proper gui_request messages
   - Timeout handling
   - Error handling

3. **RuntimeProcessRunner:**
   - Handle gui_request messages
   - Deserialize arguments correctly
   - Invoke InOut methods
   - Send responses

### Integration Tests

1. **External runtime with Matrix:**
   ```groovy
   def matrix = Matrix.builder()...
   io.view(matrix, "Test")  // Should display in Gade GUI
   ```

2. **External runtime with Chart:**
   ```groovy
   def chart = PieChart.create(...)
   io.display(chart, "Test")  // Should display in Gade GUI
   ```

3. **Timeout test:**
   - Simulate long GUI operation
   - Verify 60-second timeout

4. **Error handling:**
   - Invalid arguments
   - Method not found
   - Clear error messages

### Manual Testing

1. Start Gade with Gradle runtime
2. Run script: `io.display(PieChart.create(...))`
3. Verify chart appears
4. Run script: `io.view(Matrix.create(...))`
5. Verify table appears

## Performance Considerations

### Latency

- **Socket roundtrip:** ~1-5ms (local TCP)
- **Matrix CSV serialization:** ~10-20ms (1000 rows)
- **Chart PNG encoding:** ~50-100ms (800x600)
- **Total for io.display(chart):** ~60-125ms

Acceptable for interactive use.

### Memory

- **Matrix:** ~2x (original + CSV string)
- **Chart PNG:** ~500KB per chart
- Temporary objects cleaned up after response

## Migration Guide

### For Users

**Before:**
```groovy
// Had to switch to GADE runtime
io.display(myChart)  // ERROR in Gradle runtime
```

**After:**
```groovy
// Works in any runtime
io.display(myChart)  // Works in Gradle, Maven, Custom!
```

### Behavioral Differences

- Slightly higher latency (~60-125ms) in external runtimes
- Same API - transparent to users
- Charts serialized as images (no interactivity in external runtimes)

## Success Criteria

- ✅ `io.display(chart)` works in Gradle runtime
- ✅ `io.view(matrix)` works in Maven runtime
- ✅ `io.prompt(...)` works in Custom runtime
- ✅ All existing tests pass
- ✅ No performance regression in GADE runtime
- ✅ Backward compatible with protocol 1.0
- ✅ Clear error messages

## Implementation Effort

**Total: 1.5-2 days** (all in Gade)

- Phase 1: RemoteInOut class (2-3 hours)
- Phase 2: Update GadeRunnerMain (1 hour)
- Phase 3: Update RuntimeProcessRunner (2-3 hours)
- Phase 4: Protocol version bump (15 min)
- Testing: Unit + integration tests (2-3 hours)
- Documentation: Update AGENTS.md (1 hour)

**No changes needed to gi-console or matrix library!**
