# Example compilation

## Problem

Several chart and cookbook scripts used package names, constructors, and chart helpers from older Matrix releases. The regular test suite did not compile scripts under `examples/`, so these errors went unnoticed.

## Solution

The `compileExamples` Gradle task compiles the chart and cookbook scripts that use Gade's bundled Matrix dependencies. It also invokes `compileGroovy` in the standalone `examples/xcharts`, `examples/project`, `examples/library`, and `examples/gradleCpTest` projects, each with its own dependencies. The root `test` task depends on `compileExamples`, so CI and local test runs check these scripts automatically.

Scripts with independent `@Grab` dependencies and other standalone projects retain their own build or runtime requirements. This gate checks Groovy compilation; dynamic method calls and visual output still need a runtime check.

## Testing

Run `./gradlew compileExamples -g ./.gradle-user` for a fast compilation check. Run `./gradlew test -g ./.gradle-user` for the full suite and the example compilation gate.

## Impact

The examples use the Matrix packages and chart APIs shipped with the current Gade build. Compilation failures in the covered scripts now fail the main test task.
