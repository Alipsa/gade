package se.alipsa.gade;

import groovy.lang.GroovyClassLoader;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.codehaus.groovy.control.CompilerConfiguration;

/**
 * {@link GroovyClassLoader} implementation that denies access to specified package prefixes.
 * The loader applies child-first lookup semantics (with the exception of core JDK packages)
 * so script specific dependencies can override those exposed by the parent while still
 * preventing scripts from loading application implementation classes.
 */
public final class IsolatedGroovyClassLoader extends GroovyClassLoader {

  private final List<String> blockedPackages;

  /**
   * Create an isolated loader.
   *
   * @param parent the parent class loader.
   * @param blockedPackages the package names that must not be exposed to scripts.
   */
  public IsolatedGroovyClassLoader(ClassLoader parent, Collection<String> blockedPackages) {
    super(parent);
    this.blockedPackages = normalisePackages(blockedPackages);
  }

  /**
   * Create an isolated loader with a specific compiler configuration.
   *
   * @param parent the parent class loader.
   * @param configuration the compiler configuration to apply.
   * @param blockedPackages the package names that must not be exposed to scripts.
   */
  public IsolatedGroovyClassLoader(
      ClassLoader parent, CompilerConfiguration configuration, Collection<String> blockedPackages) {
    super(parent, configuration);
    this.blockedPackages = normalisePackages(blockedPackages);
  }

  private List<String> normalisePackages(Collection<String> packages) {
    if (packages == null || packages.isEmpty()) {
      return List.of();
    }
    return packages.stream()
        .map(pkg -> Objects.requireNonNull(pkg, "blockedPackage"))
        .map(pkg -> pkg.endsWith(".") ? pkg.substring(0, pkg.length() - 1) : pkg)
        .distinct()
        .collect(Collectors.collectingAndThen(Collectors.toList(), List::copyOf));
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    if (isBlocked(name)) {
      throw new ClassNotFoundException("Access to class " + name + " is restricted");
    }

    synchronized (getClassLoadingLock(name)) {
      Class<?> loadedClass = findLoadedClass(name);
      if (loadedClass == null) {
        if (shouldDelegateToParentFirst(name)) {
          loadedClass = loadFromParent(name);
        } else {
          try {
            loadedClass = findClass(name);
          } catch (ClassNotFoundException notFound) {
            loadedClass = loadFromParent(name, notFound);
          }
        }
      }
      if (resolve && loadedClass != null) {
        resolveClass(loadedClass);
      }
      return loadedClass;
    }
  }

  private Class<?> loadFromParent(String name) throws ClassNotFoundException {
    return loadFromParent(name, null);
  }

  private Class<?> loadFromParent(String name, ClassNotFoundException original)
      throws ClassNotFoundException {
    ClassLoader parent = getParent();
    if (parent == null) {
      if (original != null) {
        throw original;
      }
      throw new ClassNotFoundException(name);
    }
    try {
      return parent.loadClass(name);
    } catch (ClassNotFoundException parentMissing) {
      if (original != null) {
        parentMissing.addSuppressed(original);
      }
      throw parentMissing;
    }
  }

  private boolean shouldDelegateToParentFirst(String name) {
    if (name == null) {
      return false;
    }
    return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.");
  }

  private boolean isBlocked(String name) {
    if (name == null || blockedPackages.isEmpty()) {
      return false;
    }
    for (String prefix : blockedPackages) {
      if (name.equals(prefix) || name.startsWith(prefix + ".")) {
        return true;
      }
    }
    return false;
  }
}
