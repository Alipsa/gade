# Shell Commands Through `io`

## Problem

Groovy sessions can use the GUI interaction object injected as `io`, but shell
command execution was not available through Gade's gi-fx dependency.

## Solution

Gade uses `se.alipsa.gi:gi-fx:0.4.1-SNAPSHOT`, which adds shell command methods
to the inherited interaction API:

```groovy
io.sh('ls -al') // streams stdout to the Gade console and returns it
output = io.sh('ls -al')
result = io.shell('ls -al')
println result.stdout
println result.stderr
println result.exitCode
println result.success
```

The methods are available in GADE, Gradle, Maven, and Custom runtimes. For
external runtimes, `ShellResult` is transported as a map so its properties can
be accessed from Groovy without adding gi-fx to the runner process classpath.
`io.sh(command)` also routes live standard output to Gade's console while
retaining the captured output as its return value.

## Testing

Runtime interaction tests cover `sh()` and `shell()` through the socket-backed
`io` proxy for every supported runtime type. Serializer tests cover transport
of `ShellResult` fields.

## Impact

Shell commands execute with the behavior and permissions of the host process,
as defined by gi-fx. Existing `io` methods and runtime bindings are unchanged.
