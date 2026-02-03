package se.alipsa.gade.code.completion;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamically scans classpath to discover available classes.
 * Supports scanning from any ClassLoader (including DynamicClassLoader
 * and GroovyClassLoader) to include @Grab, Maven, and Gradle dependencies.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe for concurrent scanning operations.
 * The singleton instance uses a {@code volatile} field for safe publication across threads.
 * ClassLoader-based caching uses {@link ConcurrentHashMap} keyed by classloader identity hash,
 * allowing multiple threads to safely scan different classloaders or reuse cached results
 * concurrently without synchronization overhead.
 *
 * @see ConcurrentHashMap
 * <p><b>Thread-Safety:</b> This class is thread-safe.</p>
 */
public final class ClasspathScanner {

  private static final Logger LOG = LogManager.getLogger(ClasspathScanner.class);

  // Default packages to always scan
  private static final String[] CORE_PACKAGES = {
      // Java core
      "java.lang", "java.util", "java.io", "java.net", "java.time",
      "java.math", "java.text", "java.nio", "java.sql", "java.security",
      "java.awt", "java.beans", "java.concurrent",
      // Groovy core
      "groovy.lang", "groovy.util", "groovy.json", "groovy.xml",
      "groovy.transform", "groovy.sql", "groovy.io", "groovy.grape",
      "groovy.cli", "groovy.console", "groovy.time", "groovy.yaml",
      "groovy.text", "groovy.ant", "groovy.servlet", "groovy.swing",
      "groovy.test", "groovy.mock", "groovy.jmx", "groovy.nio",
      // Apache Groovy runtime
      "org.codehaus.groovy", "org.apache.groovy"
  };

  // Package preference order (lower index = higher priority)
  private static final List<String> PACKAGE_PRIORITY = List.of(
      "java.lang", "java.util", "java.time", "java.math", "java.io",
      "groovy.lang", "groovy.util"
  );

  // Cache keyed by classloader identity
  private final Map<Integer, CachedIndex> cache = new ConcurrentHashMap<>();

  // Singleton instance
  private static volatile ClasspathScanner instance;

  private ClasspathScanner() {}

  /**
   * Returns the singleton instance.
   */
  public static ClasspathScanner getInstance() {
    if (instance == null) {
      synchronized (ClasspathScanner.class) {
        if (instance == null) {
          instance = new ClasspathScanner();
        }
      }
    }
    return instance;
  }

  /**
   * Scans all classes available on the given classloader.
   * Results are cached by classloader identity.
   *
   * @param classLoader the classloader to scan
   * @return map of simple class name to list of fully qualified names
   */
  public Map<String, List<String>> scan(ClassLoader classLoader) {
    if (classLoader == null) {
      classLoader = ClasspathScanner.class.getClassLoader();
    }

    int key = System.identityHashCode(classLoader);
    CachedIndex cached = cache.get(key);

    if (cached != null && !cached.isStale(classLoader)) {
      return cached.index;
    }

    Map<String, List<String>> index = doScan(classLoader, CORE_PACKAGES);
    cache.put(key, new CachedIndex(index, classLoader));

    return index;
  }

  /**
   * Scans classes in specific packages only.
   *
   * @param classLoader the classloader to use
   * @param packages    packages to scan
   * @return map of simple class name to list of fully qualified names
   */
  public Map<String, List<String>> scanPackages(ClassLoader classLoader, String... packages) {
    if (classLoader == null) {
      classLoader = ClasspathScanner.class.getClassLoader();
    }
    return doScan(classLoader, packages);
  }

  /**
   * Scans additional jars and merges into the cached index.
   *
   * @param classLoader the classloader context
   * @param jars        jar files to scan
   */
  public void addJars(ClassLoader classLoader, Collection<File> jars) {
    if (jars == null || jars.isEmpty()) return;

    List<String> paths = new ArrayList<>();
    for (File jar : jars) {
      if (jar.exists() && jar.getName().endsWith(".jar")) {
        paths.add(jar.getAbsolutePath());
      }
    }

    if (paths.isEmpty()) return;

    try (ScanResult sr = new ClassGraph()
        .overrideClasspath(paths)
        .enableClassInfo()
        .scan()) {

      int key = System.identityHashCode(classLoader);
      CachedIndex cached = cache.get(key);
      Map<String, List<String>> index = cached != null
          ? new HashMap<>(cached.index)
          : new HashMap<>();

      for (ClassInfo ci : sr.getAllClasses()) {
        String simple = ci.getSimpleName();
        if (simple == null || simple.isEmpty()) continue;
        index.computeIfAbsent(simple, k -> new ArrayList<>()).add(ci.getName());
      }

      // Re-sort all lists
      Comparator<String> byPriority = packagePriorityComparator();
      for (List<String> list : index.values()) {
        list.sort(byPriority);
      }

      cache.put(key, new CachedIndex(index, classLoader));
    } catch (Exception e) {
      LOG.warn("Failed to scan additional jars", e);
    }
  }

  /**
   * Resolves a simple class name to possible fully qualified names.
   *
   * @param simpleName the simple class name (e.g., "BigDecimal")
   * @param classLoader the classloader context
   * @return list of FQCNs, ordered by preference
   */
  public List<String> resolve(String simpleName, ClassLoader classLoader) {
    Map<String, List<String>> index = scan(classLoader);
    return index.getOrDefault(simpleName, Collections.emptyList());
  }

  /**
   * Invalidates the cache for a specific classloader.
   */
  public void invalidate(ClassLoader classLoader) {
    if (classLoader != null) {
      cache.remove(System.identityHashCode(classLoader));
    }
  }

  /**
   * Invalidates all cached indexes.
   */
  public void invalidateAll() {
    cache.clear();
  }

  private Map<String, List<String>> doScan(ClassLoader classLoader, String[] packages) {
    Map<String, List<String>> index = new HashMap<>();
    long start = System.currentTimeMillis();

    try {
      ClassGraph cg = new ClassGraph()
          .enableClassInfo()
          .enableSystemJarsAndModules();

      if (packages != null && packages.length > 0) {
        cg.acceptPackages(packages);
      }

      // Add URLs from URLClassLoader hierarchy
      addClassLoaderUrls(cg, classLoader);

      try (ScanResult sr = cg.scan()) {
        for (ClassInfo ci : sr.getAllClasses()) {
          String simple = ci.getSimpleName();
          if (simple == null || simple.isEmpty()) continue;
          // Skip inner classes for basic completion
          if (ci.getName().contains("$")) continue;

          index.computeIfAbsent(simple, k -> new ArrayList<>()).add(ci.getName());
        }
      }

      // Sort lists by package priority
      Comparator<String> byPriority = packagePriorityComparator();
      for (List<String> list : index.values()) {
        list.sort(byPriority);
      }

      long elapsed = System.currentTimeMillis() - start;
      LOG.debug("Classpath scan completed: {} classes in {} ms", index.size(), elapsed);

    } catch (Exception e) {
      LOG.warn("Classpath scan failed", e);
    }

    return index;
  }

  private void addClassLoaderUrls(ClassGraph cg, ClassLoader cl) {
    Set<URL> urls = new LinkedHashSet<>();
    collectUrls(cl, urls);
    if (!urls.isEmpty()) {
      List<String> paths = new ArrayList<>();
      for (URL url : urls) {
        try {
          paths.add(new File(url.toURI()).getAbsolutePath());
        } catch (Exception ignore) {
          // Skip malformed URLs
        }
      }
      if (!paths.isEmpty()) {
        cg.overrideClasspath(paths);
      }
    }
  }

  private void collectUrls(ClassLoader cl, Set<URL> urls) {
    if (cl == null) return;
    if (cl instanceof URLClassLoader ucl) {
      urls.addAll(Arrays.asList(ucl.getURLs()));
    }
    collectUrls(cl.getParent(), urls);
  }

  private Comparator<String> packagePriorityComparator() {
    return Comparator.comparingInt(fqn -> {
      for (int i = 0; i < PACKAGE_PRIORITY.size(); i++) {
        if (fqn.startsWith(PACKAGE_PRIORITY.get(i) + ".")) {
          return i;
        }
      }
      return PACKAGE_PRIORITY.size();
    });
  }

  /**
   * Cached index with staleness tracking.
   */
  private static final class CachedIndex {
    final Map<String, List<String>> index;
    final int urlCount;

    CachedIndex(Map<String, List<String>> index, ClassLoader cl) {
      this.index = Collections.unmodifiableMap(index);
      this.urlCount = countUrls(cl);
    }

    boolean isStale(ClassLoader cl) {
      // Check if classloader has new URLs (indicating @Grab or dependency reload)
      return countUrls(cl) != urlCount;
    }

    private static int countUrls(ClassLoader cl) {
      int count = 0;
      while (cl != null) {
        if (cl instanceof URLClassLoader ucl) {
          count += ucl.getURLs().length;
        }
        cl = cl.getParent();
      }
      return count;
    }
  }
}
