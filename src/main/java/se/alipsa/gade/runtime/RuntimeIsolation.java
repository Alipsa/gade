package se.alipsa.gade.runtime;

import java.util.concurrent.Callable;

/**
 * Utility to execute code with runtime-specific isolation for classloaders and @Grab system classloader handling.
 */
public final class RuntimeIsolation {

  private RuntimeIsolation() {}

  public static <T> T run(ClassLoader classLoader, RuntimeType runtimeType, Callable<T> action) throws Exception {
    if (classLoader == null) {
      throw new IllegalArgumentException("classLoader must not be null");
    }
    String property = "groovy.grape.enable.system.classloader";
    boolean toggleSystemLoader = runtimeType != null && !RuntimeType.GADE.equals(runtimeType);
    String previousProp = System.getProperty(property);

    ClassLoader original = Thread.currentThread().getContextClassLoader();
    if (toggleSystemLoader) {
      System.setProperty(property, "false");
    }
    Thread.currentThread().setContextClassLoader(classLoader);
    try {
      return action.call();
    } finally {
      Thread.currentThread().setContextClassLoader(original);
      if (toggleSystemLoader) {
        if (previousProp == null) {
          System.clearProperty(property);
        } else {
          System.setProperty(property, previousProp);
        }
      }
    }
  }
}
