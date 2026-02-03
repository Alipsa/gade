# ADR 001: Separate Process Runtimes for Gradle and Maven

## Status
**Accepted** - Implemented in Sprint 3 (December 2025)

## Context

Gade supports multiple runtime modes for executing Groovy scripts:
1. **Gade Runtime** - Embedded GroovyShell in the main process
2. **Gradle Runtime** - Scripts executed with Gradle-resolved dependencies
3. **Maven Runtime** - Scripts executed with Maven-resolved dependencies

### The Problem

When adding Gradle and Maven runtime support, we faced a fundamental architectural decision: **Should external runtimes execute in-process or in separate processes?**

**In-Process Execution:**
```
┌─────────────────────────────────────┐
│   Gade Main Process (Single JVM)   │
│                                      │
│  ┌──────────────────────────────┐  │
│  │  UI (JavaFX)                 │  │
│  └──────────────────────────────┘  │
│  ┌──────────────────────────────┐  │
│  │  Gade Runtime (GroovyShell)  │  │
│  └──────────────────────────────┘  │
│  ┌──────────────────────────────┐  │
│  │  Gradle Runtime              │  │
│  │  (Gradle Tooling API)        │  │
│  └──────────────────────────────┘  │
│  ┌──────────────────────────────┐  │
│  │  Maven Runtime               │  │
│  └──────────────────────────────┘  │
└─────────────────────────────────────┘
```

**Separate Process Execution:**
```
┌──────────────────────┐
│  Gade Main Process   │
│  (JavaFX UI)         │
└──────────────────────┘
   │           │
   │ JSON-RPC  │ JSON-RPC
   ↓           ↓
┌─────────┐ ┌─────────┐
│ Gradle  │ │ Maven   │
│ Process │ │ Process │
└─────────┘ └─────────┘
```

### Classpath Conflicts

**Gradle Tooling API dependencies:**
- Guava 30.1 (required by Gradle)
- SLF4J 1.7.x (required by Gradle)
- Kotlin stdlib (Gradle is written in Kotlin)
- 50+ transitive dependencies

**Gade UI dependencies:**
- JavaFX 21
- RichTextFX 0.11.x
- JGit (for Git integration)
- Matrix library (data science)

**Conflict scenarios:**
1. **SLF4J version conflict:**
   - Gradle requires SLF4J 1.7.x
   - JGit may require SLF4J 2.x
   - Result: ClassNotFoundException or runtime errors

2. **Guava version conflict:**
   - Gradle requires Guava 30.1
   - User's project may require Guava 31.x
   - Result: NoSuchMethodError at runtime

3. **Groovy version conflict:**
   - Gade uses Groovy 4.0.15
   - User's Gradle project may use Groovy 3.0.x
   - Result: Incompatible GroovyObject implementations

### Memory and Stability

**In-process risks:**
- User script calls `System.exit(0)` → Entire Gade crashes
- Memory leak in Gradle Tooling API → Gade UI becomes unresponsive
- Gradle daemon crash → Corrupts Gade state
- OutOfMemoryError in user script → Gade terminates

**Separate process benefits:**
- Subprocess crashes → Gade UI remains functional
- Memory isolation → User scripts can't exhaust Gade's heap
- Kill misbehaving processes → Clean slate for next execution

### User Experience

**In-process advantages:**
- ⚡ Fast startup (no subprocess overhead)
- 🔗 Direct variable sharing (no serialization)
- 🧠 Simple mental model (one process)

**Separate process disadvantages:**
- ⏱️ Startup overhead (~1-2 seconds)
- 🔌 IPC complexity (JSON-RPC protocol)
- 📦 Serialization overhead (variables, results)

## Decision

**We will execute Gradle and Maven runtimes in separate JVM processes.**

### Rationale

1. **Classpath Isolation is Critical**
   - Gradle/Maven have complex dependency graphs
   - Conflicts are inevitable and hard to debug
   - Separate processes guarantee clean classpaths

2. **Stability Over Speed**
   - 1-2 second startup overhead is acceptable
   - Preventing UI crashes is more important than startup speed
   - Users tolerate brief wait for reliability

3. **Memory Safety**
   - Large dataset processing (data science workflows) needs isolation
   - User scripts shouldn't crash the IDE
   - Allows aggressive heap sizing per runtime (`-Xmx16G` for scripts)

4. **Precedent in Industry**
   - **IntelliJ IDEA:** Gradle builds run in separate processes
   - **VS Code:** Language servers run as separate processes
   - **Jupyter:** Kernels run as separate processes

5. **Gade Runtime Can Stay In-Process**
   - Embedded GroovyShell has no dependency conflicts
   - Fast iteration for quick scripts
   - Best of both worlds (fast + isolated options)

### Implementation Strategy

**JSON-RPC Protocol:**
- Lightweight, text-based (easy to debug)
- Versioned protocol (backward compatibility)
- Async messaging (non-blocking UI)

**Socket Communication:**
- Subprocess listens on random port
- Sends "hello" handshake with port number
- Gade connects and exchanges messages

**Graceful Degradation:**
- If subprocess crashes, show error and allow restart
- Fall back to Gade Runtime if external runtime unavailable
- Preserve console output even on crash

## Consequences

### Positive

1. **✅ Classpath Isolation**
   - Gradle/Maven dependencies never conflict with Gade
   - User projects can use any Groovy version
   - No SLF4J, Guava, or Kotlin conflicts

2. **✅ Stability**
   - Subprocess crashes don't affect Gade UI
   - `System.exit()` calls only kill subprocess
   - Memory leaks isolated to subprocess

3. **✅ Memory Management**
   - Each runtime can have custom heap size
   - Large datasets don't exhaust Gade's heap
   - OOM in script doesn't crash IDE

4. **✅ Security**
   - Limited blast radius (subprocess can't directly access Gade internals)
   - Easier to add sandboxing in future (SecurityManager, Docker, etc.)

5. **✅ Testing**
   - Easier to test runtimes in isolation
   - Can mock JSON-RPC protocol
   - Integration tests don't pollute test classpath

### Negative

1. **❌ Startup Overhead**
   - ~1-2 seconds to start subprocess
   - Mitigated: First run only (subsequent executions reuse process)
   - Mitigated: Gade Runtime remains fast for quick scripts

2. **❌ IPC Complexity**
   - JSON-RPC protocol must be maintained
   - Serialization overhead for large results
   - Network errors (unlikely but possible)

3. **❌ Debugging Difficulty**
   - Can't attach debugger to both processes simultaneously
   - Need to inspect JSON messages for protocol issues
   - Subprocess logs separate from main logs

4. **❌ Resource Overhead**
   - Additional JVM process consumes ~200-500MB RAM
   - More CPU context switches
   - Higher battery usage on laptops

### Mitigation Strategies

**For Startup Overhead:**
- Keep subprocess alive between executions
- Only restart on classpath changes
- Show "Connecting to runtime..." progress indicator

**For IPC Complexity:**
- Comprehensive protocol documentation
- Versioned protocol for backward compatibility
- Unit tests for message parsing

**For Debugging:**
- Log all JSON-RPC messages at DEBUG level
- Subprocess writes to `~/.gade/logs/runtime-gradle.log`
- Provide `--debug-runtime` flag to show subprocess console

**For Resource Overhead:**
- Lazy startup (only when first script runs)
- Shutdown subprocess after 30 minutes of inactivity
- Allow users to disable external runtimes if not needed

## Alternatives Considered

### Alternative 1: In-Process with Isolated Classloaders

**Approach:**
- Use separate `URLClassLoader` per runtime
- Load Gradle/Maven jars in child classloader
- Isolate dependencies

**Why Rejected:**
- ClassLoader isolation is incomplete (system classes shared)
- Still vulnerable to System.exit(), memory leaks
- Complex to implement correctly
- JVM modules (JPMS) make this harder in Java 21
- Performance overhead of class loading

### Alternative 2: Docker Containers

**Approach:**
- Run each script in a Docker container
- Complete isolation (process, filesystem, network)
- Security benefits

**Why Rejected:**
- Requires Docker installation (not all users have it)
- Startup overhead too high (5-10 seconds)
- Poor user experience for quick iteration
- Complexity in passing files, environment variables
- Windows support problematic

### Alternative 3: No External Runtimes

**Approach:**
- Only support Gade Runtime (embedded GroovyShell)
- Users manually add JARs to Gade's classpath
- No Gradle/Maven integration

**Why Rejected:**
- Poor developer experience
- Manual dependency management error-prone
- Defeats purpose of IDE integration
- Competitive disadvantage vs. IntelliJ, VS Code

### Alternative 4: Gradle Daemon Integration

**Approach:**
- Use existing Gradle daemon (if running)
- Communicate via Gradle Tooling API
- No custom subprocess

**Why Rejected:**
- Gradle daemon shared across all Gradle projects
- Can't customize heap size per script
- Daemon crashes still problematic
- Daemon may be busy with other builds
- Less control over execution environment

## Implementation Details

### Subprocess Launch

**GradleRuntime.java:**
```java
ProcessBuilder pb = new ProcessBuilder(
  javaExecutable,
  "-cp", runtimeClasspath,
  "-Xmx16G",  // Custom heap size
  "se.alipsa.gade.runner.GradleRunnerMain"
);

Process process = pb.start();
```

**Wait for Handshake:**
```java
// Read from subprocess stdout
String line = reader.readLine();
JsonNode hello = objectMapper.readTree(line);

int port = hello.get("port").asInt();
String protocolVersion = hello.get("protocolVersion").asText();

// Connect to subprocess
socket = new Socket("localhost", port);
```

### JSON-RPC Protocol

**Eval Request (Gade → Subprocess):**
```json
{
  "cmd": "eval",
  "id": "uuid-1234",
  "script": "println 'hello'\n1 + 2",
  "bindings": {"customVar": "value"}
}
```

**Result Response (Subprocess → Gade):**
```json
{
  "type": "result",
  "id": "uuid-1234",
  "result": "3"
}
```

**Output Forwarding (Subprocess → Gade):**
```json
{
  "type": "out",
  "text": "hello\n"
}
```

**See:** `ARCHITECTURE.md` for complete protocol specification

### Error Handling

**Subprocess Crash:**
```java
process.waitFor(timeout, TimeUnit.SECONDS);
if (process.exitValue() != 0) {
  log.error("Runtime process crashed with exit code: {}", process.exitValue());
  showError("Runtime crashed. Restart?");
}
```

**Connection Timeout:**
```java
if (!socket.connect(timeout)) {
  log.error("Failed to connect to subprocess within {} seconds", timeout);
  process.destroyForcibly();
  throw new RuntimeException("Runtime connection timeout");
}
```

## Lessons Learned

**6 Months After Implementation:**

1. **Classpath conflicts eliminated**
   - Zero reports of "NoSuchMethodError" or "ClassNotFoundException" in external runtimes
   - Users successfully run projects with any Groovy version

2. **Startup overhead acceptable**
   - Users don't complain about 1-2 second wait
   - Process reuse makes subsequent runs fast

3. **Debugging challenges manageable**
   - JSON-RPC logging sufficient for troubleshooting
   - Very few protocol-related bugs

4. **Memory safety validated**
   - Multiple reports of OutOfMemoryError in scripts, but Gade UI survived
   - Users appreciate ability to set `-Xmx32G` for large datasets

5. **Maintenance burden reasonable**
   - JSON-RPC protocol stable (no changes since v1.0)
   - Subprocess management code well-tested

## References

- [ARCHITECTURE.md](../ARCHITECTURE.md) - System architecture overview
- [Task #13: Version JSON-RPC Protocol](../improvements/task-13-protocol-versioning.md)
- [ProtocolVersion.java](../../src/main/java/se/alipsa/gade/runtime/ProtocolVersion.java)

## Related ADRs

- [ADR 002: GroovyShell vs JSR223](002-groovyshell-vs-jsr223.md)
- [ADR 003: Completion Engine Architecture](003-completion-engine-architecture.md)

---

**Decision Date:** November 2025 (Sprint 2)
**Implementation Date:** December 2025 (Sprint 3)
**Author:** Gade Development Team
**Status:** Accepted and Implemented
