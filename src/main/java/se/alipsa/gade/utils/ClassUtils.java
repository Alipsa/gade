package se.alipsa.gade.utils;

/**
 * Utility methods for interacting with the JVM class loader hierarchy.
 */
public final class ClassUtils {

  private ClassUtils() {
    // Utility class
  }

  /**
   * Locate the bootstrap (root) class loader for the current JVM instance.
   *
   * @return the top-most parent class loader, or the system class loader if no parent exists.
   */
  public static ClassLoader getBootstrapClassLoader() {
    ClassLoader bootstrapLoader = ClassLoader.getSystemClassLoader();
    while (bootstrapLoader.getParent() != null) {
      bootstrapLoader = bootstrapLoader.getParent();
    }
    return bootstrapLoader;
  }
}
