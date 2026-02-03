# Instructions for Publishing gi-console 0.2.0

**Context:** Gade requires gi-console:0.2.0 for headless mode support. Currently only 0.1.0 is published to Maven Central, which is incompatible with gi-common:0.2.0.

**Target Project:** https://github.com/Alipsa/GuiInteraction

---

## Issue Summary

### Current Situation

**Published Versions:**
```
se.alipsa.gi:gi-common:0.2.0    ✅ Published
se.alipsa.gi:gi-fx:0.2.0        ✅ Published
se.alipsa.gi:gi-swing:0.2.0     ✅ Published
se.alipsa.gi:gi-console:0.1.0   ❌ Old version (incompatible)
se.alipsa.gi:gi-console:0.2.0   ❌ NOT published
```

**Dependency Conflict:**
```gradle
// What we need:
testImplementation "se.alipsa.gi:gi-console:0.2.0"

// What Maven Central has:
testImplementation "se.alipsa.gi:gi-console:0.1.0"
// ↑ This depends on gi-common:0.1.0
// ↑ But Gade uses gi-common:0.2.0
// ↑ Result: Version conflict
```

**Impact on Gade:**
- Cannot use gi-console in production
- Headless mode not fully functional
- TestGade uses temporary AbstractInOut workaround

### What Gade Needs

**For Phase 1 (Test Infrastructure):**
- ✅ **Completed:** Using AbstractInOut as temporary base class
- ⏳ **Blocked:** Waiting for gi-console:0.2.0 to switch from AbstractInOut

**For Phase 2 (Production Headless Mode):**
- ⏳ **Blocked:** Cannot implement without gi-console:0.2.0
- Use case: Server-side script execution, CI/CD automation
- Architecture: EnvironmentUtils detects headless, uses gi-console instead of gi-fx

---

## Task 1: Publish gi-console:0.2.0 (REQUIRED)

### Objective
Publish gi-console version 0.2.0 to Maven Central, compatible with gi-common:0.2.0.

### Steps

1. **Navigate to GuiInteraction project:**
   ```bash
   cd /path/to/GuiInteraction
   ```

2. **Verify gi-console module structure:**
   ```bash
   ls -la gi-console/
   # Should contain: src/, build.gradle, etc.
   ```

3. **Update gi-console/build.gradle:**
   ```gradle
   // Ensure version is 0.2.0
   version = '0.2.0'

   dependencies {
       // Must depend on gi-common:0.2.0 (not 0.1.0)
       implementation "se.alipsa.gi:gi-common:0.2.0"

       // Other dependencies...
       implementation 'org.jsoup:jsoup:1.21.2'  // or latest
   }
   ```

4. **Verify InOut class implementation:**
   ```bash
   # Check that gi-console/src/main/groovy/se/alipsa/gi/console/InOut.groovy exists
   # and implements all GuiInteraction interface methods
   ```

5. **Build and test locally:**
   ```bash
   ./gradlew :gi-console:clean :gi-console:build
   ./gradlew :gi-console:test
   ```

6. **Publish to Maven Central:**
   ```bash
   ./gradlew :gi-console:publishToMavenCentral
   # Or whatever your publish task is named
   ```

7. **Verify publication:**
   ```bash
   # Wait 15-30 minutes for Maven Central sync
   curl -s "https://search.maven.org/solrsearch/select?q=g:se.alipsa.gi+AND+a:gi-console+AND+v:0.2.0&wt=json" | grep '"numFound"'
   # Should return numFound > 0
   ```

### Acceptance Criteria

- ✅ gi-console:0.2.0 published to Maven Central
- ✅ Depends on gi-common:0.2.0 (not 0.1.0)
- ✅ All GuiInteraction methods implemented (can be stubs/noops for Phase 1)
- ✅ Passes all tests
- ✅ Available via: `implementation "se.alipsa.gi:gi-console:0.2.0"`

---

## Task 2: Improve gi-console Method Implementations (OPTIONAL)

### Objective
Ensure gi-console has functional implementations for non-interactive methods, not just noops.

### Background

**User's note:** "Not all methods in gi-console are implemented (some are noop)."

**Methods that MUST work in headless mode:**

**File Operations:**
- `projectFile(String)` - Must return File object
- `getResourceUrl(String)` - Must load resources
- `urlExists(String, int)` - Must check URL accessibility
- `getContentType(String/File)` - Must detect MIME types

**Data Operations:**
- `viewMarkdown(String, String...)` - Could print to stdout or save to file
- `view(Integer, String...)` - Could print to stdout
- `saveToClipboard(String/File)` - Could save to temp file in headless mode
- `getFromClipboard()` - Could read from temp file in headless mode

**Methods that CAN be noop in headless mode:**

**Interactive Prompts:**
- `chooseFile()` / `chooseDir()` - No GUI, return null or throw UnsupportedOperationException
- `prompt()` variants - No user input available, return defaults or throw
- `promptYearMonth()` / `promptDate()` / `promptPassword()` - No user input

**Display Methods:**
- `display(Chart/File/String/JComponent)` - Could save to file instead of showing
- `view(Matrix/List)` - Could print summary to stdout

### Implementation Example

```groovy
// gi-console/src/main/groovy/se/alipsa/gi/console/InOut.groovy

package se.alipsa.gi.console

import se.alipsa.gi.AbstractInOut
import se.alipsa.matrix.charts.Chart
import java.io.File

class InOut extends AbstractInOut {

    // File operations - MUST work
    @Override
    File chooseFile(String title, File initialDirectory, String initialFileName, String... extensionFilters) {
        // Headless: cannot show file chooser
        throw new UnsupportedOperationException("chooseFile() not supported in headless mode. Use command-line arguments or config files.")
    }

    // Prompts - return defaults or throw
    @Override
    String prompt(String headerText, String label, String defaultValue) {
        // In headless mode, return default if provided
        if (defaultValue != null) {
            println "[gi-console] prompt('$headerText'): using default value: $defaultValue"
            return defaultValue
        }
        throw new UnsupportedOperationException("prompt() requires user input, not available in headless mode")
    }

    // Display - save to file instead
    @Override
    void display(Chart chart, String... titleOpt) {
        String title = titleOpt.length > 0 ? titleOpt[0] : "chart"
        File outputFile = new File("${title}.png")
        println "[gi-console] display(Chart): saving to ${outputFile.absolutePath}"
        // TODO: Implement chart.saveToFile(outputFile)
    }

    // View - print to stdout
    @Override
    void view(se.alipsa.matrix.core.Matrix tableMatrix, String... title) {
        String t = title.length > 0 ? title[0] : "Table"
        println "[gi-console] view(Matrix): $t"
        println tableMatrix.toString()  // Or tableMatrix.head() for large tables
    }
}
```

### Guidelines

**For interactive methods (prompts, file choosers):**
- Throw `UnsupportedOperationException` with helpful message
- OR accept defaults from environment variables/system properties
- Document alternative approaches (config files, CLI args)

**For display methods:**
- Print to stdout for text content
- Save to files for charts/images
- Log the action taken

**For file operations:**
- Must work normally (no GUI needed)
- Use standard Java File I/O

### Acceptance Criteria

- ✅ All file operations work correctly
- ✅ Interactive methods throw clear exceptions or accept defaults
- ✅ Display methods save to files or print to stdout
- ✅ Documentation explains headless behavior
- ✅ Tests verify headless behavior

---

## Task 3: Improve gi-fx Headless Detection (OPTIONAL)

### Objective
Make gi-fx fail gracefully when initialized in headless mode, with clearer error message.

### Current Behavior

**What happens now:**
```java
// When gi-fx InOut is initialized in headless mode:
throw new UnsupportedOperationException(
    "gi-fx InOut requires a graphical environment. Use gi-console for headless environments."
)
```

**Where:** Likely in static initializer or constructor of `se.alipsa.gi.fx.InOut`

### Improvement Suggestions

**Option 1: Better error message with usage example**
```groovy
// gi-fx/src/main/groovy/se/alipsa/gi/fx/InOut.groovy

package se.alipsa.gi.fx

import java.awt.GraphicsEnvironment

class InOut extends se.alipsa.gi.AbstractInOut {

    static {
        if (GraphicsEnvironment.isHeadless()) {
            throw new UnsupportedOperationException("""
                gi-fx InOut requires a graphical environment but detected headless mode.

                For headless environments, use gi-console instead:

                    @Grab(group:'se.alipsa.gi', module:'gi-console', version:'0.2.0')
                    import se.alipsa.gi.console.InOut
                    def io = new InOut()

                Or set up environment-based selection:

                    def isHeadless = GraphicsEnvironment.isHeadless()
                    def inoutClass = isHeadless ?
                        'se.alipsa.gi.console.InOut' :
                        'se.alipsa.gi.fx.InOut'
                    def io = Class.forName(inoutClass).newInstance()

                See: https://github.com/Alipsa/GuiInteraction#headless-mode
            """.stripIndent())
        }
    }

    // Rest of implementation...
}
```

**Option 2: Automatic fallback (more complex)**
```groovy
// gi-fx/src/main/groovy/se/alipsa/gi/fx/InOut.groovy

package se.alipsa.gi.fx

import java.awt.GraphicsEnvironment
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class InOut extends se.alipsa.gi.AbstractInOut {

    private static final Logger log = LogManager.getLogger(InOut.class)
    private final boolean headless

    InOut() {
        this.headless = GraphicsEnvironment.isHeadless()
        if (headless) {
            log.warn("gi-fx initialized in headless mode - some methods will be unavailable")
        }
    }

    @Override
    void display(se.alipsa.matrix.charts.Chart chart, String... titleOpt) {
        if (headless) {
            log.warn("display(Chart) called in headless mode - saving to file instead")
            String title = titleOpt.length > 0 ? titleOpt[0] : "chart"
            File outputFile = new File("${title}.png")
            // Save chart to file
        } else {
            // Normal GUI display
        }
    }

    // Similar pattern for other methods...
}
```

### Recommendation

**Use Option 1 (better error message)** for now because:
- Keeps gi-fx and gi-console separated (single responsibility)
- Clearer for users which module to use
- Simpler to maintain
- Forces explicit choice (headless vs GUI)

**Consider Option 2** for future if:
- Users frequently switch between GUI and headless
- Want seamless environment detection
- Willing to maintain dual-mode logic

### Acceptance Criteria

- ✅ Clear error message when gi-fx used in headless mode
- ✅ Error message includes usage example
- ✅ Points to gi-console as alternative
- ✅ Documentation updated

---

## Task 4: Update Documentation (RECOMMENDED)

### Objective
Document headless mode usage in GuiInteraction README.

### Add to README.md

```markdown
## Headless Mode

For environments without a graphical display (CI/CD, servers, containers), use `gi-console`:

### Installation

```gradle
dependencies {
    implementation 'se.alipsa.gi:gi-console:0.2.0'
}
```

### Usage

```groovy
import se.alipsa.gi.console.InOut

def io = new InOut()

// File operations work normally
def projectDir = io.projectFile("data")

// Display methods save to files instead of showing GUI
io.display(chart, "sales-report")  // Saves to sales-report.png

// Interactive methods require alternatives
// Instead of: def file = io.chooseFile("Select input")
// Use: def file = new File(args[0])  // From CLI argument
```

### Environment Detection

Automatically choose the right implementation:

```groovy
def isHeadless = java.awt.GraphicsEnvironment.isHeadless()
def inoutClass = isHeadless ?
    'se.alipsa.gi.console.InOut' :
    'se.alipsa.gi.fx.InOut'
def io = Class.forName(inoutClass).newInstance()
```

### Method Behavior in Headless Mode

| Method | Headless Behavior |
|--------|-------------------|
| `chooseFile()` / `chooseDir()` | Throws `UnsupportedOperationException` |
| `prompt*()` methods | Returns default value or throws |
| `display(Chart)` | Saves chart to PNG file |
| `view(Matrix)` | Prints to stdout |
| `projectFile()` | Works normally |
| `getContentType()` | Works normally |

### CI/CD Example

```yaml
# GitHub Actions
- name: Run headless script
  run: |
    groovy -Djava.awt.headless=true \
           -cp "gi-console-0.2.0.jar:*" \
           generate-report.groovy
```

### Docker Example

```dockerfile
FROM openjdk:21-slim
COPY gi-console-0.2.0.jar /app/lib/
COPY script.groovy /app/
WORKDIR /app
CMD ["groovy", "-cp", "lib/*", "script.groovy"]
```
```

### Acceptance Criteria

- ✅ README documents headless mode usage
- ✅ Examples for gi-console
- ✅ Environment detection example
- ✅ CI/CD usage examples
- ✅ Method behavior table

---

## Verification Plan

### After Publishing gi-console:0.2.0

**1. Test in Gade project:**
```bash
cd /path/to/gade

# Update HeadlessInOut.java to use gi-console
# (See instructions in phase-1-headless-test-infrastructure.md)

# Run tests
./gradlew test -Dgroups=gui

# Should see:
# - All 285 tests pass
# - HeadlessInOut uses gi-console (not AbstractInOut)
# - Log: "Creating HeadlessInOut for testing"
```

**2. Test standalone:**
```bash
cd /tmp
cat > test-gi-console.groovy <<'EOF'
@Grab(group:'se.alipsa.gi', module:'gi-console', version:'0.2.0')
import se.alipsa.gi.console.InOut

def io = new InOut()
println "InOut class: ${io.class.name}"
println "Project file: ${io.projectFile('test.txt')}"
println "✅ gi-console:0.2.0 working"
EOF

groovy test-gi-console.groovy
# Should output: ✅ gi-console:0.2.0 working
```

**3. Verify Maven Central:**
```bash
# Check artifact is available
curl -s "https://repo1.maven.org/maven2/se/alipsa/gi/gi-console/0.2.0/gi-console-0.2.0.pom" | grep "<version>"
# Should return: <version>0.2.0</version>
```

---

## Priority

**CRITICAL - Task 1:** Publish gi-console:0.2.0
- **Impact:** Blocks Gade Phase 2 implementation
- **Effort:** 1-2 hours (if build setup exists)
- **Benefit:** Enables headless mode for all GuiInteraction users

**HIGH - Task 2:** Improve gi-console implementations
- **Impact:** Better user experience, fewer surprises
- **Effort:** 2-4 hours
- **Benefit:** Functional headless mode (not just stubs)

**MEDIUM - Task 3:** Improve gi-fx error messages
- **Impact:** Better developer experience
- **Effort:** 30 minutes
- **Benefit:** Clearer guidance when error occurs

**MEDIUM - Task 4:** Update documentation
- **Impact:** User adoption
- **Effort:** 1 hour
- **Benefit:** Users understand headless mode

---

## Timeline Estimate

| Task | Effort | Priority | Can Start |
|------|--------|----------|-----------|
| Task 1: Publish gi-console:0.2.0 | 1-2 hours | CRITICAL | Immediately |
| Task 2: Improve implementations | 2-4 hours | HIGH | After Task 1 |
| Task 3: Better error messages | 30 min | MEDIUM | In parallel |
| Task 4: Documentation | 1 hour | MEDIUM | After Task 1 |

**Total: 5-8 hours** for complete headless mode support.

**Minimum viable: Task 1 only (1-2 hours)** - Unblocks Gade immediately.

---

## Questions?

**Contact:** This document generated for Gade project
**Related Files:**
- `docs/improvements/gi-fx-headless-support.md` - Full analysis
- `docs/improvements/phase-1-headless-test-infrastructure.md` - Gade implementation

**Gade Issue:** GUI tests require headless mode support
**Gade Solution:** Phase 1 infrastructure complete, waiting for gi-console:0.2.0

---

**Created:** February 3, 2026
**For:** GuiInteraction project (https://github.com/Alipsa/GuiInteraction)
**Requested by:** Gade project (headless testing requirement)
