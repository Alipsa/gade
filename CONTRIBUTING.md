# Contributing to Gade

Thank you for your interest in contributing to Gade! This document provides guidelines and instructions for contributing to the project.

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Getting Started](#getting-started)
3. [Development Setup](#development-setup)
4. [Building the Project](#building-the-project)
5. [Running Tests](#running-tests)
6. [Code Style Guidelines](#code-style-guidelines)
7. [Pull Request Process](#pull-request-process)
8. [Testing Requirements](#testing-requirements)
9. [Documentation](#documentation)
10. [Commit Message Guidelines](#commit-message-guidelines)

---

## Code of Conduct

### Our Standards

- Be respectful and inclusive
- Accept constructive criticism gracefully
- Focus on what is best for the community
- Show empathy towards other community members

### Reporting Issues

If you experience or witness unacceptable behavior, please report it by opening an issue or contacting the maintainers.

---

## Getting Started

### Prerequisites

**Required:**
- **Java JDK 21 or later** (LTS recommended)
- **Git** for version control

**Recommended:**
- **IDE with Gradle support** (IntelliJ IDEA, Eclipse, VS Code)
- **8GB+ RAM** for development
- **macOS, Linux, or Windows** (all supported)

### Fork and Clone

1. **Fork the repository** on GitHub

2. **Clone your fork:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/gade.git
   cd gade
   ```

3. **Add upstream remote:**
   ```bash
   git remote add upstream https://github.com/perNyfelt/gade.git
   ```

4. **Create a feature branch:**
   ```bash
   git checkout -b feature/my-new-feature
   ```

---

## Development Setup

### IDE Configuration

#### IntelliJ IDEA (Recommended)

1. **Import project:**
   - File → Open → Select `gade` directory
   - Choose "Gradle" when prompted

2. **Set JDK:**
   - File → Project Structure → Project → SDK → Java 21

3. **Enable Gradle auto-import:**
   - Settings → Build, Execution, Deployment → Build Tools → Gradle
   - Check "Automatically import Gradle project changes"

4. **Code style:**
   - Settings → Editor → Code Style → Java
   - Import from: `AGENTS.md` (documented style)

#### Eclipse

1. **Import as Gradle project:**
   - File → Import → Gradle → Existing Gradle Project

2. **Configure Java 21:**
   - Project → Properties → Java Build Path → Libraries → Add JRE

#### VS Code

1. **Install extensions:**
   - Extension Pack for Java
   - Gradle for Java

2. **Open folder:**
   - File → Open Folder → Select `gade` directory

### Environment Variables

For development, you may want to set:

```bash
# Increase heap size for large projects
export JAVA_OPTS="-Xmx8G"

# Enable debug logging
export GADE_DEBUG=true
```

---

## Building the Project

### Quick Build

```bash
# Clean and build
./gradlew clean build

# Skip tests (faster)
./gradlew clean build -x test
```

### Run from Source

```bash
# Launch Gade in development mode
./gradlew run
```

**Hot reload:**
- Changes to `.groovy` files: Automatically picked up
- Changes to `.java` files: Require `./gradlew classes` and restart

### Create Distribution

```bash
# Build platform-specific distributions
./gradlew clean build runtimeZip

# Find outputs in:
ls -lh build/gade-*.zip
```

**Outputs:**
- `build/gade-linux.zip`
- `build/gade-macos.zip`
- `build/gade-windows.zip`

---

## Running Tests

### All Tests

```bash
# Run complete test suite
./gradlew test
```

**Test categories:**
- Unit tests (JUnit 5 + Mockito)
- GUI smoke tests (TestFX, headless mode)
- Integration tests

### Specific Tests

```bash
# Run tests for specific class
./gradlew test --tests CompletionEngineTest

# Run tests matching pattern
./gradlew test --tests "*Completion*"

# Run only GUI tests
./gradlew test --tests GadeSmokeTest
```

### Code Coverage

```bash
# Generate JaCoCo coverage report
./gradlew test jacocoTestReport

# View report
open build/reports/jacoco/test/html/index.html
```

**Coverage targets:**
- Core business logic: 70%+
- CompletionEngine: 80%+
- Infrastructure code: Best effort (many require integration tests)

**See:** `docs/improvements/coverage-analysis.md`

### Performance Benchmarks

```bash
# Run all benchmarks
./gradlew jmh

# Run specific benchmark suite
./gradlew jmh -Pjmh="CompletionEngine"
./gradlew jmh -Pjmh="FileOperations"
./gradlew jmh -Pjmh="GradleCache"

# Profile with GC
./gradlew jmh -Pjmh.profilers=gc
```

**See:** `docs/improvements/task-27-performance-benchmarks.md`

### GUI Tests (TestFX)

**Headless mode (default):**
```bash
# Tests run headless automatically
./gradlew test
```

**Show GUI during tests (debugging):**
```bash
# Override headless mode
./gradlew test -Dtestfx.headless=false
```

**See:** `docs/improvements/testfx-smoke-tests.md`

---

## Code Style Guidelines

### General Principles

1. **Readability over cleverness**
   - Clear code > concise code
   - Avoid overly clever one-liners

2. **Single Responsibility Principle**
   - Each class has ONE focused purpose
   - Extract helper classes when needed

3. **Immutability**
   - Prefer `final` fields
   - Use immutable classes where possible (CompletionContext, CompletionItem)

4. **Null Safety**
   - Never return `null` from collections (use `Collections.emptyList()`)
   - Document `@Nullable` parameters/returns
   - Prefer `Optional<T>` for methods that may not return a value

5. **Error Handling**
   - Catch specific exceptions (not `Exception` or `Throwable`)
   - Log exceptions before rethrowing
   - Provide helpful error messages

### Java Style

**Formatting:**
```java
// 2-space indentation
public class Example {
  private final String name;

  public Example(String name) {
    this.name = name;
  }

  public void method() {
    if (condition) {
      doSomething();
    } else {
      doSomethingElse();
    }
  }
}
```

**Naming conventions:**
- Classes: `PascalCase` (e.g., `CompletionEngine`)
- Methods: `camelCase` (e.g., `resolveClasspath`)
- Constants: `UPPER_SNAKE_CASE` (e.g., `MAX_RETRIES`)
- Packages: lowercase (e.g., `se.alipsa.gade.code.completion`)

**Ordering:**
```java
public class Example {
  // 1. Static fields
  private static final Logger log = ...;

  // 2. Instance fields
  private final String name;
  private int count;

  // 3. Constructors
  public Example() { ... }

  // 4. Public methods
  public void doSomething() { ... }

  // 5. Package-private methods
  void helperMethod() { ... }

  // 6. Private methods
  private void internalMethod() { ... }

  // 7. Inner classes
  private static class Inner { ... }
}
```

### Javadoc

**Public APIs must have Javadoc:**

```java
/**
 * Resolves the classpath for a Gradle project.
 * <p>
 * This method uses the Gradle Tooling API to resolve all dependencies
 * declared in build.gradle. The result is cached based on a fingerprint
 * of the build file.
 * </p>
 *
 * @param projectDir the project root directory containing build.gradle
 * @return set of JAR file paths on the resolved classpath
 * @throws IllegalArgumentException if projectDir is null or not a directory
 * @throws GradleException if dependency resolution fails
 */
public Set<File> resolveClasspath(File projectDir) {
  // ...
}
```

**Include examples for complex APIs:**

```java
/**
 * Creates a completion item using the builder pattern.
 * <p>
 * Example:
 * </p>
 * <pre>{@code
 * CompletionItem item = CompletionItem.builder()
 *     .label("println")
 *     .kind(CompletionKind.METHOD)
 *     .insertText("println()")
 *     .cursorOffset(-1)
 *     .build();
 * }</pre>
 *
 * @return a new builder instance
 */
public static Builder builder() {
  return new Builder();
}
```

### Groovy Style

**Follow Java conventions** where applicable.

**Groovy-specific:**
```groovy
// Use def for local variables
def name = 'Alice'
def numbers = [1, 2, 3]

// Explicit types for method parameters
void processData(String input, List<Integer> numbers) {
  // ...
}

// Closures: space after opening brace
numbers.each { num ->
  println num
}
```

### Git Commit Messages

**Format:**
```
Short summary (50 chars or less)

Detailed explanation (wrap at 72 characters):
- What changed
- Why the change was necessary
- Any side effects or gotchas

Closes #123
```

**Examples:**

```
Add code completion for SQL table names

Implement SqlCompletionEngine to provide completions for:
- SQL keywords (SELECT, FROM, WHERE, etc.)
- Table names from connected databases
- Column names based on selected table

Uses JDBC metadata API to introspect database schema.
Performance: ~50ms for typical database (100 tables).

Closes #42
```

```
Fix NullPointerException in GradleUtils.resolveClasspath

Issue: NPE when build.gradle doesn't exist in project directory.

Solution: Check file existence before calling Gradle Tooling API.
Return empty set for non-Gradle projects instead of crashing.

Closes #56
```

**Co-authoring:**

For pair programming or significant collaboration:
```
Co-Authored-By: Name <email@example.com>
```

---

## Pull Request Process

### Before Submitting

**Checklist:**

- [ ] Code builds without errors (`./gradlew build`)
- [ ] All tests pass (`./gradlew test`)
- [ ] New code has test coverage (70%+ for business logic)
- [ ] Public APIs have Javadoc
- [ ] No `System.out.println` in production code (use `Logger`)
- [ ] Code follows style guidelines
- [ ] Commit messages are clear and descriptive
- [ ] CHANGELOG.md updated (if user-facing change)

### Submitting PR

1. **Push to your fork:**
   ```bash
   git push origin feature/my-new-feature
   ```

2. **Create Pull Request on GitHub:**
   - Go to your fork on GitHub
   - Click "Compare & pull request"

3. **Fill out PR template:**

   ```markdown
   ## Description
   Brief description of what this PR does.

   ## Motivation
   Why is this change necessary? What problem does it solve?

   ## Changes
   - List of specific changes
   - Affected components
   - Breaking changes (if any)

   ## Testing
   - How was this tested?
   - Manual testing steps
   - Automated tests added

   ## Screenshots
   (If UI changes)

   ## Checklist
   - [ ] Tests pass
   - [ ] Documentation updated
   - [ ] No breaking changes (or documented in CHANGELOG)

   Closes #123
   ```

4. **Await review:**
   - Maintainers will review within 3-5 business days
   - Address feedback by pushing new commits
   - No need to force-push (preserve history)

### Review Process

**Reviewers will check:**
- Code quality and style
- Test coverage
- Documentation completeness
- Performance implications
- Breaking changes

**Common feedback:**
- "Add tests for edge case X"
- "Extract this into a helper method"
- "Update Javadoc to clarify Y"

**Merging:**
- Once approved, maintainer will merge
- PR will be squashed into a single commit (usually)
- Your name will be preserved in commit history

---

## Testing Requirements

### Unit Tests

**Use JUnit 5 + Mockito:**

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompletionEngineTest {

  @Test
  void testSimpleCompletion() {
    CompletionContext context = CompletionContext.builder()
      .fullText("def x = 'hello'\nx.")
      .caretPosition(17)
      .build();

    List<CompletionItem> items = engine.complete(context);

    assertTrue(items.size() > 0, "Should have completions");
    assertTrue(
      items.stream().anyMatch(i -> i.label().equals("toUpperCase")),
      "Should suggest String methods"
    );
  }
}
```

**Mock external dependencies:**

```java
@Test
void testGradleResolution() {
  // Mock Gradle Tooling API
  GradleConnector connector = mock(GradleConnector.class);
  ProjectConnection connection = mock(ProjectConnection.class);

  when(connector.forProjectDirectory(any())).thenReturn(connection);
  when(connection.model(IdeaProject.class)).thenReturn(...);

  Set<File> classpath = gradleUtils.resolveClasspath(projectDir);

  assertEquals(5, classpath.size());
}
```

### Integration Tests

**For complex interactions:**

```java
@Test
void testRuntimeProcessExecution() throws Exception {
  RuntimeConfig config = new RuntimeConfig("Test", RuntimeType.GRADLE, ...);
  RuntimeProcessRunner runner = new RuntimeProcessRunner(config, ...);

  runner.start();
  String result = runner.execute("1 + 2");

  assertEquals("3", result);

  runner.stop();
}
```

### GUI Tests (TestFX)

**For JavaFX components:**

```java
@ExtendWith(ApplicationExtension.class)
class GadeSmokeTest {

  @Start
  void onStart(Stage stage) {
    new Gade().start(stage);
  }

  @Test
  void testMenuBar(FxRobot robot) {
    robot.clickOn("File");
    robot.clickOn("New Script");

    // Verify new tab created
    assertNotNull(robot.lookup(".tab-pane").query());
  }
}
```

**See:** `src/test/java/se/alipsa/gade/GadeSmokeTest.java`

### Test Organization

**Package structure mirrors main:**
```
src/
├── main/
│   └── java/
│       └── se/alipsa/gade/
│           └── code/
│               └── completion/
│                   └── CompletionEngine.java
└── test/
    └── java/
        └── se/alipsa/gade/
            └── code/
                └── completion/
                    ├── CompletionEngineTest.java
                    ├── CompletionContextTest.java
                    └── CompletionItemTest.java
```

### Coverage Goals

| Component | Target Coverage | Rationale |
|-----------|----------------|-----------|
| CompletionEngine | 80%+ | Core user-facing feature |
| GradleUtils | 50%+ | Infrastructure-heavy, hard to unit test |
| RuntimeProcessRunner | 60%+ | Integration-heavy |
| UI Components | 30%+ | TestFX smoke tests sufficient |

**See:** `docs/improvements/coverage-analysis.md`

---

## Documentation

### When to Update Docs

**Always update documentation when:**
- Adding a new feature
- Changing public API
- Fixing a bug that affected documented behavior
- Adding a new extension point

### Documentation Types

**1. Code Comments**
```java
// Explain WHY, not WHAT
// Good:
// Use ConcurrentHashMap for thread-safe access from completion threads
private final Map<String, CompletionEngine> engines = new ConcurrentHashMap<>();

// Bad:
// Create a map
private final Map<String, CompletionEngine> engines = new HashMap<>();
```

**2. Javadoc**
- All public classes, methods, interfaces
- Include examples for non-trivial APIs
- Document thread-safety guarantees
- Specify performance characteristics if relevant

**3. User Documentation**
- Update `README.md` for user-facing features
- Update `docs/user-guide.md` for usage instructions
- Add troubleshooting entries for common issues

**4. Developer Documentation**
- Update `ARCHITECTURE.md` for architectural changes
- Create ADR (Architecture Decision Record) for significant decisions

### Writing ADRs

**Template:** `docs/adr/template.md`

**Example:**

```markdown
# ADR 001: Separate Process Runtimes

## Status
Accepted

## Context
Gradle and Maven have complex classpaths that conflict with Gade's UI dependencies...

## Decision
Execute Gradle/Maven scripts in separate JVM processes, communicating via JSON-RPC...

## Consequences
**Positive:**
- Isolated classpaths (no conflicts)
- Runtime crashes don't affect Gade UI

**Negative:**
- IPC overhead (~1-2 seconds startup)
- More complex architecture
```

---

## Issue Reporting

### Before Opening an Issue

**Search existing issues** to avoid duplicates.

### Bug Reports

**Include:**
- **Gade version:** (check Help → About)
- **Operating system:** (macOS 13.5, Ubuntu 22.04, Windows 11, etc.)
- **Java version:** (output of `java -version`)
- **Steps to reproduce:**
  1. Open Gade
  2. Create new Groovy script
  3. Type: `println 'hello`
  4. Click Run
  5. **Expected:** Print "hello"
  6. **Actual:** Error dialog

**Attach:**
- Log file: `~/.gade/logs/gade.log`
- Screenshot (if UI issue)
- Minimal reproduction script

### Feature Requests

**Include:**
- **Use case:** What problem does this solve?
- **Proposed solution:** How should it work?
- **Alternatives considered:** What else did you think about?
- **Examples:** Similar features in other tools

---

## Release Process (Maintainers Only)

**For maintainers preparing a release:**

1. **Update version:** `build.gradle`
   ```groovy
   version = '1.1.0'
   ```

2. **Update CHANGELOG.md**

3. **Create release branch:**
   ```bash
   git checkout -b release/v1.1.0
   ```

4. **Run full test suite:**
   ```bash
   ./gradlew clean build test jacocoTestReport jmh
   ```

5. **Build distributions:**
   ```bash
   ./gradlew runtimeZip
   ```

6. **Tag release:**
   ```bash
   git tag -a v1.1.0 -m "Release version 1.1.0"
   git push origin v1.1.0
   ```

7. **Create GitHub release:**
   - Go to Releases → New Release
   - Upload `build/gade-*.zip` files
   - Copy CHANGELOG entry to release notes

---

## Getting Help

**Questions?**
- Open a [GitHub Discussion](https://github.com/perNyfelt/gade/discussions)
- Check [existing issues](https://github.com/perNyfelt/gade/issues)
- Read [documentation](docs/)

**Found a bug?**
- [Open an issue](https://github.com/perNyfelt/gade/issues/new)

**Want to contribute but don't know where to start?**
- Look for issues labeled `good first issue`
- Check [TODO.md](todo.md) for planned features

---

## License

By contributing to Gade, you agree that your contributions will be licensed under the MIT License.

---

## Thank You!

Your contributions make Gade better for everyone. Thank you for taking the time to contribute!

---

**Document Version:** 1.0.0
**Last Updated:** February 3, 2026
