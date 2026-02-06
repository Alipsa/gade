## Gade Runtime Architecture Specification

### 1. Process Hierarchy & Communication

* **Isolation:** Every Groovy execution must occur within a dedicated **Runtime Process**.
* **Communication:** Each Runtime Process is initialized with a **Gade Socket Jar** on the boot classpath.
* **Dependency Constraint:** The Gade Socket Jar must have **zero third-party dependencies** (JDK only) to ensure absolute version neutrality.
* **The Process Classloader:** This is the root of the hierarchy (e.g., `Groovy RootLoader`). It contains the JDK and the Gade Socket Jar. It has no parent classloader, acting as the effective System Classloader for the runtime.

### 2. Classloading Strategy

Gade utilizes a **Hierarchical Sacrificial Classloader** pattern. This allows the UI to "reset" the script environment by discarding the child loader without terminating the process.

* **Delegation Mode:**
* **Gade/Custom Runtimes:** Parent-First (Standard Java delegation).
* **Maven/Gradle Runtimes:** Parent-Last (Child-First). The loader checks the project dependencies *before* the Process Classloader to allow version overrides of libraries.


* **Chain of Responsibility:**
1. **Main Loader:** Parent is the Process Classloader. Contains `compile` and `runtime` scope dependencies.
2. **Test Loader:** Parent is the Main Loader. Contains `test` scope dependencies.



---

### 3. Runtime Definitions

| Runtime Type | Classpath Construction                                      | Dependency Injection |
| --- |-------------------------------------------------------------| --- |
| **Gade** | Fixed to Gade's internal Groovy/Java versions.              | Dynamic via `@Grab`. |
| **Maven** | Resolved via `pom.xml` using `MavenClasspathUtils`.         | `addURL()` to Main/Test loaders. |
| **Gradle** | Resolved via `build.gradle` using `GradleUtil`.             | `addURL()` to Main/Test loaders. |
| **Custom** | User-defined Java Home, Groovy Home, Jars and dependencies. | Parent Classloader + `@Grab`. |

---

### 4. Advanced Configuration Support

* **RootLoader Compatibility:** The Process Classloader must support `addURL` to allow for `@GrabConfig(systemClassLoader=true)`.
* **Dynamic Reset:** The IDE must be able to nullify and recreate the `GroovyClassLoader` instances (Main/Test) to refresh the environment without restarting the JVM process.
* **JDK Selection:** For Maven, Gradle, and Custom runtimes, the `java.home` can be explicitly defined; otherwise, it defaults to the Gade Application JDK.

---

### 5. Execution Flow

1. **Initialization:** The Runtime Process starts; the **Process Classloader** loads the Socket Jar and establishes a connection to the Gade GUI.
2. **Dependency Resolution:** Based on the Runtime Type, the backend identifies the required URLs (Jars/Folders).
3. **Loader Chaining:** The `Main Loader` is instantiated, followed by the `Test Loader`.
4. **Injection:** Dependencies are injected into the respective loaders using `addURL`.
5. **Execution:** The Groovy script is compiled and executed within the context of the specific loader (Main or Test) depending on whether the file to execute is a main or a test file (we know the location from the build file).