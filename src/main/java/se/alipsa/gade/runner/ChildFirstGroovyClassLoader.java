package se.alipsa.gade.runner;

import groovy.lang.GroovyClassLoader;

/**
 * A GroovyClassLoader variant with child-first delegation for non-JDK classes.
 */
public class ChildFirstGroovyClassLoader extends GroovyClassLoader {

  public ChildFirstGroovyClassLoader(ClassLoader parent) {
    super(parent);
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      if (shouldDelegateParentFirst(name)) {
        return super.loadClass(name, resolve);
      }
      Class<?> loaded = findLoadedClass(name);
      if (loaded == null) {
        try {
          loaded = findClass(name);
        } catch (ClassNotFoundException e) {
          loaded = super.loadClass(name, false);
        }
      }
      if (resolve) {
        resolveClass(loaded);
      }
      return loaded;
    }
  }

  private boolean shouldDelegateParentFirst(String className) {
    return className.startsWith("java.")
        || className.startsWith("javax.")
        || className.startsWith("jdk.")
        || className.startsWith("sun.")
        || className.startsWith("com.sun.")
        || className.startsWith("org.w3c.")
        || className.startsWith("org.xml.")
        || className.startsWith("org.ietf.");
  }
}
