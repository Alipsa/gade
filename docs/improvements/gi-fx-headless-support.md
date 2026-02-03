# Enabling Headless Mode Support for Gade TestFX Tests

**Date:** February 3, 2026
**Issue:** GUI smoke tests show popup windows during automated builds
**Root Cause:** `se.alipsa.gi.fx.InOut` requires graphical environment
**Solution:** Conditional InOut implementation selection based on environment

---

## Problem Analysis

### Current Situation

When running TestFX smoke tests, Gade instantiates `se.alipsa.gade.interaction.InOut` which extends `se.alipsa.gi.fx.InOut`. The gi-fx InOut class has a static initializer that detects headless mode and throws:

```
UnsupportedOperationException: gi-fx InOut requires a graphical environment.
Use gi-console for headless environments.
```

**Location of Issue:**
- `src/main/java/se/alipsa/gade/interaction/InOut.java:62`
  ```java
  public class InOut extends se.alipsa.gi.fx.InOut {
  ```

- `src/main/java/se/alipsa/gade/Gade.java:175-177`
  ```java
  guiInteractions = Map.of(
      "io", new se.alipsa.gade.interaction.InOut()
  );
  ```

### GuiInteraction Library Architecture

The GuiInteraction library ([GitHub](https://github.com/Alipsa/GuiInteraction)) provides three implementations:

| Module | Technology | Environment | Version (Current) |
|--------|-----------|-------------|-------------------|
| **gi-fx** | JavaFX | Desktop GUI | 0.2.0 ✅ (currently used) |
| **gi-swing** | Swing | Desktop GUI | 0.2.0 |
| **gi-console** | Text/Console | **Headless/CI** | 0.2.0 ⚠️ (not yet added) |

**Key Finding:** The error message explicitly recommends using **gi-console for headless environments**.

### Current Dependency

```gradle
// build.gradle:106
implementation "se.alipsa.gi:gi-fx:0.2.0"
```

---

## Solution Design

### Approach 1: Runtime Detection in Gade (Recommended)

Modify Gade's InOut wrapper to conditionally select the appropriate gi implementation based on runtime environment.

#### Implementation Strategy

**1. Add gi-console dependency:**

```gradle
// build.gradle
implementation "se.alipsa.gi:gi-fx:0.2.0"
testImplementation "se.alipsa.gi:gi-console:0.2.0"  // Headless tests only
```

**2. Create headless detection utility:**

```java
// src/main/java/se/alipsa/gade/utils/EnvironmentUtils.java
package se.alipsa.gade.utils;

import java.awt.GraphicsEnvironment;

public class EnvironmentUtils {

    /**
     * Detects if the application is running in a headless environment.
     * Checks both AWT headless property and actual graphics environment.
     *
     * @return true if headless, false if GUI available
     */
    public static boolean isHeadless() {
        // Check explicit headless system property
        String headlessProp = System.getProperty("java.awt.headless");
        if ("true".equalsIgnoreCase(headlessProp)) {
            return true;
        }

        // Check if running in TestFX headless mode
        String testfxHeadless = System.getProperty("testfx.headless");
        if ("true".equalsIgnoreCase(testfxHeadless)) {
            return true;
        }

        // Check actual graphics environment availability
        try {
            return GraphicsEnvironment.isHeadless();
        } catch (Exception e) {
            // If we can't determine, assume headless for safety
            return true;
        }
    }
}
```

**3. Refactor InOut to use conditional parent class:**

This is the **challenging part** - Java doesn't allow conditional class extension at runtime. We have two sub-options:

#### Option 1A: Composition over Inheritance (Cleaner)

```java
// src/main/java/se/alipsa/gade/interaction/InOut.java
package se.alipsa.gade.interaction;

import se.alipsa.gade.utils.EnvironmentUtils;

/**
 * Gade's InOut wrapper that delegates to the appropriate gi implementation
 * based on the runtime environment (GUI vs headless).
 */
public class InOut {

    private final Object delegate;  // Either gi.fx.InOut or gi.console.InOut

    public InOut() {
        if (EnvironmentUtils.isHeadless()) {
            // Use console implementation for headless/CI environments
            delegate = new se.alipsa.gi.console.InOut();
        } else {
            // Use JavaFX implementation for GUI environments
            delegate = new se.alipsa.gi.fx.InOut();
        }
    }

    // Delegate all methods to the underlying implementation
    // This requires forwarding every method from the gi InOut interface

    public Set<String> dbConnectionNames() {
        if (delegate instanceof se.alipsa.gi.fx.InOut fx) {
            return gui.getEnvironmentComponent().getDefinedConnectionsNames();
        } else {
            // gi-console may have noop implementation
            return Collections.emptySet();
        }
    }

    // ... forward all other methods ...
}
```

**Pros:**
- Clean separation of concerns
- No inheritance complexity
- Can add headless-specific behavior easily

**Cons:**
- Requires forwarding ~100+ methods
- Increases maintenance burden
- Verbose code

#### Option 1B: Factory Pattern with Separate Classes (Recommended)

```java
// src/main/java/se/alipsa/gade/interaction/InOutFactory.java
package se.alipsa.gade.interaction;

import se.alipsa.gade.utils.EnvironmentUtils;

public class InOutFactory {

    /**
     * Creates the appropriate InOut implementation based on environment.
     */
    public static InOut create() {
        if (EnvironmentUtils.isHeadless()) {
            return new HeadlessInOut();
        } else {
            return new GuiInOut();
        }
    }
}

// src/main/java/se/alipsa/gade/interaction/GuiInOut.java
public class GuiInOut extends se.alipsa.gi.fx.InOut implements InOut {
    // Current implementation (rename existing InOut class to this)
}

// src/main/java/se/alipsa/gade/interaction/HeadlessInOut.java
public class HeadlessInOut extends se.alipsa.gi.console.InOut implements InOut {

    // Override methods that require special handling in headless mode

    @Override
    public void display(Chart chart, String... titleOpt) {
        // In headless mode, either:
        // 1. Noop (do nothing)
        // 2. Log the action
        // 3. Save to file instead
        log.info("Headless mode: display() called for chart (noop)");
    }

    @Override
    public void view(Matrix tableMatrix, String... title) {
        // In headless mode, print to console or log
        log.info("Headless mode: view() called for matrix: {}", title);
    }

    // ... override other GUI-specific methods ...
}

// src/main/java/se/alipsa/gade/interaction/InOut.java (interface)
public interface InOut {
    // Define common contract that both GuiInOut and HeadlessInOut must implement
    // This might not be necessary if gi-common already defines a common interface
}
```

**Update Gade.java:**

```java
// src/main/java/se/alipsa/gade/Gade.java:175-177
guiInteractions = Map.of(
    "io", InOutFactory.create()
);
```

**Pros:**
- Clean architecture
- Separates GUI and headless implementations
- Easy to test
- Follows dependency inversion principle

**Cons:**
- Requires refactoring existing InOut class
- Adds new classes to maintain

---

### Approach 2: Modify gi-fx Library (Not Recommended)

Since gi-fx is "our project" (user's words), we could modify gi-fx itself to not throw the exception in headless mode. However, this is **not recommended** because:

1. **Architectural violation:** gi-fx is explicitly designed for GUI environments
2. **The solution already exists:** gi-console is purpose-built for headless
3. **Maintenance burden:** Merging two implementations creates complexity
4. **Breaking change:** Other projects using gi-fx might depend on the current behavior

---

## Implementation Plan

### Phase 1: Minimal Change (Quick Fix for Tests)

**Goal:** Get TestFX tests running in headless mode with minimal code changes.

**Steps:**

1. **Add test-only gi-console dependency:**
   ```gradle
   testImplementation "se.alipsa.gi:gi-console:0.2.0"
   ```

2. **Create test-specific InOut for smoke tests:**
   ```java
   // src/test/java/se/alipsa/gade/TestInOut.java
   package se.alipsa.gade;

   public class TestInOut extends se.alipsa.gi.console.InOut {
       // Minimal headless InOut for testing
       // Override only critical methods needed for smoke tests
   }
   ```

3. **Modify GadeSmokeTest to inject TestInOut:**
   ```java
   @Override
   public void start(Stage stage) throws Exception {
       // Set system property before Gade initialization
       System.setProperty("gade.test.headless", "true");

       Gade app = new Gade();
       app.start(stage);

       // Override io with test implementation after startup
       // This is hacky but works for smoke tests
   }
   ```

**Pros:**
- Quick to implement
- Isolated to test code
- Doesn't affect production Gade

**Cons:**
- Hacky solution
- Doesn't solve the general headless use case
- Limited to test scenarios

### Phase 2: Production-Ready Solution (Full Implementation)

**Goal:** Enable Gade to run in true headless mode for scripting/automation scenarios.

**Steps:**

1. **Add production gi-console dependency:**
   ```gradle
   implementation "se.alipsa.gi:gi-console:0.2.0"
   ```

2. **Create EnvironmentUtils** (as shown above)

3. **Implement Factory Pattern:**
   - Create `InOutFactory`
   - Refactor existing `InOut` → `GuiInOut`
   - Create new `HeadlessInOut`
   - Extract common interface if needed

4. **Update Gade initialization:**
   ```java
   guiInteractions = Map.of(
       "io", InOutFactory.create()
   );
   ```

5. **Handle GUI-specific methods in HeadlessInOut:**
   - `display()` methods → noop or save to file
   - `view()` methods → console output or log
   - `prompt()` methods → use console input or throw UnsupportedOperationException
   - `dbConnection()` methods → should work (no GUI needed)

6. **Update documentation:**
   - Add headless mode section to user guide
   - Document which methods are noop in headless mode
   - Provide examples of running Gade scripts in headless CI/CD

7. **Testing:**
   - Verify smoke tests pass in headless mode
   - Test actual headless script execution
   - Ensure GUI mode still works correctly

**Effort Estimate:** 4-6 hours

---

## gi-console Limitations (Important!)

The user mentioned: "not all methods in gi-console are implemented (some are noop)."

**Methods likely to be noop or limited in gi-console:**

### GUI Display Methods
- `display(Chart)` - Cannot display charts in headless mode
- `display(Node)` - Cannot display JavaFX nodes
- `display(Image)` - Cannot show images
- `view(Matrix)` - May just log or print to stdout

**Alternatives in headless mode:**
```java
// Instead of:
io.display(chart, "Sales Chart")

// In headless mode, save to file:
io.save(chart, new File("sales-chart.png"))
```

### Interactive Prompts
- `prompt()` - Cannot show GUI dialogs
- `promptPassword()` - May use console input
- `promptSelect()` - May use console input with limitations

**Headless alternatives:**
```java
// Use environment variables or config files instead of prompts
String password = System.getenv("DB_PASSWORD");
```

### File Choosers
- `chooseFile()` - Cannot show file picker dialog
- `chooseDir()` - Cannot show directory picker

**Headless alternatives:**
```java
// Use command-line arguments or config files
File inputFile = new File(args[0]);
```

### Methods That SHOULD Work in Headless Mode

These methods don't require GUI and should work in gi-console:

- ✅ `dbConnect()` - Database connections
- ✅ `dbSelect()` - SQL queries
- ✅ `dbExecuteSql()` - SQL execution
- ✅ `projectDir()` - File system access
- ✅ `projectFile()` - File resolution
- ✅ `scriptFile()` - Script file access
- ✅ `readImage()` - Image file reading
- ✅ `save(Chart, File)` - Saving charts to files
- ✅ `addDependency()` - Maven dependency resolution
- ✅ `help()` - Text output

**Critical for smoke tests:** The smoke tests only verify UI structure presence, not interaction. In headless mode with TestFX Monocle, the UI exists but isn't displayed. The issue is the InOut initialization, not the UI operations themselves.

---

## Decision Matrix

| Approach | Effort | Impact on Prod | Test Coverage | Recommended |
|----------|--------|----------------|---------------|-------------|
| **Current (Exclude tests)** | 1 hour ✅ | None | Reduced | Temporary |
| **Phase 1 (Test-only fix)** | 2 hours | None | Full headless | For testing |
| **Phase 2 (Production ready)** | 6 hours | Enables headless | Full + headless | **Best long-term** |
| **Modify gi-fx** | 4 hours | Breaks separation | Full | ❌ Not recommended |

---

## Recommendations

### Immediate Action (Already Done)

✅ **Current state:** GUI tests excluded by default via `@Tag("gui")`
✅ **Works for:** Preventing popup windows during builds
✅ **Trade-off:** No automated GUI testing in CI/CD

### Short-term (Next Sprint)

**Implement Phase 1:** Test-specific headless support

**Why:**
- Enables automated GUI testing in CI/CD
- Low risk (test-only changes)
- Quick win for build automation

**Tasks:**
1. Add `testImplementation "se.alipsa.gi:gi-console:0.2.0"`
2. Create minimal TestInOut extending gi-console
3. Modify GadeSmokeTest to use TestInOut
4. Update build.gradle to run GUI tests in headless mode by default
5. Verify all 285 tests pass in CI/CD

### Long-term (Future Sprint)

**Implement Phase 2:** Production headless support

**Why:**
- Enables server-side script execution
- Supports automated report generation
- Allows CI/CD script validation
- Future-proofs Gade for containerized deployments

**Use cases:**
```bash
# Run Gade script in CI/CD
gade --headless --script analyze-data.groovy --output report.html

# Automated data processing
gade --headless --runtime gradle --script etl-pipeline.groovy
```

**Tasks:**
1. Complete Phase 1 first (prerequisite)
2. Implement EnvironmentUtils
3. Create InOutFactory with factory pattern
4. Refactor InOut → GuiInOut
5. Implement HeadlessInOut with gi-console
6. Add comprehensive tests for both modes
7. Document headless capabilities and limitations
8. Create example scripts demonstrating headless usage

---

## Testing Strategy

### Phase 1 Testing

```bash
# Run all tests in headless mode (including GUI tests)
./gradlew test -Dtestfx.headless=true

# Should pass 285/285 tests without showing GUI windows
```

### Phase 2 Testing

```bash
# Test GUI mode explicitly
./gradlew test -Dgade.headless=false
# Expected: 285 tests pass, GUI windows may appear

# Test headless mode
./gradlew test -Dgade.headless=true
# Expected: 285 tests pass, no GUI windows

# Test with TestFX + Monocle
./gradlew test -Dtestfx.headless=true -Dgroups=gui
# Expected: All GUI tests pass using Monocle headless platform
```

---

## References

### gi Libraries

- **GuiInteraction Repository:** https://github.com/Alipsa/GuiInteraction
- **Maven Repository:** https://mvnrepository.com/artifact/se.alipsa.gi
- **gi-fx:** Desktop GUI (JavaFX) - version 0.2.0
- **gi-console:** Headless/CI (Console) - version 0.2.0
- **gi-common:** Shared interfaces - version 0.2.0

### Related Documentation

- TestFX headless mode: Attempted but incompatible with gi-fx initialization
- Monocle platform: Headless JavaFX testing (works if InOut issue resolved)
- Current workaround: GUI tests excluded via `@Tag("gui")` annotation

### Key Files

- `src/main/java/se/alipsa/gade/interaction/InOut.java:62` - Current gi-fx extension
- `src/main/java/se/alipsa/gade/Gade.java:175-177` - InOut instantiation
- `src/test/java/se/alipsa/gade/GadeSmokeTest.java` - GUI smoke tests
- `build.gradle:106` - gi-fx dependency
- `docs/improvements/testfx-smoke-tests.md` - Current test documentation

---

## Conclusion

**Immediate fix (already implemented):** Excluding GUI tests by default prevents popup windows during builds but reduces test coverage.

**Recommended path forward:**

1. **Short-term:** Implement Phase 1 (test-only headless support) to restore automated GUI testing in CI/CD
2. **Long-term:** Implement Phase 2 (production headless support) to enable advanced automation scenarios

**Key insight:** The gi-console library already exists and is purpose-built for headless environments. We should use it rather than trying to make gi-fx work headless.

**Constraint to consider:** Some gi-console methods are noop, so HeadlessInOut implementation must handle these cases gracefully (log warnings, throw descriptive exceptions, or provide alternative behavior).

---

**Next Step:** User decision on whether to:
- A) Keep current state (tests excluded, no automation)
- B) Implement Phase 1 (test-only headless, quick win)
- C) Implement Phase 2 (full headless support, strategic investment)
