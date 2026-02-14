# FQCN Resolution Error Messages

## Problem

When a Groovy script uses an inline Fully Qualified Class Name (FQCN) like `se.alipsa.groovy.charts.BoxChart.create(...)` and the class can't be resolved at compile time, Groovy's compiler falls back to treating the dotted expression as a property access chain. At runtime, the first identifier (e.g. `se`) is looked up as a variable, producing the confusing error:

```
No such property: se for class: Script2
```

This gives users no indication that the actual problem is a missing or misnamed class. The specific example in `examples/xcharts/src/BoxChart.groovy` used the old package name `se.alipsa.groovy.charts.BoxChart` which was renamed to `se.alipsa.matrix.charts.BoxChart` in `matrix-charts-0.4.0.jar`.

## Solution

Introduced a custom script base class (`GadeScript`) and a package proxy (`PackageProxy`) that intercept the property access chain and produce clear error messages.

### New Files

- **`src/main/java/se/alipsa/gade/runner/GadeScript.java`** - Custom `Script` base class that overrides `getProperty()`. When a `MissingPropertyException` occurs, it checks if the property name corresponds to a known package root on the classpath (by looking for a matching directory entry via `ClassLoader.getResources(name + "/")`). If so, returns a `PackageProxy` to continue resolution. Binding variables always take precedence.

- **`src/main/java/se/alipsa/gade/runner/PackageProxy.java`** - A `GroovyObjectSupport` subclass that progressively builds the FQCN via property access. Each `getProperty()` call attempts `classLoader.loadClass(prefix + "." + name)`. If the class is found, it's returned directly. If not found and the name starts with uppercase (class name convention), an immediate error is thrown. Otherwise, a new `PackageProxy` with the extended prefix is returned.

### Modified Files

- **`src/main/java/se/alipsa/gade/runner/GadeRunnerEngine.java`** - Added `CompilerConfiguration` with `GadeScript` as the script base class, passed to both `mainShell` and `testShell` `GroovyShell` instances.

- **`examples/xcharts/src/BoxChart.groovy`** - Fixed old package name `se.alipsa.groovy.charts.BoxChart` to `se.alipsa.matrix.charts.BoxChart`.

### Error Messages

With this change, users see clear messages like:

```
Cannot resolve class 'se.alipsa.groovy.charts.BoxChart'. Check spelling and ensure the required jar is on the classpath.
```

Instead of the confusing `No such property: se for class: Script2`.

## Testing

Tests in `src/test/java/se/alipsa/gade/runner/GadeScriptFqcnTest.java`:

- FQCN resolves known JDK class (`java.util.Collections.emptyList()`)
- FQCN static method call works (`java.util.Collections.singletonList('hello')`)
- Unresolved FQCN produces helpful error containing "Cannot resolve"
- Uppercase unresolved class gives immediate error with the full FQCN in the message
- Binding variables take precedence over package proxy
- Non-package property still throws `MissingPropertyException`
- Short name not on classpath throws `MissingPropertyException`
- Normal script execution is unaffected

## Impact

- Users get actionable error messages when a class can't be found, instead of misleading property access errors.
- Existing scripts are unaffected: binding variables (`io`, `gadeRuntime`, etc.) always take precedence since `super.getProperty()` is called first.
- Only names matching actual package directories on the classpath trigger the proxy -- common variable names are never intercepted.
- Follows the existing `UnsupportedGuiInteraction` pattern in the same package.
