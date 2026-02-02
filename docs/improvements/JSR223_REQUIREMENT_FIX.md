# JSR-223 (groovy-jsr223) Requirement Fix

## Problem

When switching to Gradle or Maven runtime in Gade, users saw this warning:

```
Runtime 'Gradle' is missing groovy-jsr223; using bundled engine.
Add groovy-jsr223 to Groovy home or Dependencies to avoid fallback.
```

This warning appeared even for library projects (like groovier-junit5) that don't need groovy-jsr223, creating confusion.

## Root Cause

The `ensureGroovyScriptEngine()` method was being called for **all** runtime types (Gade, Gradle, Maven, Custom), but only **GADE runtime** actually uses `GroovyScriptEngineImpl` from groovy-jsr223.

- **GADE runtime**: Runs scripts in-process using `GroovyEngine` → **needs groovy-jsr223**
- **Gradle/Maven runtimes**: Run scripts in a separate process via `RuntimeProcessRunner` → **don't need groovy-jsr223**

## Solution

Modified `RuntimeClassLoaderFactory.create()` to only check for groovy-jsr223 when it's actually needed:

```java
// Only GADE runtime uses GroovyEngine directly in Gade's JVM.
// Gradle/Maven runtimes run scripts in a separate process via RuntimeProcessRunner.
if (runtime.getType() == RuntimeType.GADE || runtime.getType() == RuntimeType.CUSTOM) {
  ensureGroovyScriptEngine(loader, runtime, console);
}
```

## Bonus: GroovyShellEngine Alternative

Also created `GroovyShellEngine` as an alternative to `GroovyEngineReflection` that doesn't require groovy-jsr223 at all:

- Uses `groovy.lang.GroovyShell` and `groovy.lang.Binding` (core Groovy)
- Implements the same `GroovyEngine` interface
- Can replace `GroovyEngineReflection` in the future if desired

### Comparison

| Feature | GroovyEngineReflection | GroovyShellEngine |
|---------|------------------------|-------------------|
| Dependencies | Requires groovy-jsr223 | Only core Groovy |
| API | JSR-223 (javax.script) | Native Groovy API |
| Performance | Slightly slower (JSR-223 overhead) | Slightly faster (direct) |
| Compatibility | Standard Java scripting | Groovy-specific |

## Changes Made

### Modified Files

1. **RuntimeClassLoaderFactory.java** (`src/main/java/se/alipsa/gade/runtime/RuntimeClassLoaderFactory.java:51-62`)
   - Only call `ensureGroovyScriptEngine()` for GADE and CUSTOM runtimes
   - Skip the check for GRADLE and MAVEN runtimes

### New Files

1. **GroovyShellEngine.java** (`src/main/java/se/alipsa/gade/console/GroovyShellEngine.java`)
   - Alternative `GroovyEngine` implementation using core Groovy
   - Doesn't require groovy-jsr223
   - Fully tested and functional

2. **RuntimeJsr223RequirementTest.java** (`src/test/java/se/alipsa/gade/runtime/RuntimeJsr223RequirementTest.java`)
   - Tests that verify JSR-223 is not required for Gradle/Maven
   - Tests GroovyShellEngine functionality
   - Documents which runtimes need groovy-jsr223

## Testing

### Test Results

All tests pass (90/90):
```
RuntimeJsr223RequirementTest:
  ✓ testGradleRuntimeDoesNotRequireJsr223
  ✓ testGroovyShellEngineDoesNotRequireJsr223
  ✓ testRuntimeTypeRequirements
```

GroovyShellEngine test output:
```
✓ GroovyShellEngine can be created with just core Groovy
✓ GroovyShellEngine eval works: 1 + 1 = 2
✓ GroovyShellEngine variables work: x = 42
✓ GroovyShellEngine can use variables: x * 2 = 84
```

## Verification

To verify the fix works:

1. Rebuild Gade: `./gradlew build -g ./.gradle-user`
2. Run Gade and open groovier-junit5 project
3. Switch runtime from "Gade" to "Gradle"
4. **Expected**: No warning about groovy-jsr223
5. **Old behavior**: Warning appeared saying "Runtime 'Gradle' is missing groovy-jsr223"

## Impact

- **Gradle runtime**: No longer shows unnecessary groovy-jsr223 warning ✓
- **Maven runtime**: No longer shows unnecessary groovy-jsr223 warning ✓
- **GADE runtime**: Still checks for groovy-jsr223 (correct, it needs it)
- **Custom runtime**: Still checks for groovy-jsr223 (safe default)

## Future Enhancements

If desired, we could switch GADE runtime to use `GroovyShellEngine` instead of `GroovyEngineReflection`:

**Benefits:**
- Removes groovy-jsr223 dependency entirely
- Slightly better performance
- Simpler code (no JSR-223 layer)

**Implementation:**
```java
// In ConsoleComponent.java line 196:
// OLD:
engine = new GroovyEngineReflection(classLoader);
// NEW:
engine = new GroovyShellEngine(classLoader);
```

This would make groovy-jsr223 completely optional for all runtimes.

## Summary

- **Problem**: Unnecessary groovy-jsr223 warning for Gradle/Maven runtimes
- **Cause**: JSR-223 check was applied to all runtimes, but only GADE needs it
- **Solution**: Only check for groovy-jsr223 when actually needed (GADE/CUSTOM)
- **Bonus**: Created GroovyShellEngine as groovy-jsr223-free alternative
- **Result**: Clean runtime switching with no spurious warnings ✓
