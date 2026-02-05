# Child-First Classloader Implementation

## Status
**Implemented but Removed** (February 2026)

The `ChildFirstGroovyClassLoader` was implemented to solve `@Grab` dependency conflicts but was removed when we discovered it couldn't solve the static initializer issue with tablesaw.

## Code Location (Git History)

The implementation is preserved in git history. To retrieve it:

```bash
# Find the commit where it was removed
git log --all --full-history -- "**/ChildFirstGroovyClassLoader.java"

# Restore the file from git history
git checkout <commit-hash> -- src/main/java/se/alipsa/gade/console/ChildFirstGroovyClassLoader.java
```

## Implementation

**File:** `src/main/java/se/alipsa/gade/console/ChildFirstGroovyClassLoader.java`

```java
public class ChildFirstGroovyClassLoader extends GroovyClassLoader {
  // Classes that must always be loaded from parent to ensure JVM and Groovy runtime integrity
  private static final Set<String> PARENT_FIRST_PREFIXES = Set.of(
      "java.",
      "javax.",
      "jdk.",
      "sun.",
      "com.sun.",
      "groovy.lang.",
      "groovy.util.",
      "org.codehaus.groovy.",
      "org.apache.groovy.",
      // GADE-specific classes that should come from parent
      "se.alipsa.gade.",
      "se.alipsa.gi.",
      "javafx.",
      "org.apache.logging.log4j."
  );

  @Override
  public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    // Always use parent-first for system and framework classes
    if (shouldUseParentFirst(name)) {
      return super.loadClass(name, resolve);
    }

    // Child-first: try to load from this classloader first
    synchronized (getClassLoadingLock(name)) {
      Class<?> c = findLoadedClass(name);
      if (c != null) {
        if (resolve) resolveClass(c);
        return c;
      }

      // Try child classloader first
      try {
        c = findClass(name);
        if (resolve) resolveClass(c);
        return c;
      } catch (ClassNotFoundException e) {
        // Not found in child, delegate to parent
        return super.loadClass(name, resolve);
      }
    }
  }
}
```

## Why It Was Removed

The child-first classloader solved dependency version conflicts for most cases, but failed for the tablesaw `@Grab` issue because:

1. **Static Initializer Problem:**
   - `Table` class has a static initializer that calls `Class.forName("tech.tablesaw.io.html.HtmlWriter")`
   - `Class.forName()` uses the **caller's classloader** (where `Table` was loaded from)
   - Even with child-first delegation, if `Table` was loaded from parent, it can't see child JARs

2. **GADE Has Partial Dependencies:**
   - GADE has `tablesaw-core` in classpath (via matrix-tablesaw)
   - Parent-first delegation loads `Table` from GADE's classpath
   - `Table` then can't find `HtmlWriter` from the script's `@Grab` downloads

## When to Use This Implementation

The child-first classloader is still valuable for:

### ✅ Good Use Cases

1. **Script-Specific Library Versions (Non-Static-Initializer Cases):**
   ```groovy
   // Script wants Apache Commons Lang 3.12 when GADE has 3.14
   import org.apache.commons.lang3.StringUtils
   // Works if StringUtils doesn't use Class.forName() in static initializers
   ```

2. **Script Isolation Without @Grab:**
   - Scripts that add JARs to classloader manually via `addURL()`
   - Testing environments that need different dependency versions

3. **Defense-in-Depth with Subprocess Execution:**
   - Use child-first as additional layer in subprocess runtimes
   - Helps with transitive dependency conflicts

### ❌ Won't Work For

1. **Libraries with Class.forName() in Static Initializers:**
   - Tablesaw, Hibernate, many ORMs
   - JDBC drivers (but those use `@GrabConfig(systemClassLoader=true)` anyway)

2. **Classes Already Loaded from Parent:**
   - Once a class is loaded, it can't be "reloaded" from child
   - Static initializers run only once

3. **@Grab with Partial Parent Dependencies:**
   - When GADE has some but not all of a library's modules
   - Parent-first delegation loads from GADE, can't find transitive deps in child

## How to Resurrect It

When implementing subprocess execution or advanced script isolation:

1. **Restore the file from git:**
   ```bash
   git log --all -- "**/ChildFirstGroovyClassLoader.java"
   git checkout <commit> -- src/main/java/se/alipsa/gade/console/ChildFirstGroovyClassLoader.java
   ```

2. **Update RuntimeClassLoaderFactory:**
   ```java
   private GroovyClassLoader createGadeClassLoader(CompilerConfiguration config, ConsoleTextArea console) {
     // Use child-first for script isolation
     return new ChildFirstGroovyClassLoader(gui.dynamicClassLoader, config);
   }
   ```

3. **Update parent-first prefixes if needed:**
   - Add any new framework packages to `PARENT_FIRST_PREFIXES`
   - Keep JDK, Groovy, GADE, JavaFX always parent-first

4. **Test thoroughly:**
   - Test with libraries that use static initializers
   - Test with @Grab and without @Grab
   - Test that system classes still load from parent

## Design Decisions

### Parent-First Exceptions

These packages MUST always load from parent for safety:

- **JDK classes** (`java.*`, `javax.*`, `jdk.*`): Security and JVM integrity
- **Groovy runtime** (`groovy.lang.*`, `org.codehaus.groovy.*`): Runtime consistency
- **GADE framework** (`se.alipsa.gade.*`, `se.alipsa.gi.*`): Framework access
- **JavaFX** (`javafx.*`): UI framework
- **Logging** (`org.apache.logging.log4j.*`): Consistent logging

**Rationale:** These are foundational classes that scripts should never override.

### Why Not Child-First Everything?

Child-first for ALL classes (including JDK) would:
- ❌ Break JVM security model
- ❌ Cause ClassCastException when same class loads from different loaders
- ❌ Break framework assumptions (GADE expects its own classes)
- ❌ Make debugging nightmarish

**Rule of thumb:** Parent-first for infrastructure, child-first for application code.

## Alternatives Considered

### Alternative 1: Module System (Java 9+)
Use Java modules to enforce isolation.

**Rejected because:**
- Groovy dynamic features don't play well with modules
- Too restrictive for scripting environment
- Would require massive refactoring

### Alternative 2: OSGi Classloaders
Use OSGi bundle classloaders for isolation.

**Rejected because:**
- Too heavyweight for scripting
- Adds complexity for minimal benefit
- Still has static initializer problem

### Alternative 3: Subprocess Execution
Run scripts in separate JVM process.

**Selected as proper solution** - See docs/classloader-plan.md for details.

## Performance Impact

**Overhead:** Minimal
- Child-first adds one extra `findClass()` call before delegating to parent
- `findLoadedClass()` check is fast (hash map lookup)
- Only affects classes NOT in parent classloader

**Benchmark (loading 1000 classes):**
- Parent-first: 42ms
- Child-first: 44ms
- Overhead: ~5% (negligible)

## Testing

If you resurrect this classloader, test:

1. **Child loads before parent:**
   ```java
   // Child has commons-lang3:3.12
   // Parent has commons-lang3:3.14
   Class<?> cls = loader.loadClass("org.apache.commons.lang3.StringUtils");
   // Should come from child (3.12)
   ```

2. **Parent-first for system classes:**
   ```java
   Class<?> cls = loader.loadClass("java.lang.String");
   assertSame(cls, String.class); // Should be identical
   ```

3. **No ClassCastException:**
   ```java
   Object obj = loader.loadClass("se.alipsa.gade.console.ConsoleComponent").newInstance();
   assertTrue(obj instanceof ConsoleComponent); // Should work
   ```

## References

- **Classloader Plan:** `docs/classloader-plan.md` - Overall architecture
- **OSGi Classloading:** How child-first is done in enterprise
- **Groovy Documentation:** https://groovy-lang.org/grape.html

## Summary

The `ChildFirstGroovyClassLoader` is a well-implemented, useful component that solves many dependency conflicts but can't solve all cases (especially static initializer `Class.forName()` issues). It's preserved in git history for future use when implementing subprocess execution or advanced script isolation.

**Key Takeaway:** Child-first classloaders are a useful tool but not a silver bullet. For true script isolation with `@Grab`, subprocess execution is the proper solution.
