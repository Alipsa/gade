# TestFX on macOS - Known Issues and Workarounds

## Issue

TestFX tests crash on macOS with SIGTRAP during JVM shutdown. This is a known issue with Monocle (the headless JavaFX implementation used by TestFX) on macOS.

**Important:** The tests themselves **pass successfully** - the crash occurs during JVM shutdown *after* all tests complete.

## Symptoms

```
Exception Type:    EXC_BREAKPOINT (SIGTRAP)
Exception Codes:   0x0000000000000001, 0x...
Triggered by Thread: XX  Java: JavaFX Application Thread
Termination Reason:  Namespace SIGNAL, Code 5, Trace/BPT trap: 5
```

Build output shows:
```
Results: SUCCESS (286 tests, 286 passed, 0 failed, 0 skipped)
BUILD FAILED in XXs
Process finished with exit code 133 (SIGTRAP)
```

## Default Behavior

**TestFX tests are automatically skipped on macOS** to avoid these crashes. You'll see:
```
Skipping TestFX tests on macOS due to known SIGTRAP crashes.
Run with -DskipTestFx=false to enable.
```

This allows the build to complete successfully with 279 tests (excluding 7 TestFX tests).

## Workarounds

### 1. Skip TestFX Tests (Default on macOS)

```bash
./gradlew test -g ./.gradle-user
```

**Result:** 279 tests run, build succeeds

### 2. Run TestFX Tests Anyway

If you need to verify TestFX tests work:

```bash
./gradlew test -DskipTestFx=false -g ./.gradle-user
```

**Result:** All 286 tests pass, but build reports FAILED due to SIGTRAP crash. **Check the test results - they all passed!**

### 3. Run Only Non-TestFX Tests Explicitly

```bash
./gradlew test -DskipTestFx=true -g ./.gradle-user
```

Works on any platform (not just macOS).

### 4. Run Tests on Linux/CI

TestFX tests run reliably on Linux without crashes. Use CI/CD for final verification.

## Technical Details

### Root Cause

The SIGTRAP crash is caused by Monocle's interaction with macOS's native graphics system during JavaFX shutdown. The issue has been reported in:

- [TestFX Issue #66](https://github.com/javafxports/openjdk-jfx/issues/66) - Java 10 + Monocle = JVM Crash
- [Monocle Issues](https://github.com/TestFX/Monocle/issues)
- Various JavaFX macOS crash reports

### Current Configuration

The build is configured with:
- Headless mode using Monocle
- Glass platform settings for embedded systems
- Special JVM args to minimize crashes
- `prism.order=sw` disabled on macOS (causes more crashes)

### Why Not Fix It?

This is a long-standing issue in Monocle's interaction with macOS. The TestFX/Monocle projects haven't resolved it because:
1. It only affects macOS
2. Tests pass successfully before the crash
3. Most users run headless tests on Linux CI servers
4. The alternative (non-headless testing) requires a display/X server

## Verification

To verify that tests actually pass despite the crash:

1. Run with TestFX enabled: `./gradlew test -DskipTestFx=false -g ./.gradle-user`
2. Look for the test results summary **before** the crash:
   ```
   Results: SUCCESS (286 tests, 286 passed, 0 failed, 0 skipped)
   ```
3. Even though `BUILD FAILED` appears after, all tests passed

## For CI/CD

For continuous integration:
- Use Linux build agents (no crashes)
- Or skip TestFX tests: `./gradlew test -DskipTestFx=true`
- Or parse test results XML to verify success despite exit code

## Future

If Oracle/Gluon fixes the Monocle macOS SIGTRAP issue, this workaround can be removed. Monitor:
- [Gluon JavaFX releases](https://gluonhq.com/products/javafx/)
- [OpenJFX releases](https://openjfx.io/)
- TestFX GitHub issues

## Sources

- [TestFX/Monocle GitHub Issues](https://github.com/TestFX/Monocle/issues)
- [JavaFX macOS Crash Reports](https://bugs.openjdk.org/)
- [Gluon OpenJFX Updates](https://gluonhq.com/securing-the-future-of-openjfx-january-2026-critical-patch-update-released/)
