package se.alipsa.gade.runner;

import groovy.lang.Binding;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

/**
 * Custom script base class that improves error messages for unresolved FQCNs.
 * <p>
 * When a Groovy script uses an inline FQCN like {@code se.alipsa.matrix.charts.BoxChart.create(...)},
 * and the class can't be resolved at compile time, Groovy treats the dotted expression as a
 * property access chain. Without this base class, the first identifier (e.g. {@code se}) would
 * produce a confusing {@code MissingPropertyException: No such property: se for class: Script2}.
 * <p>
 * This class intercepts the {@code MissingPropertyException} and checks if the property name
 * corresponds to a known package root on the classpath. If so, it returns a {@link PackageProxy}
 * that progressively builds the FQCN and produces a clear error message if the class can't be found.
 * <p>
 * Binding variables always take precedence since {@code super.getProperty()} is called first.
 */
public abstract class GadeScript extends Script {

  protected GadeScript() {
    super();
  }

  protected GadeScript(Binding binding) {
    super(binding);
  }

  @Override
  public Object getProperty(String property) {
    try {
      return super.getProperty(property);
    } catch (MissingPropertyException e) {
      if (isKnownPackageRoot(property)) {
        return new PackageProxy(property, getClass().getClassLoader());
      }
      throw e;
    }
  }

  private boolean isKnownPackageRoot(String name) {
    if (name == null || name.isEmpty() || !Character.isLowerCase(name.charAt(0))) {
      return false;
    }
    ClassLoader cl = getClass().getClassLoader();
    try {
      Enumeration<URL> resources = cl.getResources(name + "/");
      return resources.hasMoreElements();
    } catch (IOException e) {
      return false;
    }
  }
}
