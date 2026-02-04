# Remote GUI Interaction - Quick Reference

## Overview

Enable `io` object to work in external runtimes (Gradle, Maven, Custom) by creating a proxy implementation (`RemoteInOut`) that forwards GUI calls over the existing socket protocol to the real `InOut` in the main Gade process.

## Architecture - Gade Only

**No changes needed to gi-console or matrix library!**

All implementation happens in Gade:

```
External Runtime:
  io = new RemoteInOut(...)  // Proxy implementation
  io.display(chart) → serialize → send gui_request via socket

Main Gade Process:
  receive gui_request → deserialize → realInOut.display(chart) → GUI appears
  → serialize result → send gui_response via socket
```

## Implementation - Single Repository

### Phase 1: Create RemoteInOut (2-3 hours)

**File:** `src/main/java/se/alipsa/gade/runner/RemoteInOut.java`

- Extends `AbstractInOut` (or implements `GuiInteraction`)
- Overrides all methods to proxy via socket
- Uses `ArgumentSerializer` to serialize/deserialize arguments

**File:** `src/main/java/se/alipsa/gade/runner/ArgumentSerializer.java`

- Serializes Matrix using `matrix.toCsvString(true, true)`
- Deserializes using `Matrix.builder().csvString(csv).build()`
- Serializes charts as Base64 PNG
- Handles primitives (String, Integer, etc.)

### Phase 2: Update GadeRunnerMain (1 hour)

- Replace `UnsupportedGuiInteraction` with `RemoteInOut`
- Add `gui_response` / `gui_error` handlers
- Maintain pending futures map for async responses

### Phase 3: Update RuntimeProcessRunner (2-3 hours)

- Add `gui_request` message handler
- Deserialize arguments
- Invoke real `InOut` methods via reflection
- Serialize and send response

### Phase 4: Protocol Version (15 min)

- Bump protocol version from 1.0 to 1.1
- Maintain backward compatibility

## Key Design Decisions

### Serialization (All in Gade)

**Matrix:**
```java
// Serialize
Map<String, Object> map = new HashMap<>();
map.put("_type", "se.alipsa.matrix.core.Matrix");
map.put("csv", matrix.toCsvString(true, true));

// Deserialize
String csv = (String) map.get("csv");
Matrix matrix = Matrix.builder().csvString(csv).build();
```

The CSV includes metadata:
```csv
#name: Sales
#types: String, Integer
Month,Revenue
Jan,1000
Feb,1200
```

**Charts:**
```java
// Serialize as PNG
BufferedImage image = renderChart(chart);
String base64 = Base64.getEncoder().encodeToString(pngBytes);
map.put("imageData", base64);

// Deserialize
byte[] pngBytes = Base64.getDecoder().decode(base64);
ImageView view = new ImageView(new Image(...));
```

**Rationale:**
- Matrix: Full fidelity, can filter/sort in GUI
- Charts: Simple, works for all chart types
- No new dependencies - uses existing Matrix methods

### No Library Changes

- gi-console: Stays generic, no Gade-specific code
- matrix: Uses existing toCsvString/csvString methods
- All serialization lives in Gade where protocol lives

## Protocol Changes

### New Messages

**GUI Request (Runner → Gade):**
```json
{
  "type": "gui_request",
  "id": "uuid",
  "method": "view",
  "args": [
    {"_type": "se.alipsa.matrix.core.Matrix", "csv": "..."},
    "My Table"
  ]
}
```

**GUI Response (Gade → Runner):**
```json
{
  "type": "gui_response",
  "id": "uuid",
  "result": null
}
```

**GUI Error (Gade → Runner):**
```json
{
  "type": "gui_error",
  "id": "uuid",
  "error": "error message"
}
```

### Protocol Version

**Current:** 1.0
**New:** 1.1

Major version must match. Old runners (1.0) still connect but won't support GUI.

## Testing Strategy

### Unit Tests

**ArgumentSerializer:**
- Matrix round-trip (serialize/deserialize)
- Chart PNG encoding/decoding
- Primitives pass through
- Null handling

**RemoteInOut:**
- Method calls create correct gui_request
- Timeout after 60 seconds
- Error handling

**RuntimeProcessRunner:**
- Handle gui_request
- Invoke InOut methods
- Send responses

### Integration Tests

1. **External runtime with Matrix:**
   ```groovy
   io.view(Matrix.create(...), "Test")
   ```

2. **External runtime with Chart:**
   ```groovy
   io.display(PieChart.create(...), "Test")
   ```

3. **Timeout handling**
4. **Error messages**

## Implementation Timeline

**Total: 1.5-2 days** (all in Gade repository)

### Day 1 (Morning)
- [ ] Create ArgumentSerializer (1-2 hours)
  - Matrix serialization using toCsvString
  - Chart serialization as PNG
  - Unit tests

- [ ] Create RemoteInOut (2-3 hours)
  - Implement GuiInteraction interface
  - Proxy all methods via socket
  - Unit tests

### Day 1 (Afternoon)
- [ ] Update GadeRunnerMain (1 hour)
  - Replace UnsupportedGuiInteraction
  - Add gui_response/gui_error handlers
  - Update tests

- [ ] Update RuntimeProcessRunner (2-3 hours)
  - Add gui_request handler
  - Invoke InOut methods via reflection
  - Unit tests

### Day 2 (Morning)
- [ ] Integration testing (2-3 hours)
  - Test with real Matrix/Chart objects
  - Test timeout handling
  - Test error cases
  - Manual testing with examples

- [ ] Protocol version bump (15 min)
- [ ] Documentation (1-2 hours)
  - Update AGENTS.md
  - Add examples
  - Update user guide

## User Experience

### Before

```groovy
// In Gradle runtime
def chart = PieChart.create(...)
io.display(chart)  // ERROR: UnsupportedOperationException
```

**User must switch to GADE runtime**

### After

```groovy
// In Gradle runtime
def chart = PieChart.create(...)
io.display(chart)  // Works! (~100ms latency)
```

**No changes needed - works everywhere!**

## Performance

- **Local socket roundtrip:** ~1-5ms
- **Matrix CSV serialization (1000 rows):** ~10-20ms
- **Chart PNG encoding (800x600):** ~50-100ms
- **Total for io.display(chart):** ~60-125ms

Acceptable for interactive use.

## Success Criteria

- ✅ `io.display(chart)` works in Gradle runtime
- ✅ `io.view(matrix)` works in Maven runtime
- ✅ `io.prompt(...)` works in Custom runtime
- ✅ All existing tests pass
- ✅ No performance regression in GADE runtime
- ✅ Backward compatible with protocol 1.0
- ✅ Clear error messages
- ✅ No changes to gi-console or matrix library

## Notes

- Implementation is entirely within Gade repository
- Reuses existing Matrix serialization (no new code)
- Clean separation: RemoteInOut proxies, InOut implements
- Protocol remains simple and debuggable

See full design: `REMOTE_GUI_INTERACTION.md`
