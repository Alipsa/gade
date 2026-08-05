# Rhino JavaScript Engine Migration

## Problem

The JavaScript tab used the standalone Nashorn engine and Nashorn-specific values such as
`ScriptObjectMirror` and `Java.to`. Nashorn is no longer the preferred JavaScript engine for
the application, and its ECMAScript support is limited.

## Solution

Gade now uses Mozilla Rhino through its JSR-223 integration. The application includes matching
`org.mozilla:rhino` and `org.mozilla:rhino-engine` dependencies and creates a Rhino engine for
each JavaScript session.

The existing JavaScript tab behavior is preserved:

- JavaScript sessions retain their bindings, output/error routing, and restart controls.
- The `View(data, title)` helper remains available.
- JavaScript arrays are converted to Java matrices by Gade’s Java-side Rhino conversion code
  before they are passed to the matrix builder.
- JavaScript code should use `io.view(data, title)` when calling the lower-level interaction
  object directly; Nashorn’s `Java.to(...)` is not required.

## Testing

`RhinoScriptEngineTest` verifies Rhino JSR-223 creation, modern JavaScript syntax, JavaScript-to-
Java array conversion, and the bundled `View` helper.

## Impact

The About dialog reports Rhino instead of Nashorn. The JavaScript tab remains non-cancellable;
long-running JavaScript or I/O can still require terminating the application externally.
