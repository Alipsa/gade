# Gradle Tooling API with Java 21

## Issue

When running Gade with Java 21, the Gradle Tooling API fails to initialize with:

```
Cannot locate manifest for module 'gradle-runtime-api-info' in classpath: [].
```

This occurs because Java 21's module system restricts access to internal JDK modules that the Gradle Tooling API needs.

## Solution

Add the following JVM arguments when running Gade:

```
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.net=ALL-UNNAMED
```

## How to Apply

### Running from Gradle (Development)

```bash
./gradlew run -g ./.gradle-user
```

The `build.gradle` file is already configured with these JVM args in `applicationDefaultJvmArgs`.

### Running from Packaged Distribution

The packaged runtime distributions (created with `./gradlew runtimeZip`) automatically include these JVM args in the `gade` (Linux/Mac) and `gade.bat` (Windows) launcher scripts. No additional configuration needed!

### Running from IDE (IntelliJ IDEA, etc.)

1. Edit your Run Configuration
2. Add to **VM options**:
   ```
   --add-opens=java.base/java.lang=ALL-UNNAMED
   --add-opens=java.base/java.util=ALL-UNNAMED
   --add-opens=java.base/java.io=ALL-UNNAMED
   --add-opens=java.base/java.net=ALL-UNNAMED
   --enable-native-access=javafx.graphics,javafx.media,javafx.web,ALL-UNNAMED
   ```

### Running from JAR

```bash
java --add-opens=java.base/java.lang=ALL-UNNAMED \
     --add-opens=java.base/java.util=ALL-UNNAMED \
     --add-opens=java.base/java.io=ALL-UNNAMED \
     --add-opens=java.base/java.net=ALL-UNNAMED \
     --enable-native-access=javafx.graphics,javafx.media,javafx.web,ALL-UNNAMED \
     -jar gade.jar
```

## Why This Works

The Gradle Tooling API uses reflection and dynamic class loading to interact with Gradle distributions. Java 21's module system (JPMS) restricts this access by default. The `--add-opens` flags allow the Tooling API to:

- Access internal JDK classes via reflection
- Load Gradle distribution modules dynamically
- Initialize the Gradle runtime classpath properly

## Verification

After adding these JVM args, you should be able to:

1. Open a Gradle project in Gade
2. Switch to "Gradle" runtime successfully
3. See proper classpath resolution

The error `classpath: []` should be replaced with actual JAR paths from the Gradle distribution.

## Alternative: Use Java 17

If you don't want to manage JVM arguments, you can run Gade with Java 17 (LTS), which has fewer module restrictions:

```bash
export JAVA_HOME=/path/to/java17
./gradlew run -g ./.gradle-user
```

Java 17 still works well with Gradle and requires fewer `--add-opens` flags.
