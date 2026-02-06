# Gade Runtime Architecture Specification

## Overview

Gade executes user scripts that may have dependencies conflicting with the IDE itself. This architecture isolates user code from Gade's application classpath while supporting multiple build systems (Gradle, Maven) and custom Groovy installations.

**Key Goals:**
- Complete isolation between Gade application classes and user scripts
- Support for project-specific dependency versions that may differ from Gade's bundled libraries
- Dynamic environment reset without full application restart
- Seamless GUI interaction from isolated scripts

> **Reading guide:** Sections 1–9 describe the **target architecture**. Features already implemented are noted as "Done" in [Section 10 – Current State vs Target](#10-current-state-vs-target); items still under development are marked "TODO" there. Where the current implementation differs from the target design, Section 10 documents the gap.

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

Gade uses a **Hierarchical Sacrificial Classloader** pattern: child classloaders can be discarded and recreated to "reset" the script environment without terminating the subprocess.

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
- Test Loader parent = Main Loader
- Scripts execute in Main Loader or Test Loader based on source location: files under a recognized test source directory (e.g. `src/test/`) use the Test Loader; all others use the Main Loader
- Both loaders can be discarded and recreated without subprocess restart

---

## 3. Runtime Type Definitions

| Runtime Type | Classpath Construction                            | Dependency Injection            |
|--------------|---------------------------------------------------|---------------------------------|
| **Gade**     | Fixed to Gade's bundled Groovy/Ivy versions       | Dynamic via `@Grab`             |
| **Maven**    | Resolved from `pom.xml` via `MavenClasspathUtils` | `addURL()` to Main/Test Loaders |
| **Gradle**   | Resolved from `build.gradle` via `GradleUtils`    | `addURL()` to Main/Test Loaders |
| **Custom**   | User-defined Java Home, Groovy Home, jars         | Process Classloader + `@Grab`   |

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

| Direction     | Type                           | Purpose                                |
|---------------|--------------------------------|----------------------------------------|
| Gade → Runner | `addClasspath`                 | Send dependency URLs after handshake   |
| Gade → Runner | `eval`                         | Execute a script                       |
| Gade → Runner | `interrupt`                    | Cancel running script                  |
| Gade → Runner | `shutdown`                     | Graceful subprocess termination        |
| Gade → Runner | `resetClassloaders`            | Discard and recreate Main/Test Loaders |
| Runner → Gade | `hello`                        | Handshake with protocol version        |
| Runner → Gade | `classpathAdded`               | Acknowledge classloader setup complete |
| Runner → Gade | `classpathCleared`             | Acknowledge classloader reset complete |
| Runner → Gade | `result`                       | Script execution result                |
| Runner → Gade | `error`                        | Script execution error                 |
| Runner → Gade | `out` / `err`                  | Captured stdout/stderr                 |
| Bidirectional | `gui_request` / `gui_response` | Remote InOut method invocation         |

### Startup Handshake Sequence

```
1. Subprocess starts → sends "hello" with protocol version
2. Main process validates version compatibility
3. Main process sends "addClasspath" with groovyEntries + projectEntries
4. Subprocess builds classloader hierarchy
5. Subprocess sends "classpathAdded" acknowledgment
6. Main process enters command loop
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

| Class                       | Location   | Responsibility                                                          |
|-----------------------------|------------|-------------------------------------------------------------------------|
| `GroovyRuntimeManager`      | `console/` | Orchestrates runtime lifecycle; creates classloaders; starts subprocess |
| `RuntimeClassLoaderFactory` | `runtime/` | Creates GroovyClassLoader per runtime type with correct parent          |
| `RuntimeProcessRunner`      | `runtime/` | Manages subprocess: start, connect, send commands, handle responses     |
| `GadeRunnerMain`            | `runner/`  | Subprocess entry point                                                  |
| `GadeRunnerEngine`          | `runner/`  | Script evaluation in subprocess                                         |

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

### Dynamic Classloader Reset
The IDE can nullify and recreate Main/Test Loaders without restarting the subprocess. A reset must not be sent while a script evaluation is in progress; the caller must either wait for the current `result`/`error` response or send `interrupt` first.

1. Send `resetClassloaders` command
2. Subprocess discards Main/Test Loaders (all class references from the old loaders become eligible for GC)
3. Subprocess sends `classpathCleared` acknowledgment
4. Main process sends new `addClasspath` with updated dependencies
5. Subprocess recreates loaders and sends `classpathAdded` acknowledgment

### JDK Selection
For Maven, Gradle, and Custom runtimes, `java.home` can be explicitly configured. If not set, defaults to the Gade Application JDK.

---

## 9. Execution Flow

1. **Initialization:** Runtime Process starts; Process Classloader loads Runner JAR; socket connection established with Gade GUI.

2. **Dependency Resolution:** Based on Runtime Type, the main process resolves required URLs:
   - Gradle: via Tooling API (`GradleUtils.addGradleDependencies()`)
   - Maven: via pom.xml parsing (`MavenClasspathUtils.addPomDependenciesTo()`)
   - Custom: from configured paths
   - Gade: bundled jars only

3. **Loader Construction:** Main Loader instantiated with compile/runtime deps, then Test Loader with test deps.

4. **Script Execution:** Script compiled and executed in Main Loader or Test Loader context based on source file location (files under a test source directory use Test Loader; all others use Main Loader).

5. **Reset (if needed):** On dependency change or user request, discard loaders and rebuild from step 3.

---

## 10. Current State vs Target

| Aspect                               | Current Implementation                                 | Target State                                | Status     |
|--------------------------------------|--------------------------------------------------------|---------------------------------------------|------------|
| Subprocess isolation                 | Yes - RuntimeProcessRunner                             | Yes                                         | Done       |
| Socket protocol                      | ProtocolXml over TCP                                   | ProtocolXml over TCP                        | Done       |
| Runner JAR                           | gade-runner.jar with JDK-only bootstrap                | gade-runner.jar with JDK-only bootstrap     | Done       |
| Main/Test Loader separation          | Single GroovyClassLoader; test context at construction | Separate Main + Test Loaders                | **TODO**   |
| Parent-Last for Maven/Gradle         | Parent-First for all types                             | Parent-Last for Maven/Gradle                | **TODO**   |
| Dynamic reset without restart        | Subprocess killed and restarted                        | Classloaders discarded in-process           | **TODO**   |
| ProcessRootLoader with public addURL | URLClassLoader with PlatformCL parent                  | Custom ProcessRootLoader for addURL support | **TODO**   |
| GUI proxy                            | RemoteInOut                                            | RemoteInOut                                 | Done       |
| @GrabConfig(systemClassLoader=true)  | Not verified                                           | Process Classloader supports addURL         | **Verify** |

---

## 11. Implementation Priorities

1. **Main/Test Loader Separation** - Enables proper test isolation and scope-aware dependency injection.

2. **Parent-Last Delegation** - Critical for Maven/Gradle projects where user deps must override bundled versions.

3. **In-Process Classloader Reset** - Performance improvement; avoids subprocess restart overhead on dependency changes.

4. **ProcessRootLoader** - Custom URLClassLoader subclass with public `addURL()` for `@GrabConfig(systemClassLoader=true)` compatibility. Must be JDK-only to avoid forcing a Groovy version.
