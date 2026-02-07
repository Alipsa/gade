We currently execute Groovy code using the classpath of the IDE but I want to introduce a "runtime" concept where the options would be:
- Gradle (use the classpath defined in the gradle build script) - this is to a great extent supported today though the classpath is not fully isolated from the IDE
- Maven (use the classpath defined in the maven pom.xml) - this is to a great extent supported today though the classpath is not fully isolated from the IDE
- Gade (the current behavior)
- Custom (the user would point to the jvm and the groovy installation + adding additional jars or dependencies using a gradle short version syntax). There could be several custom profiles so a unique name for each custom runtime would be required.

Which runtime to use can be project specific so should be stored in preferences i.e. when i runtime is selected store it as a preference using the directory we are in as the key and the name of the runtime as the value.

There is an existing meny called runtimes where the Gradle, Maven and Gade runtimes are always available and a menu option to create a custom runtime is present. However, none of these menu items have any actions defined in them. If a user has created runtimes before they should be listed after the first 3 runtimes in the menu. The current runtime should be marked with a check symbol so it is clear which runtime that is used when executing code.

I also want the menu option for editing runtimes to be implemented i.e. the action should lead to a dialog where the available runtimes are selectable and the 5 things I could customize available for editing i.e.
1. The name of the runtime
2. The path to the jvm to run
3. The Groovy home to use
4. Additional jar files
5. Dependencies using the gradle short notation i.e. groupId:artifactId:version

This edit runtime dialog should also give the user the possibility to delete or add a new runtime.

If no gradle build script is present in the root dir then the Gradle option should be grayed out and disabled for selection. If no maven build script is present then the Maven option should be grayed out and disabled for selection.

If I have selected a runtime and then removed it manually e.g. I have selected to use the Maven runtime and then removed the maven build file. I should be presented with a dialog explaining that the runtime is not available in this project and prompt the user to select a new one.

The Gradle, Maven and custom runtimes should be completely isolated from the IDE classpath. If the user uses @Grab with @GrabConfig(systemClassLoader=true) then that should not affect the classpath of the IDE. Also, it should be possible to specify a different version of things like the Matrix and Parquet libraries than what is available in the IDE when using the Gradle, Maven or custom runtimes.

• Here’s the implementation plan:

- Map current state: find runtime menu wiring, preference store (project-dir keyed), Groovy execution entry points, and InOut injection path to understand where classpath and session are set
  up.
- Define runtime model: enum/types for Gradle, Maven, Gade, Custom with fields (name, jvm path, groovyHome, jars, gradle deps). Persist per project dir; add availability checks (Gradle/Maven
  disabled when build files missing) and fallback flow when a selected runtime disappears.
- UI behaviors: populate “Runtimes” menu with the three built-ins plus saved customs; show checkmark on active runtime; disable Gradle/Maven when unavailable; add “Edit runtimes” dialog
  enabling select/edit/create/delete with fields for the five properties and validation for names/paths/duplicates; ensure updates refresh menu and preferences.
- Execution isolation: build classpath per runtime (Gradle via tool invocation, Maven via pom, Custom from settings) in an isolated classloader so IDE classpath isn’t polluted; handle @Grab
  with isolated systemClassLoader so grabs don’t leak into IDE; allow overriding library versions (e.g., Matrix/Parquet) per runtime; ensure Gade runtime retains current behavior but uses
  isolated classloader.
- ensure lifecycle management, serialization constraints, and that proxies work with the isolated classloaders.

---

## Groovy Version Selection

The Groovy version used for script execution is determined by the runtime type. This is by design — each runtime type has a different mechanism for specifying the Groovy version, matching how that runtime type manages dependencies in general.

| Runtime Type | Groovy Version Source | How to Change It |
|--------------|----------------------|------------------|
| **Gade** | Fixed to Gade's bundled Groovy/Ivy jars (`lib/groovy/`) | Cannot be changed — the Gade runtime always uses the bundled version. Switch to a Custom, Maven, or Gradle runtime to use a different Groovy version. |
| **Maven** | Implicit — resolved from `pom.xml` project dependencies. If the project's dependencies include a Groovy runtime, that version is used. | Add or change the Groovy dependency version in `pom.xml`. If no Groovy dependency is declared, Gade's bundled version is used as a fallback. |
| **Gradle** | Implicit — resolved from `build.gradle` project dependencies via the Gradle Tooling API. If the project's dependencies include a Groovy runtime, that version is used. | Add or change the Groovy dependency version in `build.gradle`. If no Groovy dependency is declared, Gade's bundled version is used as a fallback. |
| **Custom** | Explicit — loaded from the Groovy Home directory configured in the runtime settings (all jars in `<groovyHome>/lib/`). | Change the Groovy Home path in the Edit Runtimes dialog. If no Groovy Home is configured, Gade's bundled version is used as a fallback. |

**Bundled Groovy Fallback:** For Maven and Gradle runtimes, Gade checks whether the resolved project dependencies include Groovy jars. If they do, those are used (project version takes precedence). If they don't, Gade adds its own bundled Groovy/Ivy jars so that the runtime always has a working Groovy environment. This ensures scripts can always execute, even in projects that don't explicitly declare a Groovy dependency.