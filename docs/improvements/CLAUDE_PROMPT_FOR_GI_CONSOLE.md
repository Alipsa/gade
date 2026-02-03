# Claude Prompt for gi-console Project

Copy and paste this prompt into a new Claude conversation when working on the GuiInteraction project.

---

## PROMPT START

I'm working on the **GuiInteraction** project (https://github.com/Alipsa/GuiInteraction), which provides GUI interaction capabilities for Groovy applications.

### Current Problem

**gi-console:0.2.0 needs to be published to Maven Central.** Currently only version 0.1.0 is available, which depends on gi-common:0.1.0. However, the other modules (gi-fx, gi-swing, gi-common) are all at version 0.2.0, causing dependency conflicts.

**Published versions:**
- ✅ se.alipsa.gi:gi-common:0.2.0 (published)
- ✅ se.alipsa.gi:gi-fx:0.2.0 (published)
- ✅ se.alipsa.gi:gi-swing:0.2.0 (published)
- ❌ se.alipsa.gi:gi-console:0.2.0 (NOT published - this is what's needed)
- ❌ se.alipsa.gi:gi-console:0.1.0 (published but incompatible)

### Task 1: Publish gi-console:0.2.0 (CRITICAL)

**Requirements:**
1. gi-console module must be version 0.2.0
2. Must depend on gi-common:0.2.0 (not 0.1.0)
3. Must implement all methods from `se.alipsa.gi.GuiInteraction` interface
4. Must be published to Maven Central

**Steps:**
1. Navigate to the GuiInteraction project
2. Update gi-console/build.gradle to:
   - Set version = '0.2.0'
   - Depend on gi-common:0.2.0
3. Verify the InOut class exists at: `gi-console/src/main/groovy/se/alipsa/gi/console/InOut.groovy`
4. Build: `./gradlew :gi-console:clean :gi-console:build :gi-console:test`
5. Publish to Maven Central: `./gradlew :gi-console:publishToMavenCentral` (or equivalent)

**Verification:**
```bash
# After 15-30 minutes, check Maven Central:
curl -s "https://search.maven.org/solrsearch/select?q=g:se.alipsa.gi+AND+a:gi-console+AND+v:0.2.0&wt=json" | grep numFound
# Should show numFound > 0
```

### Task 2: Implement Headless-Friendly Methods (RECOMMENDED)

The gi-console InOut class should handle headless environments gracefully:

**Methods that MUST work:**
- File operations: `projectFile()`, `getResourceUrl()`, `urlExists()`, `getContentType()`
- These should work exactly like in gi-fx (no GUI needed)

**Methods that CAN throw exceptions in headless mode:**
- Interactive prompts: `chooseFile()`, `chooseDir()`, `prompt*()` methods
- Should throw `UnsupportedOperationException` with clear message:
  ```groovy
  throw new UnsupportedOperationException(
      "chooseFile() not supported in headless mode. " +
      "Use command-line arguments or config files instead."
  )
  ```

**Methods that should save/print instead of displaying:**
- `display(Chart)` → Save chart to PNG file
- `view(Matrix)` → Print table summary to stdout
- `viewMarkdown(String)` → Print markdown as text

**Example implementation:**
```groovy
package se.alipsa.gi.console

import se.alipsa.gi.AbstractInOut
import se.alipsa.matrix.charts.Chart
import se.alipsa.matrix.core.Matrix
import java.io.File

class InOut extends AbstractInOut {

    // File choosers - throw in headless
    @Override
    File chooseFile(String title, File initialDirectory, String initialFileName, String... extensionFilters) {
        throw new UnsupportedOperationException(
            "chooseFile() not supported in headless mode. Use CLI args or config files."
        )
    }

    // Prompts - return default or throw
    @Override
    String prompt(String headerText, String label, String defaultValue) {
        if (defaultValue != null) {
            println "[gi-console] Using default value for '$headerText': $defaultValue"
            return defaultValue
        }
        throw new UnsupportedOperationException(
            "prompt() requires user input, not available in headless mode"
        )
    }

    // Display - save to file
    @Override
    void display(Chart chart, String... titleOpt) {
        String title = titleOpt.length > 0 ? titleOpt[0] : "chart"
        File outputFile = new File("${title}.png")
        println "[gi-console] Saving chart to: ${outputFile.absolutePath}"
        // TODO: Implement chart save logic
    }

    // View - print to stdout
    @Override
    void view(Matrix tableMatrix, String... title) {
        String t = title.length > 0 ? title[0] : "Table"
        println "[gi-console] $t:"
        println tableMatrix.toString()
    }
}
```

### Task 3: Update README (RECOMMENDED)

Add a "Headless Mode" section to the GuiInteraction README.md documenting:
- When to use gi-console vs gi-fx
- How to use gi-console
- Which methods work in headless mode
- CI/CD and Docker examples

Example section:
````markdown
## Headless Mode

For headless environments (CI/CD, servers, containers), use `gi-console`:

```gradle
dependencies {
    implementation 'se.alipsa.gi:gi-console:0.2.0'
}
```

### Method Behavior

| Method | Headless Behavior |
|--------|-------------------|
| `chooseFile()` / `chooseDir()` | Throws exception (use CLI args) |
| `prompt*()` | Returns default or throws |
| `display(Chart)` | Saves to PNG file |
| `view(Matrix)` | Prints to stdout |
| `projectFile()` | Works normally |

### Environment Detection

```groovy
def isHeadless = java.awt.GraphicsEnvironment.isHeadless()
def inoutClass = isHeadless ?
    'se.alipsa.gi.console.InOut' :
    'se.alipsa.gi.fx.InOut'
def io = Class.forName(inoutClass).newInstance()
```
````

### Context

This is needed by the **Gade project** (a Groovy IDE) which has implemented Phase 1 headless test infrastructure but is blocked waiting for gi-console:0.2.0 to be published.

**Gade's implementation:**
- Created HeadlessInOut class extending AbstractInOut (temporary workaround)
- Created TestGade subclass for test environment
- All 285 tests passing, but waiting to switch to gi-console

**Once gi-console:0.2.0 is published:**
- Gade will update HeadlessInOut to extend gi-console.InOut
- Enables Phase 2: Full production headless mode support
- Use case: Server-side script execution, CI/CD automation

### Priority

**CRITICAL:** Task 1 (publish gi-console:0.2.0) - Blocks Gade Phase 2
**HIGH:** Task 2 (implement methods) - Better UX
**MEDIUM:** Task 3 (documentation) - User adoption

### Questions

1. Does the gi-console module already exist in the GuiInteraction project?
2. What's the current build/publish process for other modules?
3. Are there any tests for gi-console that need to be updated?

Please help me:
1. Publish gi-console:0.2.0 to Maven Central
2. Ensure it's compatible with gi-common:0.2.0
3. Implement headless-friendly method behaviors
4. Update documentation

## PROMPT END

---

**Additional Context Files:**

If Claude needs more details, share these files from the Gade project:
- `docs/improvements/gi-fx-headless-support.md` - Full analysis of the problem
- `docs/improvements/gi-console-publication-instructions.md` - Detailed implementation guide

**Verification Test:**

Once published, test with:
```bash
cat > test.groovy <<'EOF'
@Grab(group:'se.alipsa.gi', module:'gi-console', version:'0.2.0')
import se.alipsa.gi.console.InOut
def io = new InOut()
println "✅ gi-console:0.2.0 working: ${io.class.name}"
EOF
groovy test.groovy
```
