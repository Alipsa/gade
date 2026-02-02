# Groovy Version Precedence Fix

## Problem

When switching to Gradle or Maven runtime in Gade, the displayed Groovy version was incorrect:
- Project build.gradle specified: Groovy 5.0.4
- Gade displayed: Groovy 5.0.1 (or whatever version Gade was bundled with)

## Root Cause

In `RuntimeClassLoaderFactory`, when creating Gradle/Maven runtime classloaders, Gade was adding its bundled Groovy **before** the project's dependencies:

```java
// OLD CODE - WRONG ORDER
GroovyClassLoader loader = new GroovyClassLoader(...);
addDefaultGroovyRuntime(loader);  // Adds Gade's Groovy FIRST
gradleUtils.addGradleDependencies(loader, ...);  // Adds project deps AFTER
```

Since classloaders search URLs in the order they're added (FIFO), Gade's Groovy was always found first, even though the project specified a different version.

## Solution

Changed the order to add project dependencies **first**, then add Gade's Groovy only as a fallback if the project doesn't have Groovy:

```java
// NEW CODE - CORRECT ORDER
GroovyClassLoader loader = new GroovyClassLoader(...);
gradleUtils.addGradleDependencies(loader, ...);  // Add project deps FIRST
addDefaultGroovyRuntimeIfMissing(loader);  // Add Gade's Groovy only if missing
```

The new `addDefaultGroovyRuntimeIfMissing()` method checks if `groovy.lang.GroovySystem` is already available before adding Gade's version.

## Changes Made

### Modified Files

1. **RuntimeClassLoaderFactory.java** (`src/main/java/se/alipsa/gade/runtime/RuntimeClassLoaderFactory.java`)
   - `createGradleClassLoader()`: Moved `addDefaultGroovyRuntime()` to after project deps
   - `createMavenClassLoader()`: Same change for Maven runtimes
   - Added `addDefaultGroovyRuntimeIfMissing()`: Checks if Groovy exists before adding fallback

### New Tests

1. **RuntimeGroovyVersionTest.java** (`src/test/java/se/alipsa/gade/runtime/RuntimeGroovyVersionTest.java`)
   - Verifies project dependencies include correct Groovy version
   - Tests classloader search order behavior
   - Validates build.gradle Groovy version specification

## Behavior

### Before Fix
1. Gade creates classloader
2. Adds Gade's Groovy 5.0.1
3. Adds project's Groovy 5.0.4
4. **Result**: Classloader finds 5.0.1 (Gade's version)

### After Fix
1. Gade creates classloader
2. Adds project's Groovy 5.0.4
3. Checks if Groovy exists (yes, 5.0.4 is there)
4. Skips adding Gade's Groovy
5. **Result**: Classloader finds 5.0.4 (project's version) ✅

### Edge Case (Project without Groovy)
1. Gade creates classloader
2. Tries to add project dependencies (no Groovy found)
3. Checks if Groovy exists (no)
4. Adds Gade's Groovy as fallback
5. **Result**: Classloader finds Gade's Groovy ✅

## Testing

Run the Groovy version test:
```bash
./gradlew test --tests "se.alipsa.gade.runtime.RuntimeGroovyVersionTest" -g ./.gradle-user
```

Expected output:
```
Project dependencies include Groovy:
  - groovy-ant-5.0.4.jar
  - groovy-groovydoc-5.0.4.jar
  - groovy-5.0.4.jar
```

## Impact

- **Gradle runtime**: Now uses project's Groovy version
- **Maven runtime**: Now uses project's Groovy version
- **Gade runtime**: Unchanged (always uses Gade's Groovy)
- **Custom runtime**: Unchanged (uses configured Groovy home or Gade's Groovy)

## Verification

To verify the fix works:

1. Rebuild Gade: `./gradlew build -g ./.gradle-user`
2. Run Gade and open groovier-junit5 project
3. Switch runtime from "Gade" to "Gradle"
4. Check the Groovy version banner - should show **5.0.4** (matching build.gradle)

The console should display:
```
****************
* Groovy 5.0.4 *
****************
```

Instead of the old behavior:
```
****************
* Groovy 5.0.1 *
****************
```
