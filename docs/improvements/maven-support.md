# Maven Support (Plan for Review)

## Goals
- Restore Maven support on par with existing Gradle support:
  - Use `pom.xml` to define the runtime classpath (like `build.gradle` does today).
  - Run Maven builds from inside the GUI, including specifying goals/targets to run.
- Enhance `Code -> Create project` and `Code -> Create library`:
  - Add a build system toggle: `Maven` / `Gradle` / `None`.
  - Generate `pom.xml` (Maven), or `build.gradle` + `settings.gradle` (Gradle), or no build files (None).
- Default runtime behavior:
  - Maven project → default runtime `Maven`
  - Gradle project → default runtime `Gradle`
  - No build system → default runtime `Gade`
- Use `se.alipsa:maven-3.9.11-utils` for Maven invocations (classpath resolution + build execution).
- If `maven-3.9.11-utils` issues are found, capture them explicitly for fixing in that sister project.

## Current State (What Exists Today)
- Built-in runtimes include `Gradle`, `Maven`, and `Gade` (`se.alipsa.gade.runtime.RuntimeManager`).
- Gradle runtime:
  - Classpath hydration via tooling API (`se.alipsa.gade.utils.gradle.GradleUtils`).
  - In-GUI build execution via `GradleTab` (`se.alipsa.gade.code.gradle.GradleTab`).
- Maven runtime:
  - Minimal `pom.xml` parsing and direct-dependency resolving (`se.alipsa.gade.runtime.MavenResolver`).
  - Runtime classloader wires Maven dependencies + `target/classes` outputs (`se.alipsa.gade.runtime.RuntimeClassLoaderFactory`).
- Project creation:
  - Dialog title says “Create Maven Project Wizard”, but current implementation creates Gradle files (`se.alipsa.gade.menu.CreateProjectWizardDialog`, `se.alipsa.gade.menu.MainMenu#showProjectWizard`).
- Templates already exist:
  - `src/main/resources/templates/project-pom.xml`
  - `src/main/resources/templates/package-pom.xml`
  - `src/main/resources/templates/project_build.gradle`
  - `src/main/resources/templates/library_build.gradle`

## Proposed Approach
### 1) Introduce a first-class “build system” model used by wizards and runtime selection
- Add a small enum/model (e.g. `BuildSystem { MAVEN, GRADLE, NONE }`) used by:
  - Create Project wizard result
  - Create Library wizard result
  - Default runtime selection on project creation
  - Optional: file-type handling (`pom.xml` → Maven tab)

- Add a small “execution context” concept so runtimes can decide whether to include `test` scoped dependencies:
  - Treat a run as “test context” when the active file is under `src/test/**` (and for backward compatibility also `test/**`), otherwise “main context”.
  - For build execution (Gradle/Maven build tabs), treat it as “test context” when the requested tasks/goals include `test`.

### 2) Maven classpath resolution via `maven-3.9.11-utils`
Replace (or augment) `MavenResolver` so Maven runtime classpath is produced by Maven itself (transitive deps, dependencyManagement, properties, exclusions, etc.), similar in spirit to how Gradle uses Tooling API.

Implementation sketch:
- New utility wrapper in Gade (e.g. `se.alipsa.gade.utils.maven.MavenUtils`):
  - `List<File> resolveClasspath(File projectDir, boolean testContext, ConsoleTextArea console, String javaHomeOverride)`
  - Uses `maven-3.9.11-utils` to:
    - Run Maven goal(s) that reliably outputs a classpath (e.g. `dependency:build-classpath`, or the library’s built-in API if it has one).
    - Parse the produced classpath into jar `File`s.
    - Include `test` scoped dependencies when `testContext == true` (to support running tests from inside Gade).
  - Adds project outputs: `target/classes`, `target/test-classes` (controlled by existing “Add build dir to classpath” preference if appropriate).
- Caching (to match Gradle runtime UX/perf):
  - Cache classpath entries keyed by a fingerprint of `pom.xml` (and maybe `.mvn/*`, `settings.xml`, active profiles, Java version).
  - Store under a project-local cache dir (similar to `.gradle-gade-tooling`, e.g. `.maven-gade-cache`).
  - Invalidation: if fingerprint changes, recompute; otherwise reuse.
- Fallback behavior:
  - If Maven invocation fails (no `mvn` available, offline/no deps yet, etc.), optionally fall back to current `MavenResolver` as a “best-effort direct deps” mode, but make it explicit in console output so users understand limitations.

### 3) Maven build execution from within the GUI (like `GradleTab`)
- Add a `MavenTab` (analogous to `se.alipsa.gade.code.gradle.GradleTab`):
  - Attach to `pom.xml` editing by mapping `pom.xml` to a Maven build tab (similar to how `build.gradle` maps to `GradleTab` today).
  - UI controls:
    - “Goals:” text field (default: `test`).
    - Persist last used goals and use that as the default next time.
    - Optional “Options:” (profiles, `-DskipTests`, `-P...`, `-D...`) if we want parity with common Maven usage.
  - Execution:
    - Use `maven-3.9.11-utils` to run Maven in a background task (JavaFX `Task`), streaming stdout/stderr to `ConsoleComponent` like Gradle does.
    - Honor the selected runtime’s `javaHome` (set `JAVA_HOME` for the Maven process or use library support if present).
    - On success: optionally prompt/scroll, and (if “Restart session after build” is enabled) restart runtime so new `target/classes` output becomes active.
    - On failure: show actionable hints similar to Gradle (e.g., missing Maven, Java mismatch, offline deps).

### 4) Wizard updates: Create Project + Create Library
- Update `CreateProjectWizardDialog` and `CreateLibraryWizardDialog` to include a build system toggle:
  - Radio buttons: `Maven`, `Gradle`, `None`
  - Persist last selection (optional) to reduce friction.
- Generation logic:
  - Maven:
    - Write `pom.xml` from `templates/project-pom.xml` or `templates/package-pom.xml`.
    - Create standard Maven/Groovy source layout:
      - `src/main/groovy`, `src/test/groovy`, `src/test/resources` (and `src/main/resources` if needed)
  - Gradle:
    - Keep existing behavior: `build.gradle` (+ `settings.gradle` for full project/library).
    - Switch project layout to `src/main/groovy` + `src/test/groovy` for symmetry with Maven projects.
    - Update the Gradle templates to match the new layout (`src/main/resources/templates/project_build.gradle`, `src/main/resources/templates/library_build.gradle`).
  - None:
    - Create only the script/test skeleton without any build files.
- Runtime selection on creation:
  - Immediately set selected runtime for the new project dir based on build system chosen:
    - Maven → select `RuntimeManager.RUNTIME_MAVEN`
    - Gradle → select `RuntimeManager.RUNTIME_GRADLE`
    - None → select `RuntimeManager.RUNTIME_GADE`
  - Refresh runtimes menu after changing project root.

### 5) Runtime reload-on-save parity (Gradle + Maven)
- Gradle already reloads runtime when classpath-affecting Gradle files are saved.
- Add the same behavior for Maven runtime:
  - When active runtime is Maven and `pom.xml` is saved within the project root, debounce-restart the runtime.
  - Consider also watching `.mvn/**` (e.g. `extensions.xml`, `maven.config`) if we support those as part of classpath computation.

## Testing Strategy
- Unit tests (no network, no external Maven required):
  - Add an abstraction around Maven invocation (e.g. `MavenInvoker` interface) so tests can fake classpath output + build results deterministically.
  - Tests for:
    - Build system toggle propagates to generated files (pom vs gradle vs none).
    - Default runtime selection matches chosen build system.
    - Maven classpath cache invalidation on `pom.xml` change.
    - Maven build goal parsing (“clean test” → args array).
- Keep integration-style Maven invocation tests optional/disabled unless the environment has Maven available and local repo pre-warmed.

## Deliverables / Milestones
1. Build system toggle added to both wizards + generation templates wired.
2. Maven runtime classpath resolved via `maven-3.9.11-utils` (with caching + clear console diagnostics).
3. In-GUI Maven build runner with goal selection (MavenTab).
4. Runtime defaults + selection persistence aligned with build system.
5. Tests + doc updates.

## `maven-3.9.11-utils` Validation Checklist (Issues to Capture)
While wiring it in, verify and document any gaps/bugs in `maven-3.9.11-utils`:
- Can it run Maven goals with:
  - Streaming stdout/stderr (not buffering huge outputs)
  - Working directory = project root
  - Custom `JAVA_HOME` / toolchain selection
  - Offline mode support (`-o`) and custom settings.xml
  - Correct exit code propagation and error reporting
- Can it produce a reliable classpath for the selected scopes:
  - `compile` / `runtime` (and optionally `test` when needed)
  - Multi-module projects (at least fail gracefully with a clear message if unsupported)
  - Windows path separator handling (`;` vs `:`)
