# Runtime Architecture Implementation Plan

This plan implements the target architecture described in [RuntimeArchitecture.md](RuntimeArchitecture.md). It follows the six priorities from Section 11, breaking each into concrete implementation steps with file-level guidance. Acceptance criteria and test gates are defined in the architecture document and not repeated here.

> **Pre-requisite reading:** [RuntimeArchitecture.md](RuntimeArchitecture.md) Sections 1-9 define the target; Section 10 tracks current vs target state; Section 11 lists priorities with acceptance criteria and test gates.

---

## Phase 0: ConnectionInfo Serialization Fix (Priority 0) ✅ COMPLETE

This is a **regression fix** that should be addressed before the other priorities. The subprocess isolation model broke the ability to use `ConnectionInfo` objects returned by `io.dbConnection()` in user scripts.

### Problem

When scripts run in the subprocess (Gradle, Maven, Custom runtimes), all `io` method calls are proxied through `RemoteInOut` → socket → `RuntimeProcessRunner` → real `InOut`. Return values are serialized by `ArgumentSerializer`. The problem:

1. **`ConnectionInfo` has no serialization support** in `ArgumentSerializer`. It falls through to the `toString()` fallback, which returns just the connection name (e.g., `"h2 test"`). Scripts receive a plain String instead of a `ConnectionInfo` object.
2. **Method chaining breaks:** The idiomatic pattern used in all example scripts — `io.dbConnection("name").withPassword("pwd")` or `io.dbConnection("name"); ci.setPassword(pwd)` — throws `MissingMethodException` because `.withPassword()` / `.setPassword()` don't exist on String.
3. **`io.dbConnect()` returns `java.sql.Connection`** which is not serializable. The subprocess receives a useless toString of the Connection object (e.g., `"org.h2.jdbc.JdbcConnection@1a2b3c"`).

**What still works:** String-based convenience methods like `io.dbSelect("connName", "sql")`, `io.dbExecuteSql("connName", "sql")`, `io.dbTableExists("connName", "table")`, and `io.dbCreate("connName", matrix, "pk")` work correctly because they take a String name, execute the full operation in the main process, and return serializable results (Matrix, int, boolean).

### Step 0.1: Add ConnectionInfo Serialization to ArgumentSerializer

`ConnectionInfo` has an `asJson(boolean maskPassword = true)` method. Calling `asJson(false)` serializes all fields including the unmasked password, which is suitable for round-trip serialization over the localhost TCP socket.

**Files to modify:**
- `src/main/java/se/alipsa/gade/runner/ArgumentSerializer.java`:
  - Add a `CONNECTION_INFO_AVAILABLE` check (same pattern as `MATRIX_AVAILABLE`)
  - In `serialize()`: detect `se.alipsa.groovy.datautil.ConnectionInfo` by class name and serialize as a map:
    ```java
    Map<String, Object> map = new HashMap<>();
    map.put("_type", "se.alipsa.groovy.datautil.ConnectionInfo");
    map.put("json", ci.asJson(false));  // unmasked password for round-trip
    ```
  - In `deserialize()`: add a case for `"se.alipsa.groovy.datautil.ConnectionInfo"` that parses the JSON string and reconstructs the object using the `(name, dependency, driver, url, user, password)` constructor
  - Use reflection for serialize/deserialize (like `MatrixSerializer`) to avoid hard dependency on `data-utils` in the runner JAR. `ConnectionInfo` is available in the main process classpath (via `matrix-sql` → `data-utils`) but may or may not be on the subprocess classpath depending on project dependencies.

**Subprocess classpath requirement:** `ConnectionInfo` is a Groovy class from `se.alipsa.groovy:data-utils` — it is compiled Groovy and requires the Groovy runtime to load. It lives in Gade's application classpath (`lib/app/`) but is **not** on the subprocess classpath by default — it is not part of the Groovy/Ivy bootstrap entries, and the Gade runtime doesn't include it. Maven/Gradle runtimes only have it if the user's project happens to depend on `data-utils`.

Since `ConnectionInfo` round-trip (including `.withPassword()` / `.setPassword()` chaining) is the primary goal, `data-utils` must be included in the subprocess `groovyEntries` — the same bootstrap classloader that holds the Groovy jars. This ensures that when `ConnectionInfo` is first loaded, the Groovy runtime classes it depends on are already available on the same classloader. Class loading is lazy, so the ordering of jars within `groovyEntries` does not matter; what matters is that both Groovy and `data-utils` are on the same classloader before any `ConnectionInfo` usage.

The `data-utils` jar must be added to the subprocess classpath for **all four runtime types**. The mechanism differs per type because Gradle/Maven use bootstrap entries while Gade/Custom put everything on `-cp`:

**Files to modify:**
- `src/main/java/se/alipsa/gade/console/GroovyRuntimeManager.java`:
  - **Gradle/Maven:** Update `buildGroovyBootstrapEntries()` to also include the `data-utils` jar alongside Groovy/Ivy jars. Locate it by scanning resolved classloader URLs for `data-utils-*.jar`, or by resolving the `ConnectionInfo` class code source location as a fallback. The jar goes into `groovyEntries` so it ends up on the bootstrap classloader where Groovy is available.
  - **Gade/Custom:** Update `buildClassPathEntries()` to also include the `data-utils` jar on the `-cp`. For these runtimes, Groovy is already on `-cp` (added by `addGroovyJarsFromDir()` / `addGroovyJarsByClassResolution()`), so `data-utils` can be added right after the Groovy jars. Use the same resolution strategy: scan for `data-utils-*.jar` in `lib/groovy/` or resolve via `ConnectionInfo` class code source.
  - Note: `buildGroovyBootstrapEntries()` currently returns empty for Gade/Custom (line 371-372). That is correct — the change for Gade/Custom goes in `buildClassPathEntries()` instead.

### Step 0.2: Handle `io.dbConnect()` via Local Connection Creation

`java.sql.Connection` is a live resource (socket/file handle) that cannot be serialized across process boundaries. Currently `io.dbConnect()` returns a useless `toString()` string in subprocess mode.

**Contract:** `io.dbConnect()` works in subprocess mode by creating the JDBC connection locally in the subprocess. `RemoteInOut` intercepts `dbConnect` calls and handles them specially instead of proxying the full round-trip. This requires the JDBC driver to be available on the subprocess classpath — the subprocess mirrors the main process `ConnectionHandler` behavior of resolving `ci.dependency` before loading the driver.

**Driver dependency resolution:** In the main process, `ConnectionHandler.connect()` resolves `ci.getDependency()` via `GradleUtils.addDependencies()` before loading the driver class. The subprocess must do the equivalent. The approach:
1. When `RemoteInOut` intercepts `dbConnect` and has a `ConnectionInfo` with a non-empty `dependency` field, it proxies a `resolveDependency` request to the main process
2. The main process resolves the dependency using `GradleUtils.addDependencies()` (which downloads the jar if needed and adds it to the dynamic classloader) and returns the resolved jar file paths
3. The subprocess adds those jar URLs to the script classloader via `GroovyClassLoader.addURL()`
4. Now the JDBC driver class is available for `SqlUtil.connect()` to load

For Maven/Gradle runtimes, the JDBC driver may already be on the classpath as a declared project dependency — in that case the resolution step is redundant but harmless (the jar is already present). For Gade/Custom runtimes and cases where the driver is declared only in the Connections tab (not in the build script), this resolution step is essential.

**Password handling:** Password prompting is in `InOut.dbConnect(ConnectionInfo ci)`, not in `InOut.dbConnection(String name)`. `dbConnection()` merely looks up the `ConnectionInfo` from the Connections tab — it never prompts. The subprocess must handle password prompting itself by proxying `io.promptPassword()` to the main process (this already works via the generic RemoteInOut proxy since `promptPassword` returns a String).

**Connection creation via `SqlUtil`:** Instead of reimplementing driver loading and connection creation, use the battle-tested `SqlUtil.connect(ConnectionInfo)` from `data-utils`. Its `driver()` method handles classloader resolution robustly — it tries the caller's classloader, then the thread context classloader, then SqlUtil's own classloader, then `Class.forName` as a final fallback. As long as the thread context classloader is set to the script classloader (which has project dependencies and the resolved driver jar), `SqlUtil.driver()` will find the JDBC driver.

`ConnectionInfo.properties` builds a `Properties` from user/password fields, and `SqlUtil.connect()` calls `driver.connect(url, properties)` directly — no `DriverManager` involved. This is the same pattern as `ConnectionHandler` in the main process.

**Flow for `dbConnect(String name)`:**
1. Script calls `io.dbConnect("name")` in the subprocess
2. `RemoteInOut.invokeMethod()` detects the method name `dbConnect`
3. It proxies `io.dbConnection("name")` to the main process to get the `ConnectionInfo`
4. If `ci.getDependency()` is non-empty, proxy a `resolveDependency(ci.getDependency())` request to the main process; receive back the resolved jar paths; add each as a URL to the script classloader
5. If `ci.getPassword()` is blank **and** the URL does not already contain embedded credentials (same `urlContainsLogin` check as `ConnectionHandler` — looks for `user`+`password` params, `@` symbol, or `integratedSecurity=true`), proxy `io.promptPassword(...)` to the main process for the GUI dialog, then call `ci.setPassword(pwd)`. Skip prompting when the URL carries auth.
6. Create connection: `Connection conn = SqlUtil.connect(ci)` — SqlUtil handles driver loading (trying multiple classloaders), property construction, and `driver.connect()`
7. Return the live `Connection` to the script

**Flow for `dbConnect(ConnectionInfo ci)`:** Same as steps 4-7 above (skip lookup, use the provided `ConnectionInfo` directly).

**Thread context classloader:** `GadeRunnerEngine` must set `Thread.currentThread().setContextClassLoader(scriptClassLoader)` before each eval. This ensures `SqlUtil.driver()` finds JDBC drivers on the script classloader (its second fallback in the classloader chain). This is good practice regardless — many libraries use the context classloader for class discovery.

**Files to modify:**
- `src/main/java/se/alipsa/gade/runner/RemoteInOut.java` — Add special handling in `invokeMethod()` for `dbConnect`. Add a `scriptClassLoader` field (a `GroovyClassLoader`, set by `GadeRunnerEngine`) so resolved dependency jars can be added via `addURL()`:
  ```java
  private GroovyClassLoader scriptClassLoader; // set by GadeRunnerEngine
  public void setScriptClassLoader(GroovyClassLoader cl) { this.scriptClassLoader = cl; }

  // In invokeMethod():
  if (name.equals("dbConnect")) {
      // If arg is a String name, proxy dbConnection(name) to get ConnectionInfo
      // If arg is already a ConnectionInfo, use it directly
      // If ci.dependency is non-empty, proxy resolveDependency() to main process,
      //   add returned jar paths to scriptClassLoader via addURL()
      // Check password; if blank and URL doesn't embed auth, proxy promptPassword()
      // Return SqlUtil.connect(ci)
  }
  ```
  The generic proxy path (for all other methods) remains unchanged.
- `src/main/java/se/alipsa/gade/runtime/RuntimeProcessRunner.java` — Add handling for `resolveDependency` requests. **Threading:** the current `handleGuiRequest()` dispatches all work to the JavaFX thread via `Platform.runLater()`. Dependency resolution involves network I/O (downloading jars) and must not block the FX thread. Add a dedicated code path: when the incoming `gui_request` method is `resolveDependency`, handle it on a background thread (e.g. `CompletableFuture.runAsync()`) instead of dispatching to `Platform.runLater()`. The background thread calls `GradleUtils.addDependencies()`, collects the resolved jar paths, and sends the `gui_response`. Only actual GUI operations (dialogs, FX node manipulation) need the FX thread.
- `src/main/java/se/alipsa/gade/runner/GadeRunnerEngine.java` — Before each `eval`, set `Thread.currentThread().setContextClassLoader(currentLoader)` and call `remoteInOut.setScriptClassLoader(currentLoader)` where `currentLoader` is the `mainLoader` or `testLoader` selected for this eval.

### Step 0.3: Update Example Scripts

**Files to verify/update:**
- `examples/database/src/appendRow.groovy` — uses `io.dbConnection("h2 test")` + `.setPassword()`
- `examples/exportData/src/database.groovy` — uses `io.dbConnection("mydatabase").withPassword(...)`
- `examples/importData/src/database.groovy` — uses `io.dbConnection("mydatabase").withPassword(...)`
- `examples/database/src/updateTable.groovy` — check usage
- `examples/database/src/copyTable.groovy` — check usage

Verify these examples work after the fix. If `ConnectionInfo` round-trips correctly, the existing patterns should work without modification.

### Step 0.4: Test ConnectionInfo and dbConnect

**Test gates — serialization:**
- Unit test: `ArgumentSerializer.serialize(connectionInfo)` produces a map with `_type` and JSON; `deserialize()` reconstructs an equivalent `ConnectionInfo`
- Unit test: `ConnectionInfo` with `null` password serializes/deserializes correctly

**Test gates — dbConnect integration:**
- Integration test: subprocess script calls `io.dbConnection("name")`, receives a usable `ConnectionInfo`, calls `.withPassword()`, and passes it to `io.dbSelect(ci, "sql")` successfully
- Integration test: subprocess script calls `io.dbConnect("name")` and receives a working `java.sql.Connection` (requires JDBC driver resolved onto subprocess classpath)

**Test gates — resolveDependency:**
- Unit test: `resolveDependency` request with a valid dependency returns jar paths and does not block the FX thread (verify execution happens off `Platform.runLater`)
- Unit test: `resolveDependency` with an unresolvable dependency (e.g. bogus groupId) propagates the error back as a `gui_error` response within a reasonable timeout — the subprocess receives a clear error message, not a hang
- Unit test: timeout — if dependency resolution takes longer than a configured limit (e.g. 60s), the request completes with a timeout error rather than blocking the subprocess indefinitely
- Unit test: duplicate/concurrent resolves — two `resolveDependency` requests for the same dependency arriving concurrently both complete successfully without corrupting the classloader (jars added via `addURL()` are idempotent — adding the same URL twice is harmless)
- Unit test: concurrent resolves for different dependencies complete independently without interfering with each other

---

## Phase 1: Subprocess Classloader Infrastructure (Priorities 1-3) ✅ COMPLETE

These three priorities are tightly coupled — they all modify the subprocess classloader setup in `GadeRunnerEngine` and `GadeRunnerMain`. Implementing them together avoids reworking the same code multiple times.

### Priority 1: Main/Test Loader Separation

**Goal:** Maven and Gradle runtimes get separate Main and Test classloaders in the subprocess so test scripts can see main-scope classes but not vice versa.

#### Step 1.1: Protocol Change — Separate Test Dependencies

Currently `addClasspath` sends two lists: `groovyEntries` (Groovy/Ivy jars) and `projectEntries` (all project deps). The subprocess needs to know which entries are test-scope.

**Files to modify:**
- `src/main/java/se/alipsa/gade/console/GroovyRuntimeManager.java` — `buildProjectDepEntries()` currently collects all non-Groovy URLs into one list. Split into two lists: `mainDepEntries` and `testDepEntries`. Update the `addClasspath` message to include three entry lists: `groovyEntries`, `mainEntries`, `testEntries`.
- `src/main/java/se/alipsa/gade/runtime/RuntimeProcessRunner.java` — Update `sendAddClasspath()` to serialize three lists (`groovyEntries`, `mainEntries`, `testEntries`) instead of two. Also add `runtimeType` to the payload so the subprocess knows which delegation mode to use.
- `src/main/java/se/alipsa/gade/runner/GadeRunnerMain.java` — Update `addClasspath` handler to parse three lists and pass them to `GadeRunnerEngine`.
- `src/main/java/se/alipsa/gade/runner/GadeRunnerEngine.java` — Update `run()` signature to accept three lists.

**Key design decision:** How to distinguish main vs test dependencies in the main process:
- **Gradle:** The Tooling API can resolve `compileClasspath` and `testCompileClasspath` separately. `GradleUtils.addGradleDependencies()` already has a `testContext` boolean — extend it to return both scopes in a single call, or call it twice (once for main, once for test) and compute the delta.
- **Maven:** `MavenUtils.resolveDependencies(pom, testContext)` similarly uses a boolean. Call with `false` for main deps and `true` for all deps; test-only deps = all minus main.
- **Gade/Custom:** Send empty `testEntries` — these runtimes use a single classloader.

#### Step 1.2: Add Test Context to Eval Payload

The subprocess needs to know which classloader to use when executing a script. The current `eval` payload only sends `cmd`, `id`, `script`, and `bindings` (see `RuntimeProcessRunner.eval()` at line 165). The subprocess has no way to determine whether a script is a "test" script.

**Files to modify:**
- `src/main/java/se/alipsa/gade/runtime/RuntimeProcessRunner.java` — Add a `testContext` boolean field to the `eval` payload. The `eval()` method signature gains an optional `boolean testContext` parameter (or overload). Current payload: `cmd`, `id`, `script`, `bindings`. New payload adds: `testContext`.
- `src/main/java/se/alipsa/gade/runner/GadeRunnerEngine.java` — Read `testContext` from the `eval` message and select the appropriate classloader (Test Loader if `testContext=true`, Main Loader otherwise).

**Design choice:** Sending a boolean `testContext` is simpler than sending the full source path and replicating test-directory detection logic in the subprocess. The main process is the right place to determine test context since it has access to the build tool and project structure.

**Default behavior:** `testContext` defaults to `false` (use Main Loader) when absent from the payload. Several eval call sites have no source file metadata — `ConsoleComponent.runScript()`, `runScriptSilent()`, and `executeScriptAndReport()` all call `eval()` without a source file. Only `runScriptAsync(script, title, listener, sourceFile)` receives a `File sourceFile` (from `GroovyTab`). When `sourceFile` is `null` or the runtime type is Gade/Custom (which have no test/main separation), `testContext` must default to `false`.

#### Step 1.3: Test Source Directory Detection (Main Process)

The main process must know which source directories are "test" directories so it can set `testContext` when calling `eval`.

**Files to modify:**
- `src/main/java/se/alipsa/gade/console/GroovyRuntimeManager.java` — After resolving dependencies, query the build tool for test source directories:
  - **Gradle:** Use Tooling API to get `sourceSets.test.allSource.srcDirs`
  - **Maven:** Convention-based: `src/test/java`, `src/test/groovy` (or parse from POM if customized)
- Store the resolved test source directories and use them to determine `testContext` when `eval` is called.

#### Step 1.4: Classloader Hierarchy Construction in Subprocess

**Files to modify:**
- `src/main/java/se/alipsa/gade/runner/GadeRunnerEngine.java` — Replace the single `GroovyClassLoader` with a hierarchy:
  ```
  bootstrapLoader (URLClassLoader with Groovy/Ivy jars, parent = platform CL or ProcessRootLoader)
      └── mainLoader (GroovyClassLoader, parent = bootstrapLoader)
              ├── compile-scope + runtime-scope deps
              └── testLoader (GroovyClassLoader, parent = mainLoader)
                      └── test-scope deps
  ```
  - For Gade/Custom runtimes (empty `testEntries`): create only `mainLoader`, skip `testLoader`.
  - `eval` command: use `testContext` from the payload to select `testLoader` or `mainLoader`.

**Incremental testing approach:** Implement the hierarchy with parent-first delegation initially (Priority 2 changes it to parent-last for Maven/Gradle). This lets the hierarchy be validated independently.

#### Step 1.5: Retire `ensureRuntimeContextForSource` Subprocess Restart

With per-eval `testContext` routing (Step 1.2) and dual classloaders in the subprocess (Step 1.4), the current context-switching mechanism — `ensureRuntimeContextForSource()` — is no longer needed. Currently it **stops and restarts the entire subprocess** every time a user switches between a main-scope and test-scope file. This is expensive (~seconds per switch) and loses all subprocess state.

**Files to modify:**
- `src/main/java/se/alipsa/gade/console/GroovyRuntimeManager.java`:
  - Remove the `runtimeTestContext` field and its tracking in `resetClassloaderAndGroovy()` (lines 130-131)
  - Replace `ensureRuntimeContextForSource()` with a lightweight method that computes the `testContext` boolean (reusing `isTestSource()`) and passes it through to the `eval` call, without restarting the subprocess
  - Remove the `isRuntimeTestContext()` accessor (or repurpose it)
- `src/main/java/se/alipsa/gade/console/ConsoleComponent.java`:
  - Update `runScriptAsync()` (line 271): instead of calling `runtimeManager.ensureRuntimeContextForSource(sourceFile, console)` before `executeScriptAndReport()`, pass the computed `testContext` to the eval method

**Note:** `isTestSource()` (line 670) is still useful — it is the test-directory detection logic reused by Step 1.3. Only the subprocess-restart behavior is retired.

### Priority 2: Parent-Last Delegation

**Goal:** Maven/Gradle Main Loader and Test Loader use child-first delegation so project dependencies override Groovy/Ivy jars in the Process Classloader.

#### Step 2.1: Child-First GroovyClassLoader

**Files to create:**
- `src/main/java/se/alipsa/gade/runner/ChildFirstGroovyClassLoader.java` — Extends `GroovyClassLoader` and overrides `loadClass()` to check its own URLs before delegating to the parent. Standard child-first pattern:
  1. Check if already loaded (`findLoadedClass`)
  2. Try to load from own URLs (`findClass`)
  3. Delegate to parent on `ClassNotFoundException`
  4. JDK/platform classes must still delegate to parent first (check `java.*`, `javax.*`, `sun.*` prefixes, or use the platform classloader check)

#### Step 2.2: Wire Delegation Mode Per Runtime Type

**Files to modify:**
- `src/main/java/se/alipsa/gade/runner/GadeRunnerEngine.java` — When constructing classloaders:
  - **Maven/Gradle:** Use `ChildFirstGroovyClassLoader` for `mainLoader` and `testLoader`
  - **Gade/Custom:** Use standard `GroovyClassLoader` (parent-first)
  - The runtime type must be communicated to the subprocess. Options:
    - Add `runtimeType` field to the `addClasspath` message
    - Or infer from the presence/absence of project entries (less robust)
  - Recommended: include `runtimeType` in the protocol message.

### Priority 3: ProcessRootLoader

**Goal:** Custom URLClassLoader with public `addURL()` installed as the system classloader in the subprocess, enabling `@GrabConfig(systemClassLoader=true)`.

#### Step 3.1: Create ProcessRootLoader Class

**Files to create:**
- `src/main/java/se/alipsa/gade/runner/ProcessRootLoader.java` — JDK-only class:
  ```java
  public class ProcessRootLoader extends URLClassLoader {
      // Required by -Djava.system.class.loader contract:
      // must have a public single-arg constructor taking ClassLoader
      public ProcessRootLoader(ClassLoader parent) {
          super(new URL[0], parent);
      }
      @Override
      public void addURL(URL url) { // Make public
          super.addURL(url);
      }
  }
  ```
  Must be packaged in `gade-runner.jar` so it's on the subprocess classpath at JVM startup.

#### Step 3.2: Install as System Classloader

**Files to modify:**
- `src/main/java/se/alipsa/gade/runtime/RuntimeProcessRunner.java` — Add `-Djava.system.class.loader=se.alipsa.gade.runner.ProcessRootLoader` to the subprocess JVM command line arguments.
- `src/main/java/se/alipsa/gade/runner/GadeRunnerMain.java` — Update bootstrap to use `ProcessRootLoader` as the root of the classloader hierarchy instead of a plain `URLClassLoader`. Groovy/Ivy jars are added to `ProcessRootLoader` via its public `addURL()`.

#### Step 3.3: Verify Runner JAR Includes ProcessRootLoader

**Files to verify:**
- `build.gradle` — The `runnerJar` task includes `se/alipsa/gade/runner/**`. Since `ProcessRootLoader` is in the `runner` package, it should be included automatically. Verify this.

#### Step 3.4: Update Classloader Hierarchy

After this change, the subprocess hierarchy becomes:
```
ProcessRootLoader (system CL, starts empty)
    ├── Groovy/Ivy jars (added via addURL after handshake)
    └── mainLoader (GroovyClassLoader, parent = ProcessRootLoader)
            └── testLoader (GroovyClassLoader, parent = mainLoader)
```

**Files to modify:**
- `src/main/java/se/alipsa/gade/runner/GadeRunnerEngine.java` — Change bootstrap loader creation to use the system classloader (`ClassLoader.getSystemClassLoader()`, which will be `ProcessRootLoader`) instead of creating a new URLClassLoader. Add Groovy/Ivy URLs to it via `addURL()`.

---

## Phase 2: Build Tool Home Configuration (Priority 4) ✅ COMPLETE

This phase is independent of Phase 1 — it can be done before or in parallel.

### Priority 4: Maven/Gradle Home Configuration

**Goal:** Users can configure a build tool installation directory for Maven and Gradle runtimes. Wrapper takes precedence over configured home.

#### Step 4.1: Add `buildToolHome` to RuntimeConfig

**Files to modify:**
- `src/main/java/se/alipsa/gade/runtime/RuntimeConfig.java`:
  - Add `private final String buildToolHome` field
  - Add `@JsonProperty("buildToolHome")` to `@JsonCreator` constructor parameter
  - Add `getBuildToolHome()` getter
  - Update `withName()` to preserve `buildToolHome`
  - No migration concern — Gade has never been released (see architecture doc compatibility note)

#### Step 4.2: Add Build Tool Home Field to RuntimeEditorDialog

**Files to modify:**
- `src/main/java/se/alipsa/gade/runtime/RuntimeEditorDialog.java`:
  - Add UI elements: `buildToolHomeLabel`, `buildToolHomeField` (TextField + directory chooser button, similar to existing `groovyHomeField`)
  - Add to the form layout between JVM Home and Groovy Home rows
  - Update `applyTypeVisibility()`: show `buildToolHome` fields for `MAVEN` and `GRADLE` types only; hide for `GADE` and `CUSTOM`
  - Update `populateFields()`: populate from `runtime.getBuildToolHome()`
  - Update `buildRuntimeFromFields()`: read `buildToolHomeField.getText()` and pass to `RuntimeConfig` constructor
  - Set placeholder text: "Leave empty for built-in / wrapper"

#### Step 4.3: Wire buildToolHome Through to GradleUtils

**Files to modify:**
- `src/main/java/se/alipsa/gade/runtime/RuntimeClassLoaderFactory.java` — In `createGradleClassLoader()`, pass `runtime.getBuildToolHome()` as the `gradleInstallationDir`:
  ```java
  // Current (line 165-169):
  var gradleUtils = new GradleUtils(null, projectDir, ...);
  // Change to:
  String buildToolHome = runtime.getBuildToolHome();
  File gradleInstDir = (buildToolHome != null && !buildToolHome.isBlank())
      ? new File(buildToolHome) : null;
  var gradleUtils = new GradleUtils(gradleInstDir, projectDir, ...);
  ```
  Note: `GradleUtils` already accepts `gradleInstallationDir` — no change needed in `GradleUtils.java`.

#### Step 4.4: Reorder GradleDistributionManager to Wrapper-First

**Files to modify:**
- `src/main/java/se/alipsa/gade/utils/gradle/GradleDistributionManager.java` — In `configureDistribution()`, change the order from INSTALLATION → WRAPPER → EMBEDDED to **WRAPPER → INSTALLATION → EMBEDDED**:
  - Move the wrapper check (with `wrapperAvailable` guard) before the installation check
  - This is approximately a 4-line reorder

#### Step 4.5: Wire buildToolHome Through to MavenClasspathUtils

This step depended on the `se.alipsa.mavenutils` library being enhanced to accept an optional Maven home and Maven wrapper.

**Files modified:**
- `src/main/java/se/alipsa/gade/utils/maven/MavenClasspathUtils.java`:
  - Updated `addPomDependenciesTo()` overload to accept optional `mavenHome`
  - Passes `MavenExecutionOptions(projectDir, configuredOrBundledHome, preferWrapper=true)` into MavenUtils dependency resolution
  - Wrapper/home/default selection is now delegated to MavenUtils and logged through selected distribution metadata
- `src/main/java/se/alipsa/gade/runtime/RuntimeClassLoaderFactory.java` — In `createMavenClassLoader()`, pass `runtime.getBuildToolHome()` to `MavenClasspathUtils`
- `src/main/java/se/alipsa/gade/utils/maven/MavenBuildUtils.java`:
  - Uses the same `MavenExecutionOptions` for `runMaven(...)`
- `src/main/java/se/alipsa/gade/code/maven/MavenTab.java`:
  - Passes selected runtime `buildToolHome` to `MavenBuildUtils`

**Status:** Fully implemented. Maven runtime now uses wrapper-first distribution selection, honors configured Maven home, and falls back to bundled/default Maven.

---

## Phase 3: Distribution & Validation (Priorities 5-6) ✅ COMPLETE

These are lower priority and depend on decisions about distribution size and Gradle/Maven version bundling.

### Priority 5: Bundled Build Tool Distributions

**Goal:** Ship Gradle and Maven distributions in `lib/gradle/` and `lib/maven/` so all runtime types work out of the box.

#### Step 5.1: Gradle Distribution Bundling

**Files to modify:**
- `build.gradle` — Add a task to download and include a Gradle distribution:
  - Option A: Download a Gradle binary distribution (zip) from `services.gradle.org` during build and extract to `lib/gradle/`
  - Option B: Use the Gradle wrapper's distribution URL to determine the version and bundle it
  - Add to the `runtime` task's `doLast` block: copy/extract the Gradle distribution into `build/image/gade-*/lib/gradle/`
  - Consider distribution size impact (Gradle binary is ~120MB; minimal distribution may suffice)

**Files to modify (once bundled):**
- `src/main/java/se/alipsa/gade/utils/gradle/GradleDistributionManager.java` — Update `EMBEDDED` mode: instead of `connector.useGradleVersion(current)`, use `connector.useInstallation(libGradleDir)` pointing to `lib/gradle/`. Detect the `lib/gradle/` path relative to the application installation directory.

#### Step 5.2: Maven Distribution Bundling

**Files to modify:**
- `build.gradle` — Similar to Gradle: download a Maven binary distribution and extract to `lib/maven/`
  - Maven binary is ~10MB (much smaller than Gradle)
  - Add to the `runtime` task's `doLast` block

**Files modified:**
- `src/main/java/se/alipsa/gade/utils/maven/MavenClasspathUtils.java` — Built-in mode now uses `lib/maven/` as fallback when no wrapper or configured home is available (via MavenUtils execution options).

**Status:** Runtime distributions now bundle Maven under `lib/maven/`, and Maven runtime resolution/build now uses wrapper → configured home → bundled/default via MavenUtils execution options.

#### Design Consideration: Distribution Size

Bundling both Gradle (~120MB) and Maven (~10MB) significantly increases distribution size. Consider:
- Shipping a minimal Gradle distribution (bin-only, no docs/samples)
- Making bundled distributions optional (separate download or build flag)
- Downloading on first use rather than bundling (trade-off: no longer "out of the box" without network)

### Priority 6: Tooling API Version Validation

**Goal:** Warn users when a configured Gradle version may be incompatible with the bundled Tooling API.

#### Step 6.1: Add Version Constants

**Files to create or modify:**
- `src/main/java/se/alipsa/gade/utils/gradle/GradleCompatibility.java` (new class):
  ```java
  public class GradleCompatibility {
      public static final String MIN_SUPPORTED_GRADLE = "5.0";
      public static final String MAX_SUPPORTED_GRADLE = "9.99";

      public static boolean isSupported(String version) { ... }

      /**
       * Extract Gradle version from an installation directory.
       * Tries lib/gradle-core-*.jar filename first, then falls
       * back to running bin/gradle --version (async, 5s timeout).
       */
      public static CompletableFuture<String> extractVersion(File gradleHome) { ... }

      /**
       * Extract Gradle version from wrapper properties distributionUrl.
       */
      public static String extractVersionFromWrapper(File wrapperProperties) { ... }
  }
  ```

#### Step 6.2: Add Validation to RuntimeEditorDialog

**Files to modify:**
- `src/main/java/se/alipsa/gade/runtime/RuntimeEditorDialog.java`:
  - When the user selects a directory via the Build Tool Home chooser and the runtime type is `GRADLE`:
    1. Call `GradleCompatibility.extractVersion()` asynchronously
    2. When the result arrives, check `GradleCompatibility.isSupported()`
    3. If outside range, show a non-blocking warning label below the field (e.g. "Warning: Gradle X.Y may not be compatible with the bundled Tooling API (supported: 5.0-9.x)")
  - For wrapper detection: when the dialog loads and the project has a wrapper, extract the version from `gradle-wrapper.properties` and validate similarly

---

## Implementation Order Summary

```
Phase 0 (Regression Fix) ✅ COMPLETE
  └── Priority 0: ConnectionInfo Serialization ← DONE

Phase 1 (Subprocess Classloader Infrastructure) ✅ COMPLETE
  ├── Priority 3: ProcessRootLoader          ← DONE
  ├── Priority 1: Main/Test Loader Separation ← DONE
  └── Priority 2: Parent-Last Delegation      ← DONE

Phase 2 (Build Tool Home Configuration) ✅ COMPLETE
  └── Priority 4: Maven/Gradle Home Config    ← Fully implemented (wrapper/configured/bundled flow active)

Phase 3 (Distribution & Validation) ✅ COMPLETE
  ├── Priority 5: Bundled Distributions       ← Implemented in runtime packaging
  └── Priority 6: Tooling API Validation      ← Implemented in RuntimeEditorDialog + GradleCompatibility
```

**Recommended start:** Priority 0 (ConnectionInfo fix) should be done first as it is a regression. After that, Priority 3 (ProcessRootLoader) and Priority 4 (Build Tool Home Config) can be done in parallel since they touch different parts of the system.

---

## Dependencies on External Libraries

| Dependency | What's Needed | Blocks |
|-----------|---------------|--------|
| `se.alipsa.mavenutils` | Wrapper/home selection APIs (`MavenExecutionOptions`) | No longer blocking (implemented) |

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `data-utils` jar missing from subprocess classpath | Medium | High | Include `data-utils` in `groovyEntries` (Gradle/Maven) and `-cp` (Gade/Custom); verify subprocess classpath contains `data-utils` by checking class availability in integration tests |
| Child-first classloader breaks Groovy internals (e.g. MOP, category handling) | Medium | High | Extensive testing with real Groovy features; exclude `groovy.*` from child-first override |
| ProcessRootLoader conflicts with Groovy's own RootLoader detection | Low | Medium | Test `@Grab` and `@GrabConfig` thoroughly; Groovy checks `instanceof RootLoader` in some code paths |
| Bundled Gradle distribution too large for distribution | High | Low | Ship bin-only distribution; or download-on-first-use |
| MavenUtils library enhancement delayed | Medium | Low | Gradle-side works without it; Maven Build Tool Home field deferred but visible in UI |

---

## Files Modified Per Priority

| File | P0 | P1 | P2 | P3 | P4 | P5 | P6 |
|------|----|----|----|----|----|----|----|
| `ArgumentSerializer.java` | X | | | | | | |
| `RemoteInOut.java` | X | | | | | | |
| `GroovyRuntimeManager.java` | X | X | | | | | |
| `ConsoleComponent.java` | | X | | | | | |
| `RuntimeProcessRunner.java` | X | X | | X | | | |
| `RuntimeConfig.java` | | | | | X | | |
| `RuntimeEditorDialog.java` | | | | | X | | X |
| `RuntimeClassLoaderFactory.java` | | | | | X | | |
| `GadeRunnerMain.java` | | X | | X | | | |
| `GadeRunnerEngine.java` | X | X | X | X | | | |
| `ChildFirstGroovyClassLoader.java` (new) | | | X | | | | |
| `ProcessRootLoader.java` (new) | | | | X | | | |
| `GradleCompatibility.java` (new) | | | | | | | X |
| `GradleDistributionManager.java` | | | | | X | X | |
| `MavenClasspathUtils.java` | | | | | X | X | |
| `GradleUtils.java` | | | | | | | |
| `build.gradle` | | | | | | X | |
