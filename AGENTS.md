# Repository Guidelines

## Project Structure & Module Organization
- The Gradle build expects JVM sources in `src/main/java` and supporting Groovy/utility scripts in `src/main/scripts` and `src/bin`. 
- Application resources (icons, templates) should live under `src/main/resources`, while test fixtures sit in `src/test/{java,groovy,resources,sql}`. 
- Executable examples go in `examples/`, shared docs live in `docs/`, and generated artifacts stay in `build/` (never commit them). 
- Launcher assets for packaged runtimes are kept in `platform/` and `install.sh` shows how to assemble a platform-specific bundle.

## Build, Test, and Development Commands
- `./gradlew build -g ./.gradle-user` compiles Java/Groovy sources, runs the full test suite, and produces jars in `build/libs`. 
- Use `./gradlew run -g ./.gradle-user` for a fast developer launch that opens the JavaFX UI with your local classes. 
- `./gradlew runtimeZip -g ./.gradle-user` creates the distributable runtime zips under `build/`. Run `./gradlew dependencyUpdates -Drevision=release -g ./.gradle-user` before touching `version.properties` to see vetted upgrades. 
- If javafx is not supported in the environment, use targeted testing, `./gradlew test --tests "se.alipsa.gade.utils.*" -g ./.gradle-user` runs only specified packages or classes.
- Always run `./gradlew test -g ./.gradle-user` when a task is finished to validate changes.
- **macOS Note**: TestFX tests are automatically skipped on macOS due to known SIGTRAP crashes in Monocle. Tests pass successfully but JVM crashes during shutdown. Run `./gradlew test -DskipTestFx=false -g ./.gradle-user` to force enable (tests will pass but build may report failure due to crash). Run `./gradlew test -DskipTestFx=true -g ./.gradle-user` on any platform to skip TestFX tests.

## Runtime Distribution & Launcher Scripts
- The `./gradlew runtimeZip -g ./.gradle-user` task creates platform-specific runtime packages under `build/` that bundle the application, dependencies, and a JDK. 
- Each distribution contains launcher scripts that must be configured with the correct JVM arguments for Java 21 compatibility with the Gradle Tooling API.

**Critical Scripts**:
- `src/bin/cponly/gade.sh` - Unix/Linux/macOS launcher script (manually maintained, copied to runtime distributions)
- `src/bin/cponly/gade.cmd` - Windows launcher script (manually maintained, copied to runtime distributions)
- `build/image/gade-*/bin/gade` - Auto-generated Unix scripts (created by beryx runtime plugin, configured via `build.gradle`)
- `build/image/gade-*/bin/gade.bat` - Auto-generated Windows scripts (created by beryx runtime plugin, configured via `build.gradle`)

**JVM Arguments for Java 21**:
The main Gade application (NOT the splash screen) requires these JVM args to allow Gradle Tooling API access to internal JDK modules:
```
--enable-native-access=javafx.graphics,javafx.media,javafx.web,ALL-UNNAMED
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.net=ALL-UNNAMED
```

These are configured in:
1. `build.gradle` - `applicationDefaultJvmArgs` for `./gradlew run` and runtime plugin auto-generated scripts
2. `src/bin/cponly/gade.sh` - Main application commands only (lines ~100-130, not splash screen)
3. `src/bin/cponly/gade.cmd` - Main application command only (lines ~41-50, not splash screen)

**Important**: The splash screen commands only need `--enable-native-access` for JavaFX, not the `--add-opens` flags. 
Only the main Gade application needs full module access for the Gradle Tooling API. See `docs/GRADLE_TOOLING_API_JAVA21.md` for details.

## Script Execution & Runtime Architecture
Gade executes Groovy scripts in a **subprocess** (`RuntimeProcessRunner`) that communicates with the main UI process via XML over TCP sockets (see `ProtocolXml`). The subprocess classpath is constructed by `GroovyRuntimeManager.buildClassPathEntries()`.

**Runtime types** (`RuntimeType` enum): GADE, GRADLE, MAVEN, CUSTOM.

**Key classes and their roles:**
- `GroovyRuntimeManager` (`console/GroovyRuntimeManager.java`) - Orchestrates runtime lifecycle: creates the classloader, builds the subprocess classpath, and starts the `RuntimeProcessRunner`. The `resetClassloaderAndGroovy()` method is the main entry point for runtime initialization.
- `RuntimeClassLoaderFactory` (`runtime/RuntimeClassLoaderFactory.java`) - Creates a `GroovyClassLoader` per runtime type. All classloaders use `ClassUtils.getBootstrapClassLoader()` as parent for isolation from Gade application classes.
- `RuntimeProcessRunner` (`runtime/RuntimeProcessRunner.java`) - Manages the long-lived subprocess: starts a JVM with the constructed classpath, connects via TCP socket, and provides async `eval()` for script execution.
- `GadeRunnerMain` (`runner/GadeRunnerMain.java`) - Entry point for the subprocess JVM. Receives scripts over the socket and evaluates them in a `GroovyShell`.

**Classpath construction (`buildClassPathEntries`):**
- **GADE runtime**: Only boot JAR on `-cp`. Groovy/Ivy + engine JAR loaded into ProcessRootLoader. Users add dependencies via `@Grab`.
- **Gradle runtime**: Only boot JAR on `-cp`. Groovy/Ivy from deps + engine JAR loaded into ProcessRootLoader. Project dependencies resolved via Gradle Tooling API.
- **Maven runtime**: Only boot JAR on `-cp`. Groovy/Ivy from deps + engine JAR loaded into ProcessRootLoader. Project dependencies resolved from pom.xml.
- **Custom runtime**: Boot JAR + engine JAR + Groovy home jars + configured additional jars + classloader URLs on `-cp`.

**Dependency resolution helpers:**
- `GradleUtils` / `GradleDependencyResolver` (`utils/gradle/`) - Resolves classpath via Gradle Tooling API.
- `MavenClasspathUtils` (`utils/maven/`) - Resolves classpath from pom.xml via `MavenUtils`.
- **Upstream dependency policy**: If a bug involves a dependency with `groupId` starting with `se.alipsa`, do not add a local workaround in Gade as the primary fix. Fix it in the related `se.alipsa*` project and report it there (preferably as a GitHub issue) before or alongside any temporary mitigation.

**Distribution layout** (after `./gradlew runtimeZip`):
- `lib/groovy/` - Groovy and Ivy jars for the subprocess classpath.
- `lib/runtimes/` - `gade-runner-boot.jar` (JDK-only bootstrap, on subprocess `-cp`) and `gade-runner-engine.jar` (Groovy-dependent engine, loaded into ProcessRootLoader).
- `lib/app/` - Gade application jars (never included in subprocess classpath).

## Coding Style & Naming Conventions
- Java and Groovy code both follow two-space indents, braces on the same line, and descriptive camelCase identifiers (`matrixLoader`, `semanticVersion`). 
- Prefer `final` where fields should not mutate, and use `Optional`/`null` defensively around UI state. 
- Logging should go through Log4j2’s `LogManager` (`private static final Logger log = ...`). 
- Resource bundles, icons, and templates belong in `src/main/resources` with lowercase dashed filenames.

## Testing Guidelines
- UI-light logic belongs in `src/test/java` with naming `ClassNameTest` or behavior-oriented `FeatureXTest`. 
- Groovy scripts under test can mirror their runtime location inside `src/test/groovy`. 
- Use JUnit Jupiter APIs and keep tests deterministic—mock filesystem or Gradle calls where feasible. 
- When a bug is reported and no test currently fails for it, add a regression test that reproduces and guards against the issue whenever feasible.
- Run `./gradlew test` (or `./gradlew test --tests "se.alipsa.gade.utils.*"` for a subset) before opening a PR and attach new fixtures to `src/test/resources` rather than embedding literals.

## Commit & Pull Request Guidelines
- Match the existing history: short, imperative commit subjects ("Ensure ivy runtime is available to isolated scripts"), optional context in the body, and reference issues with `#id` when applicable. 
- Each PR should describe purpose, highlight risky areas, enumerate manual/automated tests, and include UI screenshots or screen recordings when user-facing panes change. 
- Keep PRs focused; split refactors from feature changes, and check `todo.md` for related tasks before submitting.

## Documentation Guidelines
- Feature explanations, improvement summaries, and architectural decisions should be documented in `docs/improvements/` unless otherwise instructed. 
- Use clear markdown formatting with sections for Problem, Solution, Testing, and Impact. 
- Include code examples where helpful, and link to related files with relative paths. 
- Keep the root directory clean—only essential files like README.md, CLAUDE.md, and AGENTS.md belong there.

## Security & Configuration Tips
- Never commit secrets or developer-specific tweaks; put overrides in `env.sh`/`env.cmd` alongside `gade.sh` and keep them untracked. 
- Use `JAVA_OPTS` to adjust memory or HiDPI scaling, and prefer JDK 21+ to match the toolchain enforced in `build.gradle`. 
- When configuring JDBC connections or REST endpoints for tests, point them at local containers or sanitized fixtures so the repo stays reproducible.
