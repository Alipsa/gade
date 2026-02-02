# Gade Runtime Improvements - February 2, 2026

## Overview

This document summarizes three major improvements made to Gade's runtime handling on February 2, 2026:

1. **Gradle Daemon Cache Recovery** - Automatic recovery from Gradle daemon/cache corruption
2. **Groovy Version Precedence** - Project Groovy version now takes precedence over Gade's bundled version
3. **JSR-223 Requirement Fix** - Eliminated unnecessary groovy-jsr223 warnings for Gradle/Maven runtimes

## 1. Gradle Daemon Cache Recovery

### Problem
Opening Gradle projects sometimes failed with:
```
org.gradle.api.internal.classpath.UnknownModuleException:
Cannot locate manifest for module 'gradle-core' in classpath: []
```

### Solution
Added automatic daemon cache recovery to `GradleUtils.java`:
- Detects corruption patterns (missing module manifests, ClassLoaderRegistry failures, etc.)
- Automatically stops Gradle daemons
- Clears corrupted cache files
- Retries the operation

### Impact
- Users no longer need to manually run `gradle --stop` or clear caches
- Transparent recovery with informative logging
- Most failures recover automatically on first retry

### Files Changed
- `GradleUtils.java` (lines 148-1018)
- Added `clearGradleDaemonCache()`, `isDaemonOrCacheCorruption()`, `extractErrorType()`

### Documentation
- [GRADLE_ERROR_RECOVERY.md](GRADLE_ERROR_RECOVERY.md)

---

## 2. Groovy Version Precedence

### Problem
When using Gradle/Maven runtime, the displayed Groovy version was incorrect:
- Project specified: Groovy 5.0.4
- Gade displayed: Groovy 5.0.1 (Gade's bundled version)

### Root Cause
`RuntimeClassLoaderFactory` was adding Gade's Groovy **before** project dependencies. Since classloaders search URLs in FIFO order, Gade's version was always found first.

### Solution
Changed dependency loading order:
1. Add project dependencies **first** (including project's Groovy)
2. Check if Groovy is already available
3. Only add Gade's Groovy as **fallback** if missing

### Impact
- Gradle/Maven runtimes now use the correct project Groovy version ✓
- Displayed version matches build.gradle/pom.xml ✓
- Gade's Groovy only used when project doesn't include it ✓

### Files Changed
- `RuntimeClassLoaderFactory.java` (lines 71-100, 170-188)
- Added `addDefaultGroovyRuntimeIfMissing()`

### Documentation
- [GROOVY_VERSION_PRECEDENCE.md](GROOVY_VERSION_PRECEDENCE.md)

---

## 3. JSR-223 Requirement Fix

### Problem
Switching to Gradle/Maven runtime showed unnecessary warning:
```
Runtime 'Gradle' is missing groovy-jsr223; using bundled engine.
Add groovy-jsr223 to Groovy home or Dependencies to avoid fallback.
```

This confused users, especially for library projects that don't need groovy-jsr223.

### Root Cause
The `ensureGroovyScriptEngine()` check was applied to **all** runtimes, but only **GADE runtime** actually uses groovy-jsr223:
- **GADE runtime**: Runs scripts in-process using GroovyEngine → needs groovy-jsr223
- **Gradle/Maven**: Run scripts in separate process via RuntimeProcessRunner → don't need groovy-jsr223

### Solution
Only check for groovy-jsr223 when actually needed:
```java
if (runtime.getType() == RuntimeType.GADE || runtime.getType() == RuntimeType.CUSTOM) {
  ensureGroovyScriptEngine(loader, runtime, console);
}
```

### Bonus: GroovyShellEngine
Created `GroovyShellEngine` as an alternative that doesn't require groovy-jsr223 at all:
- Uses core Groovy classes (`GroovyShell`, `Binding`)
- Fully compatible with `GroovyEngine` interface
- Can replace `GroovyEngineReflection` in future if desired

### Impact
- No more spurious groovy-jsr223 warnings for Gradle/Maven ✓
- Cleaner runtime switching experience ✓
- Optional: Can eliminate groovy-jsr223 dependency entirely using GroovyShellEngine

### Files Changed
- `RuntimeClassLoaderFactory.java` (lines 51-62)
- New: `GroovyShellEngine.java`
- New: `RuntimeJsr223RequirementTest.java`

### Documentation
- [JSR223_REQUIREMENT_FIX.md](JSR223_REQUIREMENT_FIX.md)

---

## Testing

All improvements are fully tested with comprehensive test coverage:

### Test Summary
- Total: 90 tests (all passing ✓)
- New test files:
  - `GradleUtilsToolingApiTest.java` - Tooling API compatibility
  - `GradleUtilsGroovierJunit5Test.java` - Real project testing
  - `GradleUtilsDaemonRecoveryTest.java` - Daemon cache recovery
  - `RuntimeGroovyVersionTest.java` - Groovy version precedence
  - `RuntimeJsr223RequirementTest.java` - JSR-223 requirements

### Test Results
```
BUILD SUCCESSFUL in 13s
90 tests completed, 90 passed, 0 failed, 0 skipped
```

---

## Verification Steps

To verify all improvements work:

1. **Build Gade**:
   ```bash
   ./gradlew build -g ./.gradle-user
   ```

2. **Test with groovier-junit5**:
   - Open groovier-junit5 project in Gade
   - Switch runtime from "Gade" to "Gradle"
   - **Expected results**:
     - No "Cannot locate manifest" errors (daemon recovery works)
     - Displays "Groovy 5.0.3" or whatever version is in build.gradle (version precedence works)
     - No groovy-jsr223 warning (JSR-223 fix works)

3. **Test daemon recovery**:
   - If you ever see the "Cannot locate manifest" error:
   - Check logs - should see: "Detected Gradle daemon or cache corruption, attempting cleanup"
   - Should auto-recover without user intervention

---

## Benefits Summary

| Improvement | Before | After |
|-------------|--------|-------|
| **Gradle errors** | Manual cache clearing required | Automatic recovery ✓ |
| **Groovy version** | Shows Gade's version (wrong) | Shows project version (correct) ✓ |
| **JSR-223 warnings** | Shown for all runtimes (confusing) | Only shown when needed (clear) ✓ |
| **User experience** | Requires troubleshooting | Works transparently ✓ |

---

## Files Modified

### Core Changes
- `src/main/java/se/alipsa/gade/utils/gradle/GradleUtils.java`
- `src/main/java/se/alipsa/gade/runtime/RuntimeClassLoaderFactory.java`

### New Files
- `src/main/java/se/alipsa/gade/console/GroovyShellEngine.java`
- `src/test/java/se/alipsa/gade/utils/gradle/GradleUtilsToolingApiTest.java`
- `src/test/java/se/alipsa/gade/utils/gradle/GradleUtilsGroovierJunit5Test.java`
- `src/test/java/se/alipsa/gade/utils/gradle/GradleUtilsDaemonRecoveryTest.java`
- `src/test/java/se/alipsa/gade/runtime/RuntimeGroovyVersionTest.java`
- `src/test/java/se/alipsa/gade/runtime/RuntimeJsr223RequirementTest.java`

### Documentation
- `GRADLE_ERROR_RECOVERY.md`
- `GROOVY_VERSION_PRECEDENCE.md`
- `JSR223_REQUIREMENT_FIX.md`
- `IMPROVEMENTS_SUMMARY.md` (this file)

---

## Conclusion

These improvements significantly enhance Gade's reliability and user experience when working with Gradle and Maven projects:

✓ **More robust** - Automatic recovery from common failures
✓ **More accurate** - Correct version detection and display
✓ **Less confusing** - Warnings only when relevant
✓ **Better tested** - Comprehensive test coverage
✓ **Well documented** - Clear explanations of changes

All changes are backward compatible and thoroughly tested.
