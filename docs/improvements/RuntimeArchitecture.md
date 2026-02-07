# Gade Runtime Architecture Specification

## Overview

Gade executes user scripts that may have dependencies conflicting with the IDE itself. This architecture isolates user code from Gade's application classpath while supporting multiple build systems (Gradle, Maven) and custom Groovy installations.

**Key Goals:**
- Complete isolation between Gade application classes and user scripts
- Support for project-specific dependency versions that may differ from Gade's bundled libraries
- Dynamic environment reset without full application restart
- Seamless GUI interaction from isolated scripts
- User-configurable JVM, Groovy home, and build tool home (Gradle/Maven installation directory) per runtime, with wrapper-first selection for build tools
- Out-of-the-box experience: all runtime types work without external tool installations

**Out-of-the-Box Support (Target):** The target is for Gade to ship with everything needed to run all four runtime types without requiring the user to install external tools:
- **Gade runtime**: Bundled Groovy and Ivy jars (`lib/groovy/`) — *already available*
- **Gradle runtime**: Bundled Gradle Tooling API for dependency resolution, plus a bundled Gradle distribution (`lib/gradle/`) as the embedded fallback — *`lib/gradle/` not yet bundled; currently falls back to the Tooling API's embedded version*
- **Maven runtime**: Bundled `se.alipsa.mavenutils` library for dependency resolution, plus a bundled Maven distribution (`lib/maven/`) as the built-in fallback — *`lib/maven/` not yet bundled; currently uses `MavenUtils` library directly*
- **Custom runtime**: Falls back to bundled Groovy when no Groovy Home is configured — *already available*

When a project has a wrapper (Gradle or Maven), the wrapper's pinned version takes precedence over the bundled distribution.

> **Reading guide:** Sections 1–9 describe the **target architecture**. Features already implemented are noted as "Done" in [Section 10 – Current State vs Target](#10-current-state-vs-target); items still under development are marked "TODO" there. Where the current implementation differs from the target design, Section 10 documents the gap.

> **Note on compatibility:** Gade has not yet been released. There are no published versions, no external users, and no persisted configuration files to maintain backward compatibility with. This means data model changes (e.g. adding fields to `RuntimeConfig`), protocol changes, and API changes can be made freely without migration concerns.

---

## 1. Process Hierarchy & Communication

**Isolation Model:** Every Groovy execution occurs within a dedicated **Runtime Process** (subprocess). This provides complete classpath isolation from the Gade application.

**Communication:** The Runtime Process loads a **Runner JAR** (`gade-runner.jar`) that establishes TCP socket communication with the Gade GUI process.

**Runner JAR Constraints:**
- Must have **zero third-party dependencies** (JDK-only) for the bootstrap classes
- Contains both JDK-only bootstrap code and Groovy-dependent engine code
- Groovy-dependent classes are loaded only after the classloader hierarchy is established

**Process Classloader:** A custom `ProcessRootLoader` (extends URLClassLoader with public `addURL()`) serves as the root of the subprocess hierarchy. Initially contains only the JDK and Runner JAR; Groovy jars are added dynamically after the handshake. Has no application-level parent, acting as the effective System Classloader for the runtime. Installed via `-Djava.system.class.loader=...ProcessRootLoader` on the subprocess JVM command line so that `@GrabConfig(systemClassLoader=true)` resolves to this loader.

---

## 2. Classloading Strategy

Gade uses a **Hierarchical Classloader** pattern within each subprocess: a Process Classloader holds Groovy/Ivy jars, while child loaders (Main Loader, Test Loader) hold project dependencies. When the environment needs to be rebuilt, the entire subprocess is restarted.

### Delegation Modes

| Runtime Type | Delegation | Rationale |
|--------------|------------|-----------|
| **Gade/Custom** | Parent-First | Standard Java delegation; user adds deps via `@Grab` |
| **Maven/Gradle** | Parent-Last (Child-First) | Project dependencies override Process Classloader libraries |

### Classloader Chain

```
Process Classloader (ProcessRootLoader extends URLClassLoader)
├── JDK classes
├── Runner JAR (initially)
├── Groovy/Ivy jars (added via addURL after handshake)
│
└── Main Loader (GroovyClassLoader)
        ├── compile-scope dependencies
        ├── runtime-scope dependencies
        └── project build directories (classes/)
            │
            └── Test Loader (GroovyClassLoader)
                ├── test-scope dependencies
                └── test build directories (test-classes/)
```

**Key Properties:**
- Main Loader parent = Process Classloader
- Test Loader parent = Main Loader (so test scripts can see main-scope classes, but main scripts cannot see test dependencies)
- Scripts execute in Main Loader or Test Loader based on source location

**Test/Main Isolation Scope:**
- Test/main separation only applies to **Maven and Gradle** runtimes, where the build tool defines distinct source sets with separate dependency scopes. Gade and Custom runtimes have a single classloader with no test/main distinction.
- Gade determines whether a file is a "test" file by querying the build tool for its configured test source directories (e.g. Maven's `src/test/java`, `src/test/groovy`; Gradle's `sourceSets.test`). Files under a recognized test source directory use the Test Loader; all others use the Main Loader.

---

## 3. Runtime Type Definitions

| Runtime Type | Classpath Construction                            | Dependency Injection            | Build Tool Home Resolution |
|--------------|---------------------------------------------------|---------------------------------|----------------------------|
| **Gade**     | Fixed to Gade's bundled Groovy/Ivy versions       | Dynamic via `@Grab`             | N/A                        |
| **Maven**    | Resolved from `pom.xml` via `MavenClasspathUtils` | `addURL()` to Main/Test Loaders | Built-in `MavenUtils` library (no configurable home yet) |
| **Gradle**   | Resolved from `build.gradle` via `GradleUtils`    | `addURL()` to Main/Test Loaders | Wrapper → Configured Home → Embedded Tooling API (via `GradleDistributionManager`) |
| **Custom**   | User-defined Java Home, Groovy Home, jars         | Process Classloader + `@Grab`   | N/A                        |

**User Environment** (per runtime type — what the user's scripts see):

| Runtime Type | Project Dependencies | Compilation Output Directories |
|--------------|---------------------|-------------------------------|
| **Gade**     | None (use `@Grab`)  | `build/classes/{groovy,java}/{main,test}/` (if enabled via preference; all added to a single classloader — no main/test scope separation) |
| **Maven**    | Resolved from `pom.xml` by `MavenUtils` | `target/classes`, `target/test-classes` |
| **Gradle**   | Resolved from `build.gradle` by Gradle Tooling API | `build/classes/{groovy,java}/{main,test}/` (resolved by Tooling API) |
| **Custom**   | Configured additional jars + `@Grab` | None |

For Maven and Gradle runtimes, Gade's bundled Groovy/Ivy jars are added as a fallback only when the project's own dependencies do not include a Groovy runtime.

---

## 3a. Maven & Gradle Runtime Configuration

### Build Tool Home Selection

When Gade resolves project dependencies for Maven or Gradle runtimes, it must locate the build tool itself. Each build tool has a resolution order that determines which distribution to use.

#### Gradle

Gradle distribution selection is managed by `GradleDistributionManager`, which maintains an ordered fallback list. The **target** resolution order is:

1. **Project Wrapper** — If `gradle/wrapper/gradle-wrapper.properties` exists in the project directory, use `connector.useBuildDistribution()`. The wrapper pins a specific Gradle version for reproducible builds and should always take precedence.
2. **Configured Installation** — If a `gradleInstallationDir` is provided and exists, use `connector.useInstallation(dir)`.
3. **Embedded (Tooling API)** — Fall back to `connector.useGradleVersion(GradleVersion.current().getVersion())`, using the Gradle version bundled with the Tooling API in Gade's application classpath.

If a distribution fails to connect (e.g. network issues downloading wrapper distribution), `GradleDistributionManager.tryNextDistribution()` automatically advances to the next option in the list.

> **Current implementation note:** `GradleDistributionManager.configureDistribution()` currently places INSTALLATION before WRAPPER. This must be changed to wrapper-first to match the target order. Additionally, `GradleUtils` receives `gradleInstallationDir` as a constructor parameter that is currently always `null` because `RuntimeEditorDialog` has no Gradle Home field — so the effective current order is Wrapper → Embedded, which already matches the target for the common case.

**Tooling API Compatibility:** The Gradle Tooling API version bundled with Gade constrains the range of Gradle versions that can be used. Very old or very new Gradle versions may be incompatible.

Validation approach:
- **Version extraction:** For a configured `buildToolHome`, read the Gradle version from `<buildToolHome>/lib/gradle-core-*.jar` filename. If the jar naming doesn't match (layout varies across Gradle versions), fall back to running `<buildToolHome>/bin/gradle --version` and parsing the output. This fallback must be async and time-bounded (e.g. 5-second timeout) to avoid stalling the UI. For a project wrapper, parse the version from the `distributionUrl` property in `gradle/wrapper/gradle-wrapper.properties` (no process needed).
- **Compatibility range:** Maintain a hardcoded minimum and maximum supported Gradle version as constants in the codebase (e.g. `MIN_SUPPORTED_GRADLE = "5.0"`, `MAX_SUPPORTED_GRADLE = "9.99"`). These constants must be updated and tested when the Tooling API dependency is upgraded. `GradleVersion.current().getBaseVersion()` gives the bundled Tooling API's own Gradle version, which can serve as the initial reference point for setting these bounds.
- **Behavior:** If the detected version falls outside the supported range, display a non-blocking warning in `RuntimeEditorDialog`. The user can proceed despite the warning.

#### Maven

Maven supports a project wrapper (`mvnw` / `mvnw.cmd` + `.mvn/wrapper/maven-wrapper.properties`) that pins a specific Maven version per project, analogous to the Gradle wrapper. The target resolution order mirrors Gradle's:

1. **Project Wrapper** — If the project contains a Maven wrapper (`mvnw` / `mvnw.cmd` + `.mvn/wrapper/maven-wrapper.properties`), use the wrapper's pinned Maven version for dependency resolution. **Currently not implemented** — Gade tracks wrapper files in the classpath cache fingerprint but does not use them for dependency resolution. The `se.alipsa.mavenutils.MavenUtils` library should be enhanced to accept and use a Maven wrapper, so that Gade can delegate wrapper handling to MavenUtils rather than shelling out to `mvnw` directly.
2. **Configured Home** — If a Maven home directory is configured in the runtime settings, use that installation for dependency resolution. **Currently not implemented** — no Maven home field exists in `RuntimeEditorDialog`. `MavenUtils` should be enhanced to accept an optional Maven home directory.
3. **Built-in** — `MavenClasspathUtils` instantiates `new MavenUtils()` and calls `resolveDependencies(pom, testContext)` using Gade's bundled `se.alipsa.mavenutils` library. If this fails, a fallback to the simpler `MavenResolver.addPomDependenciesTo()` is attempted. This is the only mode currently in use.

### User Environment vs Build Environment

Gade maintains a conceptual separation between two environments:

**Build Environment** — The classpath and tools needed to *run the build tool itself*:
- **Gradle**: The Gradle Tooling API jars (`org.gradle:gradle-tooling-api` and transitive dependencies) live in Gade's application classpath (`lib/app/`). They are used by `GradleUtils` to invoke the Tooling API and resolve project dependencies. *Target:* a bundled Gradle distribution (`lib/gradle/`) will provide the embedded fallback when no wrapper or configured home is available. These jars are never added to the subprocess classpath.
- **Maven**: The `se.alipsa.mavenutils` library and its transitive dependencies live in Gade's application classpath. `MavenClasspathUtils` uses them to resolve `pom.xml` dependencies. *Target:* a bundled Maven distribution (`lib/maven/`) will provide the built-in fallback when no wrapper or configured home is available. These jars are never added to the subprocess classpath.

**User Environment** — The classpath for *executing user scripts* in the subprocess:
- Dependencies declared in the build script (`pom.xml` / `build.gradle`), resolved by the build tool
- Compilation output directories:
  - Maven: `target/classes`, `target/test-classes`
  - Gradle: `build/classes/groovy/main/`, `build/classes/groovy/test/`, `build/classes/java/main/`, `build/classes/java/test/`
- Groovy/Ivy runtime jars — from the project's own dependencies if present, or Gade's bundled version as fallback (jars from `lib/groovy/`)

This separation is enforced structurally: build tool jars reside in `lib/app/` (the Gade application classpath), while the subprocess classpath is constructed exclusively from resolved project dependencies, compilation outputs, and Groovy/Ivy jars.

### RuntimeConfig Data Model

`RuntimeConfig` currently holds: `name`, `type`, `javaHome`, `groovyHome`, `additionalJars`, `dependencies`. It is serialized via Jackson JSON and persisted per project directory through `RuntimePreferences`.

To support build tool home configuration, `RuntimeConfig` must be extended with a `buildToolHome` field. This field carries the user's configured directory from `RuntimeEditorDialog` through to:
- `GradleUtils` (as the `gradleInstallationDir` constructor parameter)
- `MavenClasspathUtils` / `MavenUtils` (as an optional Maven home)

The field should be `null` or empty when the user wants the default behavior (wrapper → built-in).

### RuntimeEditorDialog Gaps

The current `RuntimeEditorDialog` exposes the following fields per runtime type:

| Field | Gade | Maven | Gradle | Custom |
|-------|------|-------|--------|--------|
| Name | Read-only | Editable | Editable | Editable |
| Type | Read-only | Editable | Editable | Editable |
| JVM Home | Hidden | Editable | Editable | Editable |
| Groovy Home | Hidden | Hidden | Hidden | Editable |
| Additional JARs | Hidden | Hidden | Hidden | Editable |
| Dependencies | Hidden | Hidden | Hidden | Editable |
| **Build Tool Home** | N/A | **Missing** | **Missing** | N/A |

**Target:** Maven and Gradle runtimes should expose a **Build Tool Home** field (directory chooser). The default value should be empty, meaning "Built in" (use Gade's bundled distribution from `lib/gradle/` or `lib/maven/` once those are included in the distribution; currently falls back to Tooling API embedded version for Gradle and `MavenUtils` for Maven). If a project has a wrapper, the wrapper takes precedence over both the configured home and the built-in distribution. For Gradle, the dialog should also validate compatibility with the Tooling API (see below).

---

## 4. Socket Communication Protocol

### Transport Layer
- TCP socket over localhost (127.0.0.1)
- Subprocess binds to available port via `ServerSocket(0)`
- Main process connects with retry logic (50 attempts, 50ms intervals)

### Message Format
Single-line XML protocol using `ProtocolXml` (JDK-only, no external dependencies):

```xml
<msg>
  <e k="cmd">eval</e>
  <e k="id">uuid-here</e>
  <e k="script">println 'hello'</e>
  <e k="bindings"><map><e k="x" t="int">42</e></map></e>
</msg>
```

### Message Types

Each command from Gade has a defined expected response from the Runner:

| Direction     | Command                        | Expected Response      | Purpose                                |
|---------------|--------------------------------|------------------------|----------------------------------------|
| Gade → Runner | `addClasspath`                 | `classpathAdded`       | Send dependency URLs; Runner builds classloader hierarchy and acknowledges |
| Gade → Runner | `eval`                         | `result` or `error`    | Execute a script; Runner returns the result or error. May also send `out`/`err` during execution |
| Gade → Runner | `interrupt`                    | `interrupted`          | Cancel running script; Runner interrupts the eval thread and acknowledges |
| Gade → Runner | `bindings`                     | `bindings`             | Fetch current script variable bindings; Runner serializes `Binding` variables and returns them |
| Gade → Runner | `setWorkingDir`                | `result`               | Change the subprocess `user.dir` system property |
| Gade → Runner | `shutdown`                     | `shutdown` (best-effort) | Graceful subprocess termination; Runner emits acknowledgment and exits. The main process calls `process.destroy()` immediately after sending, so the acknowledgment may not be received. |

Asynchronous protocol messages (not direct command/response pairs):

| Direction     | Message                        | Purpose                                |
|---------------|--------------------------------|----------------------------------------|
| Runner → Gade | `hello`                        | Handshake: sent immediately on startup with protocol version |
| Runner → Gade | `out` / `err`                  | Captured stdout/stderr from running script |
| Bidirectional | `gui_request` / `gui_response` | Remote InOut method invocation (Runner requests, Gade responds) |
| Runner → Gade | `gui_error`                    | Error during GUI proxy invocation (e.g. serialization failure or unexpected exception in RemoteInOut) |

### Protocol Versioning

The `hello` message includes a protocol version number (`ProtocolVersion`). The main process validates that the Runner's version matches before proceeding. Since Gade has not yet been released, there is no external backward-compatibility requirement — the Runner JAR is always built and shipped together with the main process, so the versions always match.

### Startup Handshake Sequence

```
1. Subprocess starts → sends "hello" with protocol version
2. Main process validates version matches
3. Main process sends "addClasspath" with groovyEntries + projectEntries
4. Subprocess builds classloader hierarchy
5. Subprocess sends "classpathAdded" acknowledgment
6. Main process enters command loop (ready for eval/interrupt/shutdown)
```

---

## 5. Runner JAR Structure (`gade-runner.jar`)

Located at `lib/runtimes/gade-runner.jar` in distributions.

| Class               | Dependencies | Purpose                                                   |
|---------------------|--------------|-----------------------------------------------------------|
| `GadeRunnerMain`    | JDK only     | Subprocess entry point; bootstrap before Groovy loads     |
| `ProcessRootLoader` | JDK only     | URLClassLoader subclass with public `addURL()`            |
| `ProtocolXml`       | JDK only     | XML serialization for socket protocol                     |
| `ProtocolVersion`   | JDK only     | Version negotiation                                       |
| `GadeRunnerEngine`  | Groovy       | Script evaluation engine; loaded after classloaders ready |
| `RemoteInOut`       | Groovy       | GUI proxy; forwards InOut calls over socket               |

**Bootstrap Sequence:**
1. `GadeRunnerMain.main()` starts with `ProcessRootLoader` as system classloader (no Groovy imports)
2. Receives classpath entries over socket (Groovy jars, project deps)
3. Adds Groovy/Ivy jars to ProcessRootLoader via `addURL()`
4. Loads `GadeRunnerEngine` via reflection from ProcessRootLoader
5. Invokes `GadeRunnerEngine.run()` which uses Groovy classes

---

## 6. Key Orchestration Classes

There are two separate JVM processes with distinct classloader responsibilities:

**Main Process (Gade IDE)** — resolves dependencies and launches the subprocess:

| Class                       | Location   | Responsibility                                                          |
|-----------------------------|------------|-------------------------------------------------------------------------|
| `GroovyRuntimeManager`      | `console/` | Orchestrates runtime lifecycle; triggers dependency resolution; starts subprocess |
| `RuntimeClassLoaderFactory` | `runtime/` | Runs in the main process to resolve dependencies: creates a temporary `GroovyClassLoader` to collect dependency URLs from build tools (`GradleUtils`, `MavenClasspathUtils`). The URLs are then sent to the subprocess; the temporary classloader itself is not used for script execution. |
| `RuntimeProcessRunner`      | `runtime/` | Manages subprocess lifecycle: start JVM, connect via socket, send commands, handle responses |

**Subprocess (Runtime Process)** — executes user scripts:

| Class                       | Location   | Responsibility                                                          |
|-----------------------------|------------|-------------------------------------------------------------------------|
| `GadeRunnerMain`            | `runner/`  | Subprocess entry point; bootstraps before Groovy is loaded              |
| `GadeRunnerEngine`          | `runner/`  | Receives dependency URLs over socket, builds the subprocess classloader hierarchy (Process Classloader → Main Loader → Test Loader), and executes scripts |

---

## 7. GUI Interaction (RemoteInOut)

Scripts running in the subprocess cannot access JavaFX directly. The `RemoteInOut` class proxies all `InOut` method calls:

```
Script calls io.prompt("Name?")
    ↓
RemoteInOut serializes call as gui_request
    ↓
Socket sends to main process
    ↓
RuntimeProcessRunner invokes real InOut on JavaFX thread
    ↓
Result serialized as gui_response
    ↓
RemoteInOut returns value to script
```

---

## 8. Advanced Configuration

### ProcessRootLoader
The Process Classloader must support `addURL()` to enable `@GrabConfig(systemClassLoader=true)`. Since `URLClassLoader.addURL()` is protected, we provide a custom `ProcessRootLoader` class:

```java
public class ProcessRootLoader extends URLClassLoader {
    public ProcessRootLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    public void addURL(URL url) {  // Make public
        super.addURL(url);
    }
}
```

**Why not Groovy's RootLoader?** Using Groovy's RootLoader would require loading Groovy classes before the user's project dependencies, forcing a specific Groovy version. With ProcessRootLoader:
1. Subprocess starts with only JDK + Runner JAR
2. User's Groovy version (from Maven/Gradle) is added via `addURL()` after handshake
3. No Groovy version is baked into the bootstrap

ProcessRootLoader must be included in `gade-runner.jar` and remain JDK-only.

### Environment Reset
When the runtime environment needs to be rebuilt (e.g. after a dependency change, configuration change, or user request), `resetClassloaderAndGroovy()` stops the current subprocess and creates a new one. This handles all changes uniformly — whether the user changed the JVM, build tool home, or project dependencies.

1. Current `RuntimeProcessRunner` is stopped (sends `shutdown` command, then immediately calls `process.destroy()`)
2. Dependencies are re-resolved via `RuntimeClassLoaderFactory.create()` in the main process
3. New classpath entries are built from the resolved URLs
4. New `RuntimeProcessRunner` is started with the updated configuration, launching a fresh subprocess

### JDK Selection
For Maven, Gradle, and Custom runtimes, `java.home` can be explicitly configured. If not set, defaults to the Gade Application JDK.

### Build Tool Home Selection

For Gradle and Maven runtimes, the build tool installation can be configured. This determines which distribution is used to resolve project dependencies. Both tools use the same priority order: **Wrapper → Configured Home → Built-in**.

**Gradle** — `GradleUtils` accepts a `gradleInstallationDir` parameter (from `RuntimeConfig.buildToolHome`) and delegates to `GradleDistributionManager`:

```
GradleUtils(gradleInstallationDir, projectDir, javaHome)
    └── GradleDistributionManager(projectDir, gradleInstallationDir, wrapperAvailable, configManager)
            ├── WRAPPER       →  connector.useBuildDistribution()       [highest priority]
            ├── INSTALLATION  →  connector.useInstallation(dir)
            └── EMBEDDED      →  connector.useGradleVersion(current)    [lowest priority]
```

The distribution manager builds a prioritized list at construction time. If the current distribution fails, `tryNextDistribution()` advances to the next option. Gade isolates Gradle's own caches from the user's `~/.gradle` by using a project-local Gradle user home (`.gradle-gade-tooling`) via `connector.useGradleUserHomeDir()`.

> **Current implementation note:** `GradleDistributionManager.configureDistribution()` currently places INSTALLATION before WRAPPER. This must be reordered to wrapper-first to match the target priority.

**Maven** — `MavenClasspathUtils` uses `se.alipsa.mavenutils.MavenUtils` for dependency resolution. The target is for `MavenUtils` to be enhanced to support:
1. **Wrapper detection** — accept a Maven wrapper and use its pinned version for resolution
2. **Configured home** — accept an optional Maven home directory (from `RuntimeConfig.buildToolHome`)

Currently `MavenClasspathUtils` always instantiates `new MavenUtils()` with default settings (built-in mode only).

### Configuration Change Lifecycle

When the user changes runtime configuration (JVM, build tool home, Groovy version, etc.) via the `RuntimeEditorDialog` or by switching runtimes, `resetClassloaderAndGroovy()` is triggered. This follows the same Environment Reset sequence described above: stop the current subprocess and start a fresh one with re-resolved dependencies. This applies uniformly to all configuration changes.

---

## 9. Execution Flow

1. **Dependency Resolution (Main Process):** Based on Runtime Type, `RuntimeClassLoaderFactory` resolves dependency URLs:
   - Gradle: via Tooling API (`GradleUtils.addGradleDependencies()`)
   - Maven: via pom.xml parsing (`MavenClasspathUtils.addPomDependenciesTo()`)
   - Custom: from configured paths
   - Gade: bundled jars only

2. **Subprocess Launch (Main Process):** `RuntimeProcessRunner` starts a new JVM with the Runner JAR on its classpath and the configured `java.home`.

3. **Handshake (Both Processes):** Subprocess sends `hello`; main process validates protocol version; main process sends `addClasspath` with Groovy bootstrap entries and project dependency URLs.

4. **Loader Construction (Subprocess):** `GadeRunnerEngine` receives the dependency URLs and builds the classloader hierarchy: Process Classloader (Groovy/Ivy jars), Main Loader (compile/runtime deps), Test Loader (test deps).

5. **Script Execution (Subprocess):** Script compiled and executed in Main Loader or Test Loader context based on source file location (files under a test source directory use Test Loader; all others use Main Loader).

6. **Reset (if needed):** On dependency change, configuration change, or user request, the subprocess is stopped and the flow restarts from step 1 with re-resolved dependencies.

---

## 10. Current State vs Target

| Aspect                               | Current Implementation                                 | Target State                                | Status     |
|--------------------------------------|--------------------------------------------------------|---------------------------------------------|------------|
| Subprocess isolation                 | Yes - RuntimeProcessRunner                             | Yes                                         | Done       |
| Socket protocol                      | ProtocolXml over TCP                                   | ProtocolXml over TCP                        | Done       |
| Runner JAR                           | gade-runner.jar with JDK-only bootstrap                | gade-runner.jar with JDK-only bootstrap     | Done       |
| Main/Test Loader separation          | Single GroovyClassLoader; test context at construction | Separate Main + Test Loaders                | **TODO**   |
| Parent-Last for Maven/Gradle         | Parent-First for all types                             | Parent-Last for Maven/Gradle                | **TODO**   |
| Environment reset strategy           | Subprocess killed and restarted                        | Subprocess killed and restarted (adequate for all config change scenarios) | Done |
| ProcessRootLoader with public addURL | URLClassLoader with PlatformCL parent                  | Custom ProcessRootLoader for addURL support | **TODO**   |
| GUI proxy                            | RemoteInOut                                            | RemoteInOut                                 | Done       |
| ConnectionInfo serialization         | Falls through to `toString()` (returns name only); `ConnectionInfo` methods like `.withPassword()` fail in subprocess | `ArgumentSerializer` round-trips `ConnectionInfo` as a typed map with all fields (name, dependency, driver, url, user, password) | **TODO** (regression) |
| `dbConnect()` Connection return      | `java.sql.Connection` serialized as `toString()` — unusable in subprocess | `RemoteInOut` intercepts `dbConnect`, proxies `dbConnection()` to get `ConnectionInfo`, creates JDBC connection locally in subprocess | **TODO** (regression) |
| @GrabConfig(systemClassLoader=true)  | Not verified                                           | Process Classloader supports addURL         | **Verify** |
| Gradle Home configuration in UI      | Not configurable; `gradleInstallationDir` always null  | Configurable via RuntimeEditorDialog; wrapper→configured→built-in | **TODO** |
| Maven Home configuration in UI       | Not configurable; always uses built-in `MavenUtils`    | Configurable via RuntimeEditorDialog; wrapper→configured→built-in | **TODO** |
| Gradle wrapper detection             | Detected by `GradleDistributionManager`; used in distribution fallback | Wrapper takes priority over configured home and built-in | **TODO** (currently installation-first; needs reorder to wrapper-first) |
| Maven wrapper detection              | Tracked for cache fingerprint only                     | Wrapper takes priority over configured home and built-in; `MavenUtils` enhanced with wrapper support | **TODO** |
| User/Build environment separation    | Implicit — build tool jars in app classpath (`lib/app/`), user deps in subprocess | Explicitly documented and enforced          | Partial |
| `RuntimeConfig.buildToolHome` field  | No field; Gradle/Maven home not stored in config       | `buildToolHome` field in `RuntimeConfig`, persisted via `RuntimePreferences` | **TODO** |
| Bundled Gradle distribution          | Not bundled; embedded Tooling API version used as fallback | `lib/gradle/` in distribution with a Gradle installation | **TODO** |
| Bundled Maven distribution           | Not bundled; built-in `MavenUtils` library only        | `lib/maven/` in distribution with a Maven installation | **TODO** |
| Tooling API version validation       | No validation                                          | Hardcoded supported range; RuntimeEditorDialog extracts Gradle version from installation path or wrapper properties and warns if outside range | **TODO** |

---

## 11. Implementation Priorities

### 0. ConnectionInfo Serialization (Regression Fix)

The subprocess isolation model broke the ability to use `ConnectionInfo` objects returned by `io.dbConnection()`. `ArgumentSerializer` has no support for `ConnectionInfo` — it falls through to `toString()`, which returns just the connection name. This breaks the idiomatic pattern `io.dbConnection("name").withPassword("pwd")` used in all example scripts. Additionally, `io.dbConnect()` returns a `java.sql.Connection` which cannot be serialized across process boundaries.

**Acceptance criteria:**
- `ArgumentSerializer` serializes `ConnectionInfo` as a typed map with all fields and deserializes it back
- `io.dbConnection("name")` returns a usable `ConnectionInfo` in subprocess scripts
- `io.dbConnection("name").withPassword("pwd")` works in subprocess mode
- `io.dbConnect()` works transparently in subprocess mode by creating the JDBC connection locally (requires JDBC driver on subprocess classpath)
- Example scripts (`examples/database/`, `examples/exportData/`, `examples/importData/`) work in all runtime types

**Test gates:**
- Unit test: `ConnectionInfo` round-trips through `ArgumentSerializer.serialize()` / `deserialize()`
- Integration test: subprocess script calls `io.dbConnection("name")`, modifies password, passes result to `io.dbSelect(ci, "sql")`

### 1. Main/Test Loader Separation

Enables proper test isolation and scope-aware dependency injection. Only applies to Maven and Gradle runtimes.

**Acceptance criteria:**
- Subprocess creates separate Main Loader and Test Loader with correct parent chain
- Test scripts (files under build-tool-reported test source directories) execute in Test Loader
- Main scripts cannot see test-scope dependencies
- Test scripts can see main-scope classes
- Gade and Custom runtimes continue using a single classloader

**Test gates:**
- Unit test: verify classloader hierarchy construction in `GadeRunnerEngine`
- Integration test: script in `src/main/` cannot import a test-only dependency; script in `src/test/` can import both main and test dependencies

### 2. Parent-Last Delegation

Critical for Maven/Gradle projects where user deps must override bundled versions.

**Acceptance criteria:**
- Maven/Gradle Main Loader uses child-first delegation
- A project dependency (e.g. a different version of a library also in the Process Classloader) is loaded from the Main Loader, not the parent

**Test gates:**
- Integration test: project declares a library version different from Gade's bundled version; script verifies the project version is loaded

### 3. ProcessRootLoader

Custom URLClassLoader subclass with public `addURL()` for `@GrabConfig(systemClassLoader=true)` compatibility. Must be JDK-only to avoid forcing a Groovy version.

**Acceptance criteria:**
- `ProcessRootLoader` is installed as system classloader via `-Djava.system.class.loader`
- `@GrabConfig(systemClassLoader=true)` in a user script resolves to `ProcessRootLoader`
- `ProcessRootLoader` has no Groovy dependencies

**Test gates:**
- Unit test: `ProcessRootLoader.addURL()` is public and functional
- Integration test: script using `@Grab` with `@GrabConfig(systemClassLoader=true)` successfully downloads and loads a dependency

### 4. Maven/Gradle Home Configuration

Add `buildToolHome` field to `RuntimeConfig`. Add Build Tool Home directory chooser to `RuntimeEditorDialog` for Maven and Gradle runtimes. Reorder `GradleDistributionManager` to wrapper-first. Enhance `MavenUtils` to support Maven wrapper detection and configured Maven home. Pass the configured home from `RuntimeConfig` through to `GradleUtils` (as `gradleInstallationDir`) and `MavenClasspathUtils` (as an optional Maven home).

**Acceptance criteria:**
- `RuntimeConfig` includes `buildToolHome` field
- `RuntimeEditorDialog` shows Build Tool Home field for Maven and Gradle runtime types
- `GradleDistributionManager` priority order is Wrapper → Configured → Embedded
- `MavenUtils` accepts optional wrapper path and optional Maven home directory
- Empty/null `buildToolHome` means "use default" (wrapper → built-in)

**Test gates:**
- Unit test: `RuntimeConfig` with and without `buildToolHome` round-trips through JSON serialization
- Unit test: `GradleDistributionManager` with wrapper present places WRAPPER before INSTALLATION
- Integration test: Gradle project with wrapper uses wrapper version, not configured installation

### 5. Bundled Build Tool Distributions

Include a Gradle distribution in `lib/gradle/` and a Maven distribution in `lib/maven/` in the `runtimeZip` output. These provide the "built-in" fallback so all runtime types work out of the box without external tool installations. Update `GradleDistributionManager` to use `lib/gradle/` as the embedded fallback (instead of the Tooling API's `useGradleVersion()`), and update `MavenClasspathUtils` to use `lib/maven/` when no wrapper or configured home is available.

**Acceptance criteria:**
- `runtimeZip` task produces distributions containing `lib/gradle/` and `lib/maven/` directories
- `GradleDistributionManager` EMBEDDED mode uses `lib/gradle/` instead of `useGradleVersion()`
- `MavenClasspathUtils` built-in mode uses `lib/maven/` for dependency resolution
- Distribution size increase is documented in release notes

**Test gates:**
- Build test: `runtimeZip` output contains `lib/gradle/bin/gradle` and `lib/maven/bin/mvn`
- Integration test: Gradle runtime resolves dependencies using bundled distribution when no wrapper is present
- Integration test: Maven runtime resolves dependencies using bundled distribution when no wrapper is present

### 6. Tooling API Version Validation

Add validation in `RuntimeEditorDialog` to warn when a configured Gradle version is outside the bundled Tooling API's compatibility range.

**Acceptance criteria:**
- Hardcoded `MIN_SUPPORTED_GRADLE` and `MAX_SUPPORTED_GRADLE` constants exist in the codebase, updated when the Tooling API dependency changes
- When the user selects a Gradle installation via the Build Tool Home chooser, the dialog extracts the Gradle version (from `lib/gradle-core-*.jar` filename) and checks it against the supported range
- When a project wrapper specifies a version outside the supported range, a warning is shown
- Warning is non-blocking (user can proceed despite the warning)

**Test gates:**
- Unit test: version extraction from Gradle installation path (`lib/gradle-core-*.jar` parsing, with fallback to `bin/gradle --version`)
- Unit test: version extraction from `gradle-wrapper.properties` `distributionUrl`
- Unit test: versions inside range pass, versions outside range trigger warning
