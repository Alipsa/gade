# Gade Architecture

**Version:** 1.0.0
**Last Updated:** February 3, 2026

This document describes the architecture of Gade (Groovy Analytics Development Environment), covering the major components, runtime execution models, communication protocols, and extension points.

## Table of Contents

1. [System Overview](#system-overview)
2. [Component Architecture](#component-architecture)
3. [Runtime Execution Models](#runtime-execution-models)
4. [JSON-RPC Protocol](#json-rpc-protocol)
5. [Code Completion System](#code-completion-system)
6. [Extension Points](#extension-points)
7. [Build System](#build-system)
8. [Data Flow](#data-flow)

---

## System Overview

### High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                         Gade Main Process                         │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                    JavaFX UI Layer                          │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐  │  │
│  │  │ Editor   │ │ Console  │ │ Environment│ │ Connections │  │  │
│  │  │ Tabs     │ │ Output   │ │ Variables │ │ Database    │  │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────────┘  │  │
│  └────────────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                  Application Logic                          │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐  │  │
│  │  │ Code     │ │ Runtime  │ │ Git      │ │ File         │  │  │
│  │  │ Completion│ │ Manager  │ │ Integration│ │ Manager   │  │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────────┘  │  │
│  └────────────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                  Embedded Runtimes                          │  │
│  │  ┌──────────────────────────────────────────────────────┐  │  │
│  │  │ Gade Runtime (GroovyShell) - In-Process              │  │  │
│  │  └──────────────────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
           │ JSON-RPC                │ JSON-RPC
           │ (Socket)                │ (Socket)
           ↓                         ↓
┌─────────────────────────┐ ┌─────────────────────────┐
│  Gradle Runtime Process │ │  Maven Runtime Process  │
│  (Separate JVM)         │ │  (Separate JVM)         │
│  ┌───────────────────┐  │ │  ┌───────────────────┐  │
│  │ GradleRunner      │  │ │  │ MavenRunner       │  │
│  │ Groovy Engine     │  │ │  │ Groovy Engine     │  │
│  │ Gradle Tooling API│  │ │  │ Maven Embedder    │  │
│  └───────────────────┘  │ │  └───────────────────┘  │
└─────────────────────────┘ └─────────────────────────┘
```

### Design Principles

1. **Process Isolation:** Gradle and Maven runtimes execute in separate JVMs to prevent classpath conflicts
2. **Pluggable Architecture:** Code completion, syntax highlighting, and runtimes are extensible
3. **Reactive UI:** JavaFX Platform.runLater() ensures UI remains responsive during long operations
4. **Caching:** Aggressive caching of classpaths, completions, and Git state for performance
5. **Single Responsibility:** Each component has a focused purpose (see Sprint 3 refactors)

---

## Component Architecture

### 1. UI Layer (JavaFX)

**Package:** `se.alipsa.gade`

#### Main Window (Gade.java)
- Application entry point
- Menu bar, toolbar, tabs, console
- Coordinates UI components

#### Editor Components
- **EditorTab** - Base class for all editor tabs
- **GroovyTab** - Groovy script editor
- **SqlTab** - SQL script editor
- **JsTab** - JavaScript editor
- **MarkdownTab** - Markdown viewer/editor

Each tab extends `ExecutableTab` which provides:
- Execute button management
- TaskListener implementation (disable during execution)
- Common file operations

**See:** `docs/adr/002-executable-tab-pattern.md`

#### Console Components
- **ConsoleTextArea** - Output display with syntax coloring
- **GroovyEngine** - Embedded Groovy execution engine
- **RuntimeProcessRunner** - External runtime process manager

**Refactored in Sprint 3 (Task #10):** ConsoleComponent split into:
- ConsoleButtonPanel - Button management
- ConsoleEnvironmentPane - Variable display
- ConsoleTextArea - Output display

### 2. Code Completion System

**Package:** `se.alipsa.gade.code.completion`

#### Core Classes

**CompletionRegistry** (Singleton, Thread-Safe)
- Central registry for all completion engines
- Language → Engine mapping
- Cache invalidation coordination

**CompletionEngine** (Interface)
- `complete(CompletionContext) → List<CompletionItem>`
- `supportedLanguages() → Set<String>`
- `invalidateCache()` - Called when classpath changes

**CompletionContext** (Immutable)
- Full document text
- Caret position
- Utility methods: `isMemberAccess()`, `isInsideString()`, `isInsideComment()`

**CompletionItem** (Immutable)
- Completion text, display text, kind (method, field, keyword, etc.)
- Sort priority, cursor offset
- Builder pattern for complex items

#### Engines

**GroovyCompletionEngine**
- Uses ClassGraph for fast class scanning
- Reflection for member access
- Caches scanned classes (invalidated on classpath change)

**SqlCompletionEngine**
- SQL keywords, functions
- Table/column names from connected databases
- JSqlParser for syntax analysis

**JavaScriptCompletionEngine**
- JavaScript keywords
- DOM API methods
- Node.js completions

**See:** `docs/adr/003-completion-engine-architecture.md`

### 3. Runtime System

**Package:** `se.alipsa.gade.runtime`

#### Runtime Types

**GadeRuntime (Embedded)**
- In-process GroovyShell execution
- Fast, no IPC overhead
- No external dependency management
- **Use case:** Quick scripts, exploration

**GradleRuntime (Subprocess)**
- Separate JVM process
- Full Gradle Tooling API
- Dependency resolution via build.gradle
- **Use case:** Projects with dependencies

**MavenRuntime (Subprocess)**
- Separate JVM process
- maven-utils library for classpath resolution
- Dependency resolution via pom.xml
- **Use case:** Enterprise projects, POM inheritance

**CustomRuntime (Subprocess)**
- User-specified Java executable
- Custom classpath
- **Use case:** Testing against specific JDK versions

**See:** `docs/adr/001-separate-process-runtimes.md`

#### RuntimeProcessRunner

Manages external runtime processes:

**Responsibilities:**
- Start/stop runtime subprocess
- JSON-RPC message handling
- stdout/stderr forwarding to console
- Script evaluation coordination
- Graceful shutdown

**Lifecycle:**
1. Start subprocess with `GadeRunnerMain`
2. Wait for "hello" handshake
3. Send "eval" requests with script + bindings
4. Receive "result", "out", "err", "bindings" messages
5. On completion, send "shutdown" or kill process

**Thread Safety:** All public methods are synchronized

### 4. Gradle Integration

**Package:** `se.alipsa.gade.utils.gradle`

**Refactored in Sprint 3 (Task #10):** GradleUtils split into:

#### GradleUtils
- Gradle Tooling API connection
- Classpath resolution
- Dependency introspection
- Error detection with helpful hints

**Key Methods:**
- `resolveClasspath(File projectDir) → Set<File>`
- `getCacheDir() → File`
- `hasGradleProject(File dir) → boolean`
- `getProjectFingerprint(File dir) → String`

#### GradleCacheManager
- Classpath fingerprinting (build.gradle + settings.gradle hash)
- JSON-based cache storage
- Cache invalidation on file changes

**Cache Location:** `~/.gade/gradle-cache/<project-hash>.json`

#### GradleErrorDetector
- Parse Gradle errors
- Suggest fixes for common issues
- Example: "Could not find X" → "Add repository: mavenCentral()"

**Thread Safety:** All classes are thread-safe

### 5. Git Integration

**Package:** `se.alipsa.gade.inout.git`

**Library:** JGit (Eclipse)

**Features:**
- Clone, init, commit, push, pull
- Branch management
- Diff viewing
- Stash operations
- Tag management

**Components:**
- **GitUtils** - JGit wrapper methods
- **GitStatusMonitor** - Background thread monitoring .git directory
- **GitDialog** - UI dialogs for Git operations

### 6. Database Integration

**Package:** `se.alipsa.gade.environment.connections`

**Connection Types:**
- JDBC (standard databases)
- BigQuery (Google Cloud Platform)

**Components:**
- **ConnectionHandler** - JDBC connection management
- **ConnectionInfo** - Connection metadata storage
- **ConnectionsTab** - UI for managing connections

**JDBC Driver Loading:**
- Dynamic classloading via `data-utils` library
- No system classloader pollution
- Add drivers via UI or Gradle dependencies

### 7. Build System

**Tool:** Gradle 9.3.1

**Plugins:**
- `application` - Main class specification
- `groovy` - Groovy compilation
- `org.javamodularity.moduleplugin` - Java modules (JPMS)
- `org.beryx.runtime` - Platform-specific distributions
- `org.openjfx.javafxplugin` - JavaFX dependencies
- `jacoco` - Code coverage
- `me.champeau.jmh` - Performance benchmarks

**Key Build Tasks:**
```bash
./gradlew run             # Development run
./gradlew build           # Compile + test
./gradlew test            # Run all tests
./gradlew jacocoTestReport # Generate coverage report
./gradlew jmh             # Run benchmarks
./gradlew runtimeZip      # Create platform-specific distributions
```

---

## Runtime Execution Models

### Gade Runtime (Embedded)

```
┌─────────────────────────────────────────────┐
│         Gade Main Process                   │
│                                             │
│  User Script                                │
│      ↓                                      │
│  ┌────────────────────────────────────┐    │
│  │ GroovyShell.evaluate(script)       │    │
│  │   - Same classloader as Gade       │    │
│  │   - Access to all Gade classes     │    │
│  │   - stdout → ConsoleTextArea       │    │
│  └────────────────────────────────────┘    │
│      ↓                                      │
│  Result → Environment Tab                   │
└─────────────────────────────────────────────┘
```

**Advantages:**
- ⚡ Fast (no IPC overhead)
- Simple (no subprocess management)

**Limitations:**
- ❌ No external dependency management
- ❌ Classpath shared with Gade (potential conflicts)

### Gradle Runtime (Subprocess)

```
┌─────────────────────────────┐          ┌──────────────────────────┐
│   Gade Main Process         │          │  Gradle Runtime Process  │
│                             │          │                          │
│  User Script                │          │  GradleRunnerMain        │
│      ↓                      │          │                          │
│  ┌────────────────────┐    │ Socket   │  ┌────────────────────┐ │
│  │ RuntimeProcessRunner│◄───┼──────────┤►│ JSON-RPC Server    │ │
│  │  - Start process    │    │  JSON    │  │  - Receive "eval"  │ │
│  │  - Send "eval"      │    │  RPC     │  │  - Execute script  │ │
│  │  - Receive "result" │    │          │  │  - Send "result"   │ │
│  └────────────────────┘    │          │  └────────────────────┘ │
│      ↓                      │          │         ↓               │
│  Result → Environment Tab   │          │  GroovyShell.evaluate() │
│                             │          │  (with Gradle classpath)│
└─────────────────────────────┘          └──────────────────────────┘
```

**Gradle Classpath Resolution:**
1. Read build.gradle
2. Use Gradle Tooling API to resolve dependencies
3. Cache resolved classpath (fingerprinted by build.gradle hash)
4. Pass classpath to subprocess via command-line argument

**Advantages:**
- ✅ Full Gradle support (dependencies, plugins, etc.)
- ✅ Isolated classpath (no conflicts with Gade)

**Limitations:**
- ⏱️ Slower first run (dependency download)
- ⏱️ Subprocess startup overhead (~1-2 seconds)

### Maven Runtime (Subprocess)

Similar to Gradle, but uses:
- `pom.xml` instead of `build.gradle`
- maven-utils library instead of Gradle Tooling API
- MavenRunnerMain subprocess

---

## JSON-RPC Protocol

**Version:** 1.0 (as of Sprint 3, Task #13)

**See:** `src/main/java/se/alipsa/gade/runtime/ProtocolVersion.java`

### Message Types

#### 1. Handshake (Runner → Gade)

**Purpose:** Subprocess signals readiness

```json
{
  "type": "hello",
  "port": 12345,
  "protocolVersion": "1.0"
}
```

**Gade Response:** Stores port, marks runner as ready

#### 2. Eval Request (Gade → Runner)

**Purpose:** Execute a script

```json
{
  "cmd": "eval",
  "id": "uuid-1234",
  "script": "println 'hello'\n1 + 2",
  "bindings": {
    "customVar": "value"
  }
}
```

**Fields:**
- `id` - Unique request ID (used to correlate response)
- `script` - Groovy code to execute
- `bindings` - Variables to inject into script context

#### 3. Result (Runner → Gade)

**Purpose:** Return script result

```json
{
  "type": "result",
  "id": "uuid-1234",
  "result": "3"
}
```

#### 4. Error (Runner → Gade)

**Purpose:** Report script execution error

```json
{
  "type": "error",
  "id": "uuid-1234",
  "error": "No such property: foo",
  "stacktrace": "groovy.lang.MissingPropertyException: ...\n  at ..."
}
```

#### 5. Output (Runner → Gade)

**Purpose:** Forward stdout/stderr

```json
{
  "type": "out",
  "text": "hello\n"
}
```

```json
{
  "type": "err",
  "text": "Error: something failed\n"
}
```

#### 6. Bindings (Runner → Gade)

**Purpose:** Update environment variables after script execution

```json
{
  "type": "bindings",
  "bindings": {
    "x": "10",
    "y": "[1, 2, 3]"
  }
}
```

**Gade Response:** Updates Environment tab with new variables

#### 7. Shutdown (Gade → Runner)

**Purpose:** Gracefully shut down subprocess

```json
{
  "cmd": "shutdown"
}
```

**Runner Response:** Exits with code 0

### Protocol Flow

```
Gade                                  Runner (Subprocess)
  │                                         │
  ├────────────── start process ──────────►│
  │                                         ├─ listen on random port
  │                                         │
  │◄────── hello (port, version) ──────────┤
  │                                         │
  ├────── eval (id, script, bindings) ────►│
  │                                         ├─ execute script
  │◄──────────── out ("hello") ────────────┤
  │◄──────────── result (id, "3") ─────────┤
  │◄──────────── bindings ─────────────────┤
  │                                         │
  ├────── eval (id2, script2, ...) ───────►│
  │◄──────────── error (id2, ...) ─────────┤
  │                                         │
  ├──────────── shutdown ──────────────────►│
  │                                         ├─ exit(0)
```

### Error Handling

**Connection Failures:**
- Gade waits 10 seconds for "hello" handshake
- If timeout, kills process and shows error

**Script Errors:**
- Runner catches all exceptions
- Sends "error" message with stacktrace
- Does NOT crash subprocess (can run next script)

**Runner Crash:**
- Gade detects process exit
- Shows "Runtime process terminated unexpectedly"
- User can restart runtime

---

## Code Completion System

### Architecture

```
User Types → TextArea Event → CompletionPopup
                                     ↓
                            CompletionRegistry.getCompletions()
                                     ↓
                       ┌─────────────┴─────────────┐
                       ↓                           ↓
              GroovyCompletionEngine      SqlCompletionEngine
                       ↓                           ↓
         ┌─────────────┴──────────┐      ┌────────┴────────┐
         ↓                        ↓       ↓                 ↓
  ClassGraph Scan          Reflection   SQL Keywords   DB Metadata
  (all classes)           (members)                    (tables, columns)
         ↓                        ↓       ↓                 ↓
         └────────────┬───────────┘       └────────┬────────┘
                      ↓                            ↓
              List<CompletionItem>        List<CompletionItem>
                      └────────────┬───────────────┘
                                   ↓
                         Sort by Priority
                                   ↓
                        Display in Popup
```

### Performance Optimizations

**1. ClassGraph Caching**
```java
// Scan once, cache forever (until invalidateCache())
ClassGraph classGraph = new ClassGraph()
  .enableClassInfo()
  .enableMethodInfo()
  .scan();

cache.put("classGraph", classGraph);
```

**2. Completion Item Pre-computation**
```java
// Convert classes to CompletionItems eagerly
List<CompletionItem> classCompletions =
  scanResult.getAllClasses()
    .stream()
    .map(cls -> CompletionItem.class(cls.getName()))
    .toList();
```

**3. Prefix Filtering**
```java
// Filter by prefix on retrieval (not during scan)
String prefix = context.tokenPrefix();
return cachedItems.stream()
  .filter(item -> item.label().startsWith(prefix))
  .toList();
```

**4. Lazy Loading**
```java
// Only scan when completion is actually requested
if (!context.isMemberAccess() && !userPressedCtrlSpace) {
  return Collections.emptyList();  // Don't scan yet
}
```

**Target:** < 100ms completion time (benchmarked in Task #27)

### Extension Points

**To add a new language completion:**

1. **Implement CompletionEngine:**
```java
public class MyLangCompletionEngine implements CompletionEngine {
  @Override
  public List<CompletionItem> complete(CompletionContext context) {
    // Your completion logic
  }

  @Override
  public Set<String> supportedLanguages() {
    return Set.of("mylang");
  }
}
```

2. **Register on startup:**
```java
// In Gade.java or extension loader
CompletionRegistry.getInstance().register(new MyLangCompletionEngine());
```

3. **Handle cache invalidation:**
```java
@Override
public void invalidateCache() {
  myCache.clear();
}
```

**See:** Javadoc for `CompletionEngine`, `CompletionItem`, `CompletionContext`

---

## Extension Points

### 1. Code Completion Engines

**Interface:** `CompletionEngine`

**Examples:**
- GroovyCompletionEngine
- SqlCompletionEngine
- JavaScriptCompletionEngine

**Hook:** `CompletionRegistry.register(engine)`

### 2. Syntax Highlighters

**Library:** RichTextFX

**Current Implementations:**
- Groovy (keywords, strings, comments, numbers)
- SQL (keywords, identifiers, operators)
- JavaScript (ES6 keywords)
- XML, HTML, Markdown

**Extension:** Add language-specific regex patterns in `CodeTextArea.java`

### 3. Custom Runtimes

**Interface:** `RuntimeConfiguration`

**Steps:**
1. Implement subprocess main class (like `GradleRunnerMain`)
2. Implement JSON-RPC protocol
3. Add UI for runtime selection
4. Register with RuntimeManager

### 4. Database Connectors

**Interface:** Standard JDBC

**Current Support:**
- PostgreSQL, MySQL, H2, SQL Server, Oracle
- BigQuery (custom connector)

**Extension:** Implement JDBC driver or custom ConnectionHandler

### 5. File Type Handlers

**Pattern:** Tab factories in `FileOpener.java`

**Example:**
```java
if (file.getName().endsWith(".gmd")) {
  return new MarkdownTab(gui, file);
} else if (file.getName().endsWith(".sql")) {
  return new SqlTab(gui, file);
}
```

**Extension:** Add new tab type for your file extension

---

## Build System

### Module Structure (JPMS)

```
module se.alipsa.gade {
  // JavaFX
  requires javafx.controls;
  requires javafx.web;

  // Groovy
  requires org.apache.groovy;
  requires org.apache.groovy.sql;

  // Build tools
  requires org.gradle.tooling;
  requires se.alipsa.maven.utils;

  // UI libraries
  requires org.fxmisc.richtext;
  requires org.fxmisc.flowless;

  // Utilities
  requires io.github.classgraph;
  requires org.eclipse.jgit;
  requires com.fasterxml.jackson.databind;

  // Database
  requires java.sql;
  requires se.alipsa.groovy.datautil;

  // Matrix (data science)
  requires se.alipsa.groovy.matrix;
  requires se.alipsa.groovy.charts;

  // Exports (for extensions)
  exports se.alipsa.gade.code.completion;
  exports se.alipsa.gade.runtime;
}
```

### Platform-Specific Distributions

**Tool:** org.beryx.runtime plugin (jlink)

**Process:**
1. Compile Java → .class files
2. Create modular JAR
3. jlink creates minimal JRE (Java 21 + JavaFX)
4. Bundle application + JRE → platform-specific package

**Outputs:**
- `gade-linux.zip` (x64)
- `gade-macos.zip` (x64 + ARM64)
- `gade-windows.zip` (x64)

**Advantages:**
- No JDK installation required
- Smaller download (JRE includes only used modules)
- Consistent Java version across platforms

---

## Data Flow

### Script Execution (Gade Runtime)

```
User clicks "Run"
     ↓
ExecutableTab.executeAction()
     ↓
ConsoleComponent.execute(script)
     ↓
GroovyEngine.eval(script)
     ↓
GroovyShell.evaluate(script)
     ↓
Result returned
     ↓
ConsoleComponent.displayResult()
     ↓
Environment tab updated (variables)
```

### Script Execution (Gradle Runtime)

```
User clicks "Run"
     ↓
ExecutableTab.executeAction()
     ↓
ConsoleComponent.execute(script)
     ↓
RuntimeProcessRunner.execute(script)
     ↓
Send JSON: {"cmd":"eval","id":"...","script":"..."}
     ↓
[Process boundary - Socket IPC]
     ↓
GradleRunnerMain receives message
     ↓
GroovyShell.evaluate(script) [with Gradle classpath]
     ↓
stdout → Send JSON: {"type":"out","text":"..."}
     ↓
result → Send JSON: {"type":"result","id":"...","result":"..."}
     ↓
[Process boundary - Socket IPC]
     ↓
RuntimeProcessRunner.handleMessage()
     ↓
ConsoleComponent.displayResult()
     ↓
Environment tab updated
```

### Code Completion Flow

```
User types "text."
     ↓
TextArea KeyTyped event
     ↓
CompletionPopup.show()
     ↓
CompletionRegistry.getCompletions(context)
     ↓
For each registered engine:
  engine.complete(context)
     ↓
Engines return List<CompletionItem>
     ↓
Merge + sort by priority
     ↓
Display top 10 in popup
     ↓
User presses Enter
     ↓
Insert item.insertText()
     ↓
Move cursor by item.cursorOffset()
```

### Gradle Dependency Resolution

```
User opens project with build.gradle
     ↓
GradleUtils.resolveClasspath(projectDir)
     ↓
Calculate fingerprint (hash of build.gradle)
     ↓
Check cache: ~/.gade/gradle-cache/<fingerprint>.json
     ↓
Cache miss → Use Gradle Tooling API
     ↓
GradleConnector.forProjectDirectory(dir)
     ↓
connection.model(IdeaProject.class).get()
     ↓
Extract dependencies from model
     ↓
Convert to Set<File> (JAR paths)
     ↓
Save to cache (JSON)
     ↓
Return classpath
     ↓
Pass to RuntimeProcessRunner as -cp argument
```

---

## Security Considerations

### Script Execution

**Gade Runtime:**
- ⚠️ Runs in same process as UI
- ⚠️ Full access to Gade internals
- ⚠️ Can call `System.exit()`, modify files, etc.

**Gradle/Maven Runtime:**
- ✅ Separate process (limited blast radius)
- ⚠️ Still has full filesystem/network access
- ⚠️ No sandboxing (Groovy Security Manager deprecated)

**Recommendation:** Only run trusted scripts. Gade is not designed for untrusted code execution.

### JDBC Connections

- Passwords stored in plaintext in connection configuration
- No encryption at rest
- ⚠️ Ensure `.gade/connections.json` has restricted permissions

### Git Operations

- SSH keys managed by OS (not Gade)
- HTTPS credentials via Git credential manager
- ✅ No password storage in Gade

---

## Performance Characteristics

### Startup Time

**Measured on 2023 MacBook Pro (M2):**
- Cold start: ~3-4 seconds
- Warm start: ~2 seconds

**Bottlenecks:**
- JavaFX initialization (1-1.5s)
- Module loading (0.5s)
- UI construction (0.5s)

### Code Completion

**Target:** < 100ms (per roadmap Task #27)

**Actual (benchmarked):**
- Simple string completion: ~42ms
- Complex import completion: ~68ms
- Member access: ~51ms

**See:** `docs/improvements/task-27-performance-benchmarks.md`

### Gradle Classpath Resolution

**First resolution (no cache):**
- Small project (~10 deps): 5-10 seconds
- Medium project (~50 deps): 15-30 seconds
- Large project (~100+ deps): 30-60 seconds

**Cached resolution:**
- < 100ms (read JSON from disk)

### Memory Usage

**Typical usage:**
- Minimum: 200MB (empty project)
- Normal: 500MB-1GB (with Gradle runtime)
- Heavy: 2-4GB (large datasets in matrix)

**Recommendation:** -Xmx8G for data science workloads

---

## Future Architecture Considerations

### Potential Enhancements

1. **Language Server Protocol (LSP)**
   - Replace custom completion with LSP
   - Benefit: Reuse existing language servers
   - Challenge: Integration with JavaFX UI

2. **Plugin System**
   - Dynamic plugin loading
   - Separate classloader per plugin
   - Extension registry

3. **Distributed Execution**
   - Remote script execution (SSH)
   - Cluster support (Spark, etc.)

4. **Scripting API**
   - Programmatic access to Gade features
   - Automate workflows via scripts

5. **Web-Based UI**
   - Browser-based alternative to JavaFX
   - Better remote access
   - Challenge: Requires complete rewrite

---

## References

- [ADR 001: Separate Process Runtimes](docs/adr/001-separate-process-runtimes.md)
- [ADR 002: GroovyShell vs JSR223](docs/adr/002-groovyshell-vs-jsr223.md)
- [ADR 003: Completion Engine Architecture](docs/adr/003-completion-engine-architecture.md)
- [User Guide](docs/user-guide.md)
- [API Documentation](build/docs/javadoc/index.html)

---

**Document Version:** 1.0.0
**Last Updated:** February 3, 2026
**Maintained By:** Gade Development Team
