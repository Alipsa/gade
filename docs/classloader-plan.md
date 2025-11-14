# Classloader Isolation Plan

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
