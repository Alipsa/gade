# Remote GUI Interaction - Quick Reference

## Overview

Enable `io` object to work in external runtimes (Gradle, Maven, Custom) by serializing GUI calls over the existing socket protocol.

## Three-Repository Implementation

### 1. gi-console (Base library)

**What:** Add remote serialization infrastructure

**New files:**
- `RemoteSerializable.java` - Interface for serializable objects
- `RemoteSerializationRegistry.java` - Registry for deserializers
- `RemoteSerializationHelper.java` - Serialize/deserialize helpers

**Time:** 0.5 days

### 2. matrix library (Data types)

**What:** Make Matrix, Chart, MatrixXChart remoteable

**Changes:**
- `Matrix.java` - Implement RemoteSerializable, add toRemoteMap/fromRemoteMap (wraps existing toCsvString/MatrixBuilder.data)
- `Chart.java` - Implement RemoteSerializable (Base64 PNG snapshot)
- `MatrixXChart.java` - Implement RemoteSerializable (Base64 PNG)
- `MatrixRemoteInit.java` - Register deserializers

**Time:** 1 day

### 3. Gade (IDE)

**What:** Handle gui_request/gui_response messages

**New files:**
- `RemoteGuiInteraction.java` - Proxy for remote GUI calls

**Changes:**
- `GadeRunnerMain.java` - Inject RemoteGuiInteraction, handle gui_response
- `RuntimeProcessRunner.java` - Handle gui_request, invoke actual methods
- `ProtocolVersion.java` - Bump to 1.1
- `Gade.java` - Call MatrixRemoteInit.init() on startup

**Time:** 1 day

## Key Design Decisions

### Serialization Strategy

**Matrix:** Typed CSV (reuses existing toCsvString/MatrixBuilder.data)
```json
{
  "_type": "se.alipsa.matrix.core.Matrix",
  "name": "Sales",
  "csv": "Month,Revenue\nString,Integer\nJan,1000\nFeb,1200\n"
}
```

The CSV format includes:
- Row 1: Column headers
- Row 2: Type information (String, Integer, LocalDate, etc.)
- Row 3+: Data rows

This reuses Matrix's existing, tested serialization - no new parsing logic needed!

**Charts:** Base64-encoded PNG snapshots
```json
{
  "_type": "se.alipsa.groovy.charts.Chart",
  "imageData": "iVBORw0KGgoAAAANSUhEUg...",
  "title": "Sales Chart"
}
```

**Rationale:**
- Matrix: Full fidelity, supports filtering/sorting in UI
- Charts: Simple, works for all chart types, good quality

### Registry Pattern

Libraries register their own deserializers on startup:

```java
// In matrix library
RemoteSerializationRegistry.register(
    "se.alipsa.matrix.core.Matrix",
    Matrix::fromRemoteMap
);

// Gade calls on startup
MatrixRemoteInit.init();
```

This decouples serialization logic from Gade.

## Protocol Changes

### New Messages

**GUI Request (Runner → Gade):**
```json
{
  "type": "gui_request",
  "id": "uuid",
  "object": "io",
  "method": "display",
  "args": [{serialized object}, "title"]
}
```

**GUI Response (Gade → Runner):**
```json
{
  "type": "gui_response",
  "id": "uuid",
  "result": {serialized result or null}
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

Major version must match for compatibility. Old runners (1.0) will still connect but won't support GUI operations.

## Testing Strategy

### Unit Tests

**gi-console:**
- RemoteSerializationRegistry: register/deserialize
- RemoteSerializationHelper: serialize/deserialize primitives

**matrix:**
- Matrix round-trip: toRemoteMap → fromRemoteMap
- Chart serialization: PNG quality, size
- Type conversion: Integer/Double/LocalDate/etc.

**Gade:**
- RemoteGuiInteraction: method invocation, timeout
- Protocol handlers: gui_request/gui_response
- Backward compatibility: protocol 1.0 runners

### Integration Tests

1. **External runtime with Matrix:**
   ```groovy
   def matrix = Matrix.create(...)
   io.view(matrix, "Test")  // Should display in Gade GUI
   ```

2. **External runtime with Chart:**
   ```groovy
   def chart = PieChart.create(...)
   io.display(chart, "Test")  // Should display in Gade GUI
   ```

3. **Timeout handling:**
   ```groovy
   // Simulate 61-second GUI operation
   io.slowOperation()  // Should timeout at 60 seconds
   ```

4. **Error handling:**
   ```groovy
   io.display(brokenChart)  // Should show clear error message
   ```

## Rollout Plan

### Phase 1: gi-console (Day 1)

- [ ] Implement RemoteSerializable interface
- [ ] Implement RemoteSerializationRegistry
- [ ] Implement RemoteSerializationHelper
- [ ] Unit tests
- [ ] Publish gi-console 0.3.0-SNAPSHOT

### Phase 2: matrix library (Day 2)

- [ ] Update to gi-console 0.3.0-SNAPSHOT
- [ ] Implement Matrix.toRemoteMap/fromRemoteMap (wraps toCsvString/MatrixBuilder.data)
- [ ] Implement Chart.toRemoteMap/fromRemoteMap
- [ ] Implement MatrixXChart.toRemoteMap/fromRemoteMap
- [ ] Implement MatrixRemoteInit
- [ ] Unit tests for each type
- [ ] Integration tests
- [ ] Publish matrix 2.5.0-SNAPSHOT

### Phase 3: Gade (Day 3)

- [ ] Update to gi-console 0.3.0-SNAPSHOT
- [ ] Update to matrix 2.5.0-SNAPSHOT
- [ ] Implement RemoteGuiInteraction
- [ ] Update GadeRunnerMain
- [ ] Update RuntimeProcessRunner
- [ ] Bump protocol version to 1.1
- [ ] Call MatrixRemoteInit.init() on startup
- [ ] Unit tests
- [ ] Integration tests
- [ ] Manual testing with examples

### Phase 4: Documentation & Release (0.5 days)

- [ ] Update AGENTS.md
- [ ] Update GRADLE_TOOLING_API_JAVA21.md
- [ ] Create REMOTE_GUI_INTERACTION_USER_GUIDE.md
- [ ] Add examples to examples/
- [ ] Release notes
- [ ] Publish stable versions

**Total: 2.5-3 days** (reduced from 3-4 days by reusing typed CSV)

## User Experience

### Before

```groovy
// In Gradle runtime
def chart = PieChart.create(...)
io.display(chart)  // ERROR: UnsupportedOperationException
```

**User must:**
1. Switch to GADE runtime
2. Re-run script
3. Lose Gradle classpath

### After

```groovy
// In Gradle runtime
def chart = PieChart.create(...)
io.display(chart)  // Works! Chart appears in GUI
```

**No changes needed!** Transparent remote execution with ~5-10ms latency.

## Performance Considerations

### Latency

- **Local socket roundtrip:** ~1-5ms
- **Matrix serialization (1000 rows):** ~10-20ms
- **Chart PNG encoding (800x600):** ~50-100ms
- **Total for io.display(chart):** ~60-125ms

Acceptable for interactive use. Batch operations could add `io.batch { }` wrapper in future.

### Memory

- **Matrix:** ~2x memory (original + JSON)
- **Chart PNG:** ~500KB per chart
- **Concurrent operations:** Limited by socket (one request at a time per runner)

Reasonable for typical use cases. Could add streaming for very large matrices in future.

## Future Enhancements

### 1. Async Variants

```groovy
io.displayAsync(chart).whenComplete { result ->
  println "Displayed!"
}
```

Non-blocking for long operations.

### 2. Batch Operations

```groovy
io.batch {
  display(chart1)
  display(chart2)
  view(table)
}
```

Single roundtrip for multiple GUI operations.

### 3. Data-Based Chart Serialization

Instead of PNG snapshots, serialize chart data for full interactivity:

```json
{
  "_type": "se.alipsa.groovy.charts.PieChart",
  "series": [{"name": "A", "value": 10}, ...]
}
```

Allows zooming, tooltips, etc. in GUI.

### 4. Streaming Large Data

```groovy
// Stream table in 1000-row chunks
io.viewStream(millionRowTable, chunkSize: 1000)
```

For very large datasets.

## Success Criteria

- ✅ `io.display(chart)` works in Gradle runtime
- ✅ `io.view(matrix)` works in Gradle runtime
- ✅ `io.prompt(...)` works in Gradle runtime
- ✅ All existing tests pass
- ✅ No performance regression in GADE runtime
- ✅ Backward compatible with protocol 1.0
- ✅ Clear error messages for unsupported types
- ✅ Documentation complete

## Questions?

See full design document: `REMOTE_GUI_INTERACTION.md`
