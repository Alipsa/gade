# Gradle Error Recovery Improvements

## Overview

This document describes the automatic error recovery mechanisms added to `GradleUtils` to handle Gradle daemon and cache corruption issues.

## Problem

When opening Gradle projects in Gade, users occasionally encountered errors like:

```
org.gradle.api.internal.classpath.UnknownModuleException: Cannot locate manifest for module 'gradle-core' in classpath: []
```

This error indicates that the Gradle Tooling API could not properly initialize the Gradle distribution, typically due to:
- Corrupted Gradle daemon state
- Corrupted distribution cache
- Stale daemon processes with incompatible state

## Solution

The enhanced `GradleUtils.withConnection()` method now automatically detects and recovers from these errors through a multi-layered approach:

### 1. Error Detection

The system detects daemon/cache corruption by checking for these error patterns:
- "Cannot locate manifest for module" - Missing Gradle module manifests
- "Could not create service of type ClassLoaderRegistry" - Service creation failures
- "Could not create an instance of Tooling API implementation" - Tooling API initialization failures
- `UnknownModuleException` - Module not found errors

### 2. Automatic Recovery Steps

When corruption is detected, the system automatically:

1. **Stops all Gradle daemons** using `gradle --stop`
2. **Clears daemon cache** by removing files from `~/.gradle/daemon/`
3. **Clears corrupted script caches** in version-specific cache directories
4. **Retries the operation** with a fresh connection

### 3. Fallback Chain

If daemon cache clearing doesn't resolve the issue, the system falls through these fallbacks:

1. Try with wrapper distribution (if available)
2. Try with embedded/tooling API Gradle version
3. Fall back to Gade runtime (as before)

## Implementation Details

### New Methods in GradleUtils

- `isDaemonOrCacheCorruption(Throwable)` - Detects if an error is due to daemon/cache corruption
- `extractErrorType(Throwable)` - Extracts a human-readable error type for logging
- `clearGradleDaemonCache()` - Stops daemons and clears corrupted cache files
- `findGradleCommand()` - Locates gradle/gradlew executable for stopping daemons

### Modified Methods

- `withConnection()` - Enhanced with daemon cache recovery logic before other fallbacks

## Benefits

1. **Automatic Recovery** - Users no longer need to manually stop daemons or clear caches
2. **Better Logging** - Specific error types are logged to help diagnose issues
3. **Non-Destructive** - Only clears daemon cache, not the full distribution cache
4. **Transparent** - Recovery happens automatically without user intervention

## Testing

Comprehensive tests verify:
- Error detection works for all known corruption patterns
- Gradle command discovery works (gradlew, gradle in PATH)
- Cache directory structure is correctly identified
- Error type extraction provides useful diagnostics

## User Impact

When encountering the "Cannot locate manifest" error:
- **Before**: Project would fail to open, requiring manual intervention
- **After**: Gade automatically clears the daemon cache and retries, usually succeeding on the second attempt

Users will see log messages like:
```
WARN: Detected Gradle daemon or cache corruption (module manifest not found), attempting cleanup and retry
INFO: Clearing Gradle daemon cache at ~/.gradle
INFO: Successfully stopped Gradle daemons
INFO: Cleared Gradle daemon cache at ~/.gradle/daemon
```

## Future Improvements

Potential enhancements:
- Add a UI indicator when recovery is in progress
- Provide a manual "Clear Gradle Cache" menu option
- Add metrics to track how often recovery is needed
- Consider clearing daemon cache proactively on Gade startup if it detects stale state
