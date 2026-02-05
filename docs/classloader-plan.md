# Classloader Isolation Plan

## Current Status (February 2026)

**PARTIAL IMPLEMENTATION**: Gradle/Maven runtimes use subprocess execution with proper isolation. GADE runtime (in-process) has known limitations with `@Grab` that require subprocess execution to fix properly.

## Issue: Groovy scripts inherit the IDE/application classpath, leading to version conflicts and loader pollution

- Introduce a `ScriptClassLoaderManager` that constructs a root `GroovyClassLoader` with the bootstrap loader as parent and explicitly adds only the jars shipped with Gade.
- Update `Gade` and `ConsoleComponent` to obtain script loaders from the manager rather than creating `GroovyClassLoader` instances directly.
- Ensure dynamic features such as JDBC driver loading reuse the manager so dependencies are appended to the curated loader tree rather than the IDE classpath.
- Add regression tests confirming scripts cannot see IDE-only classes but still access Gade bindings.

## Issue: Allow `@GrabConfig(systemClassLoader=true)` without mutating Gade while keeping scripts isolated and interactive

- Execute Groovy scripts inside an isolated helper process (e.g., a lightweight Java worker launched via `ProcessBuilder`) whose classpath is restricted to Gade resources plus downloaded Grapes.
- Replace the current `InOut` coupling with a transport-agnostic bridge (e.g., RMI) so UI interactions traverse the process boundary while preserving the same API semantics for the UI layer.
- When a script requests `systemClassLoader`, surface that request only inside the worker JVM, keeping the IDE JVM untouched; recycle workers between executions to amortize startup costs while discarding polluted loaders.
- Provide integration tests spawning the worker and verifying that IDE-side classpaths remain unchanged even after a script uses `@GrabConfig(systemClassLoader=true)` yet still manages `InOut` prompts.

## Issue: Loader refactor must remain compatible with Gradle-based dependency hydration and cached JDBC drivers

- Refactor `GradleUtils.addGradleDependencies` to accept the manager or the curated base loader so Gradle output jars are added only to the controlled loader hierarchy.
- Adjust `ConnectionHandler.connect` to request driver URLs through the manager, ensuring drivers are reused without resetting parentage.
- Provide targeted tests simulating resolving a mock driver jar and verifying repeated connections reuse the same loader instance without leaking URLs into the system loader.
- Update documentation describing how Gradle and JDBC dependencies are loaded so the new architecture is transparent to users.

---

## Lessons Learned: @Grab and In-Process Execution (February 2026)

### Problem: @Grab Doesn't Work Properly in GADE In-Process Runtime

**Symptom:**
Scripts using `@Grab` fail with `ClassNotFoundException` even though dependencies are declared:
```groovy
@Grab("tech.tablesaw:tablesaw-core:0.44.1")
@Grab("tech.tablesaw:tablesaw-html:0.44.1")
import tech.tablesaw.api.Table

// Fails: java.lang.ClassNotFoundException: tech.tablesaw.io.html.HtmlWriter
```

### Root Causes Discovered

#### 1. Parent-First Classloader Delegation Conflict

**The Issue:**
- GADE has `tech.tablesaw:tablesaw-core:0.44.4` in its classpath (via `matrix-tablesaw` dependency)
- When a script uses `@Grab("tech.tablesaw:tablesaw-core:0.44.1")`, the `Table` class is loaded from GADE's parent classpath (0.44.4) due to parent-first delegation
- The `Table` class static initializer calls `Class.forName("tech.tablesaw.io.html.HtmlWriter")`
- `Class.forName()` uses the **caller's classloader** (GADE's parent), not the script's classloader
- GADE doesn't have `tablesaw-html` as a dependency, so `HtmlWriter` is not found
- Even though the script's `@Grab` downloaded `tablesaw-html:0.44.1`, it's in a child classloader and can't be found

**Key Insight:**
Parent-first delegation means any class that exists in GADE's classpath will NEVER be loaded from `@Grab` downloads, creating a conflict when the parent has partial dependencies.

#### 2. @Grab Transformation Timing Issues

**The Issue:**
- `@Grab` is an AST transformation that happens during **script compilation**
- Setting `groovy.grape.enable.system.classloader=false` at runtime (in `RuntimeIsolation`) was too late
- The property must be set BEFORE the `GroovyShell` is created
- Solution: Set the property in `GroovyShellEngine` constructor before creating the shell

**Code Location:** `src/main/java/se/alipsa/gade/console/GroovyShellEngine.java:34-36`

#### 3. Complex Classloader Hierarchy

**The Issue:**
- GADE's classloader hierarchy: `GroovyClassLoader` → `gui.dynamicClassLoader` → `AppClassLoader`
- `@Grab` adds JARs to the GroovyClassLoader, but `Class.forName()` delegates to parent first
- Classes loaded from parent can't see child classloader's JARs
- This is a fundamental limitation of parent-first delegation

### Attempted Solutions and Why They Failed

#### ❌ Attempt 1: Exclude tablesaw-core from matrix-tablesaw
```gradle
implementation("se.alipsa.matrix:matrix-all:2.4.1-SNAPSHOT") {
  exclude group: 'tech.tablesaw', module: 'tablesaw-core'
}
```
**Why it failed:** Still had `ClassNotFoundException` because `@Grab` wasn't adding JARs to the classloader properly.

#### ❌ Attempt 2: Child-First Classloader
Created `ChildFirstGroovyClassLoader` that checks child URLs before delegating to parent.

**Code:** `src/main/java/se/alipsa/gade/console/ChildFirstGroovyClassLoader.java` (preserved for future use)

**Why it failed:** `Class.forName()` in `Table`'s static initializer uses the **caller's classloader**, not the thread context classloader. Even with child-first delegation, the static initializer still used parent classloader.

#### ❌ Attempt 3: Set groovy.grape.enable.system.classloader in RuntimeIsolation
**Why it failed:** Property was set too late - after GroovyShell was already created and configured.

### Current Workaround (Temporary)

**Solution:** Add `tablesaw-html` to GADE's dependencies
```gradle
implementation 'tech.tablesaw:tablesaw-html:0.44.4'
```

**Why this works:**
- Now both `tablesaw-core` and `tablesaw-html` are in GADE's parent classpath
- The `Table` class static initializer can find `HtmlWriter`
- No more `ClassNotFoundException`

**Why this is not ideal:**
- ❌ GADE's classpath grows with every script dependency
- ❌ Scripts can't use different versions than GADE (e.g., tablesaw 0.44.1 vs 0.44.4)
- ❌ Defeats the purpose of `@Grab` for dependency isolation
- ❌ Not scalable - can't add every possible `@Grab` dependency to GADE

**Tracking:** See `TODO` comment in `build.gradle:195-197`

### Proper Solution: Subprocess Execution

**The Right Architecture (from original plan):**
Execute scripts with `@Grab` in an isolated subprocess, similar to how Gradle/Maven runtimes work.

**Benefits:**
- ✅ True dependency isolation - script classpath is completely separate from GADE
- ✅ `@Grab` works correctly without parent classloader interference
- ✅ Scripts can use any dependency version
- ✅ `@GrabConfig(systemClassLoader=true)` works without polluting GADE's JVM
- ✅ No need to add script dependencies to GADE's classpath

**Reference Implementation:**
- `src/main/java/se/alipsa/gade/runtime/RuntimeProcessRunner.java` - Subprocess execution
- `src/main/java/se/alipsa/gade/runner/GadeRunnerMain.java` - Subprocess entry point
- Already working for Gradle/Maven runtimes

### Implementation Roadmap

**Phase 1: Detection** ✅ (Can be done now)
- Add detection for `@Grab` annotations in script text
- Prompt user: "This script uses @Grab. Run in subprocess for proper isolation?"
- User can choose in-process (current behavior) or subprocess

**Phase 2: Subprocess @Grab Runtime** (Future work)
- Create new `RuntimeType.GADE_SUBPROCESS`
- Reuse `RuntimeProcessRunner` infrastructure from Gradle/Maven runtimes
- Ensure GUI interactions (`io.view()`, `io.prompt()`, etc.) work across process boundary
- This is already proven to work - just needs GADE runtime variant

**Phase 3: Auto-Detection** (Future enhancement)
- Automatically use subprocess when `@Grab` detected
- Make it seamless for users
- Add preference to always/never use subprocess

### ChildFirstGroovyClassLoader - Preserved for Future Use

**Location:** `src/main/java/se/alipsa/gade/console/ChildFirstGroovyClassLoader.java`

**Status:** Removed from active use but preserved in git history at commit `[current]`

**Why it's still valuable:**
- ✅ Solves dependency version conflicts for libraries that DON'T use `Class.forName()` in static initializers
- ✅ Useful for scripts that import their own versions of libraries
- ✅ Can be used in conjunction with subprocess execution for extra isolation
- ✅ Well-implemented with proper parent-first exclusions for system classes

**When to resurrect it:**
- If we implement in-process script isolation without subprocess
- If we want to allow scripts to override GADE dependency versions (non-`@Grab` case)
- As a defense-in-depth layer for subprocess execution

**Usage pattern:**
```java
// Instead of:
GroovyClassLoader loader = new GroovyClassLoader(parent, config);

// Use child-first:
GroovyClassLoader loader = new ChildFirstGroovyClassLoader(parent, config);
```

**Parent-first exceptions (already configured):**
- `java.*`, `javax.*`, `jdk.*` - JDK classes
- `groovy.lang.*`, `org.codehaus.groovy.*` - Groovy runtime
- `se.alipsa.gade.*`, `se.alipsa.gi.*` - GADE framework classes
- `javafx.*`, `org.apache.logging.log4j.*` - UI and logging

### Critical Gotchas for Future Work

#### 🚨 Class.forName() Uses Caller's Classloader
```java
// In a class loaded from parent classloader:
Class.forName("com.example.MyClass")  // Uses PARENT classloader, not child!
```

**Workaround:**
```java
// Explicitly specify classloader:
Class.forName("com.example.MyClass", true, Thread.currentThread().getContextClassLoader())
```

**Implication:** Libraries that use `Class.forName()` in static initializers (like tablesaw) CANNOT work with child-first classloaders unless ALL their dependencies are in the child.

#### 🚨 Static Initializers Run Once
Once a class is loaded and initialized, its static initializers run ONCE. You can't "reload" a class with different dependencies.

**Implication:** If GADE loads `Table` class from its classpath, scripts can't use a different version, even with child-first classloaders.

#### 🚨 Parent-First is Java Default
Java's classloader delegation is parent-first by design for security and consistency. Child-first is non-standard and can break assumptions.

**Safe approach:** Use child-first for application code, but always parent-first for:
- JDK classes (`java.*`, `javax.*`)
- Groovy runtime (`groovy.lang.*`)
- Your framework classes (`se.alipsa.gade.*`)

#### 🚨 @Grab Transformation Happens at Compile Time
The `@Grab` AST transformation runs during script compilation, not execution.

**Implication:**
- `groovy.grape.enable.system.classloader` must be set BEFORE creating GroovyShell
- Can't be changed per-script, it's a JVM-wide setting
- Scripts compiled with different settings may behave differently

#### 🚨 Ivy Cache is Shared
Grape uses Ivy underneath, which caches downloaded JARs in `~/.groovy/grapes/`.

**Implication:**
- Multiple GADE sessions share the same cache
- Cache corruption can affect all sessions
- Deleting `~/.groovy/grapes/` is sometimes necessary for debugging

### Testing Strategies

When implementing subprocess execution for `@Grab`:

1. **Test dependency isolation:**
   ```groovy
   // Script should use its own version, not GADE's
   @Grab('org.apache.commons:commons-lang3:3.12.0')
   import org.apache.commons.lang3.StringUtils
   assert StringUtils.class.package.implementationVersion == '3.12.0'
   ```

2. **Test @GrabConfig(systemClassLoader=true):**
   ```groovy
   @GrabConfig(systemClassLoader=true)
   @Grab('com.h2database:h2:2.2.224')
   import java.sql.DriverManager
   // Should work in subprocess without polluting GADE
   ```

3. **Test GUI interactions across process boundary:**
   ```groovy
   @Grab('some:library:1.0')
   import some.library.Class
   io.view(someData)  // Should still work
   result = io.prompt("Question")  // Should still work
   ```

4. **Test classloader hierarchy:**
   ```groovy
   @Grab('tech.tablesaw:tablesaw-core:0.44.1')
   import tech.tablesaw.api.Table
   // Should load from @Grab, not from GADE's classpath
   assert Table.class.classLoader.toString().contains('GroovyClassLoader')
   ```

### References

- **ADR 001:** [Separate Process Runtimes](adr/001-separate-process-runtimes.md) - Subprocess execution architecture
- **ADR 002:** [GroovyShell vs JSR223](adr/002-groovyshell-vs-jsr223.md) - Why we use GroovyShell
- **RuntimeProcessRunner:** Working subprocess implementation for Gradle/Maven
- **Groovy Grape Documentation:** https://groovy-lang.org/grape.html

### Related Issues

- **matrix-tablesaw dependency:** Brings in `tablesaw-core` which conflicts with `@Grab`
- **gui.dynamicClassLoader:** Shared classloader for JDBC drivers (can't be changed without breaking connections)
- **InOut GUI interactions:** Must work across process boundary for subprocess execution
