# Image display paths

## Problem

Scripts running in Gade's subprocess could send a `File` to `io.display`, but the GUI dispatcher gave `display(File, ...)` and `display(Object, ...)` equal scores. Depending on reflection order, it selected the generic overload, which only accepts a Tablesaw Plotly figure. A `Path` argument was serialized as a string and had no Gade display overload.

## Solution

The dispatcher now prefers an exact parameter type, then a specific assignable type, before `Object`. Gade also accepts `display(Path, String...)` and transports paths as paths across the subprocess protocol. The Path overload delegates to the existing File display behavior.

`gi-common` and `gi-fx` already provide `display(File, String...)`. They do not currently provide `display(Path, String...)`; Gade's Path overload is local to Gade.

## Testing

`RuntimeProcessRunnerProtocolTest` checks that File, Path, and String arguments select their specific overloads. `ArgumentSerializerTest` checks a Path round trip through the protocol. Run `./gradlew test -g ./.gradle-user` for the full suite.

## Impact

Scripts can use `io.display(file)` or `io.display(path)` to show an image without entering the Plotly figure handler.
