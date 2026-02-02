# Repository Guidelines

## Project Structure & Module Organization
The Gradle build expects JVM sources in `src/main/java` and supporting Groovy/utility scripts in `src/main/scripts` and `src/bin`. Application resources (icons, templates) should live under `src/main/resources`, while test fixtures sit in `src/test/{java,groovy,resources,sql}`. Executable examples go in `examples/`, shared docs live in `docs/`, and generated artifacts stay in `build/` (never commit them). Launcher assets for packaged runtimes are kept in `platform/` and `install.sh` shows how to assemble a platform-specific bundle.

## Build, Test, and Development Commands
`./gradlew build -g ./.gradle-user` compiles Java/Groovy sources, runs the full test suite, and produces jars in `build/libs`. Use `./gradlew run -g ./.gradle-user` for a fast developer launch that opens the JavaFX UI with your local classes. `./gradlew runtimeZip -g ./.gradle-user` creates the distributable runtime zips under `build/`. Run `./gradlew dependencyUpdates -Drevision=release -g ./.gradle-user` before touching `version.properties` to see vetted upgrades. If javafx is not supported in the environment, use targeted testing, `./gradlew test --tests "se.alipsa.gade.utils.*" -g ./.gradle-user` runs only specified packages or classes.
- Always run `./gradlew test -g ./.gradle-user` when a task is finished to validate changes.

## Coding Style & Naming Conventions
Java and Groovy code both follow two-space indents, braces on the same line, and descriptive camelCase identifiers (`matrixLoader`, `semanticVersion`). Prefer `final` where fields should not mutate, and use `Optional`/`null` defensively around UI state. Logging should go through Log4j2’s `LogManager` (`private static final Logger log = ...`). Resource bundles, icons, and templates belong in `src/main/resources` with lowercase dashed filenames.

## Testing Guidelines
UI-light logic belongs in `src/test/java` with naming `ClassNameTest` or behavior-oriented `FeatureXTest`. Groovy scripts under test can mirror their runtime location inside `src/test/groovy`. Use JUnit Jupiter APIs and keep tests deterministic—mock filesystem or Gradle calls where feasible. Run `./gradlew test` (or `./gradlew test --tests "se.alipsa.gade.utils.*"` for a subset) before opening a PR and attach new fixtures to `src/test/resources` rather than embedding literals.

## Commit & Pull Request Guidelines
Match the existing history: short, imperative commit subjects ("Ensure ivy runtime is available to isolated scripts"), optional context in the body, and reference issues with `#id` when applicable. Each PR should describe purpose, highlight risky areas, enumerate manual/automated tests, and include UI screenshots or screen recordings when user-facing panes change. Keep PRs focused; split refactors from feature changes, and check `todo.md` for related tasks before submitting.

## Documentation Guidelines
Feature explanations, improvement summaries, and architectural decisions should be documented in `docs/improvements/` unless otherwise instructed. Use clear markdown formatting with sections for Problem, Solution, Testing, and Impact. Include code examples where helpful, and link to related files with relative paths. Keep the root directory clean—only essential files like README.md, CLAUDE.md, and AGENTS.md belong there.

## Security & Configuration Tips
Never commit secrets or developer-specific tweaks; put overrides in `env.sh`/`env.cmd` alongside `gade.sh` and keep them untracked. Use `JAVA_OPTS` to adjust memory or HiDPI scaling, and prefer JDK 21+ to match the toolchain enforced in `build.gradle`. When configuring JDBC connections or REST endpoints for tests, point them at local containers or sanitized fixtures so the repo stays reproducible.
