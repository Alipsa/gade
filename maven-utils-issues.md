# Notes / Issues Found in `maven-3.9.11-utils` (during Gade integration)

## `MavenUtils.runMaven(...)` limitations
- No way to specify a per-invocation Java home (Gade runtimes can override `JAVA_HOME`, but `MavenUtils.runMaven` does not expose `InvocationRequest#setJavaHome(...)` nor `Invoker#setJavaHome(...)`).
- `runMaven` passes the full `mvnArgs` array into `InvocationRequest#setGoals(...)`. This means flags like `-Dfoo=bar` are treated as “goals”, which is not how Maven Invoker expects arguments.
  - It does additionally parse `-Dkey=value` into `request.getProperties()`, but the `-D...` tokens still remain in the goals list.

## `EnvUtils.parseArguments(...)` limitations
- Only supports `-Dkey=value`.
- Does not support:
  - `-Dkey` (no explicit value)
  - other common Maven flags (`-q`, `-e`, `-X`, `-o`, `-s settings.xml`, `-pl`, `-am`, etc.)

## `MavenUtils.locateMavenHome()` / PATH discovery
- When `MAVEN_HOME` is not set, `locateMaven()` searches for `mvn` on `PATH` and returns the *parent directory* of the directory containing `mvn`.
  - Example: if `mvn` is in `/usr/bin`, this returns `/usr`, which is typically not a Maven distribution home and may cause `Invoker#setMavenHome(...)` to be misconfigured.

