package se.alipsa.gade.runner;

import groovy.lang.GroovyObjectSupport;
import org.codehaus.groovy.runtime.InvokerHelper;

/**
 * Represents an unresolved package path segment during FQCN resolution.
 * <p>
 * When a Groovy script uses an inline FQCN like {@code se.alipsa.matrix.charts.BoxChart.create(...)},
 * and the class can't be resolved at compile time, Groovy treats the dotted expression as a
 * property access chain. This proxy intercepts that chain and progressively builds the FQCN,
 * attempting to resolve it as a class at each step.
 * <p>
 * If the full FQCN can't be resolved, a clear error message is thrown instead of the
 * confusing default {@code No such property: se for class: Script2}.
 */
final class PackageProxy extends GroovyObjectSupport {

  private final String prefix;
  private final ClassLoader classLoader;

  PackageProxy(String prefix, ClassLoader classLoader) {
    this.prefix = prefix;
    this.classLoader = classLoader;
  }

  @Override
  public Object getProperty(String name) {
    String fqcn = prefix + "." + name;
    try {
      return classLoader.loadClass(fqcn);
    } catch (ClassNotFoundException e) {
      if (!name.isEmpty() && Character.isUpperCase(name.charAt(0))) {
        throw new RuntimeException(
            "Cannot resolve class '" + fqcn + "'. Check spelling and ensure the required jar is on the classpath.");
      }
      return new PackageProxy(fqcn, classLoader);
    }
  }

  @Override
  public Object invokeMethod(String name, Object args) {
    try {
      Class<?> cls = classLoader.loadClass(prefix);
      return InvokerHelper.invokeStaticMethod(cls, name, args);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(
          "Cannot resolve '" + prefix + "' as a class. No such class found on the classpath.");
    }
  }

  @Override
  public String toString() {
    return "<unresolved:" + prefix + ">";
  }
}
