package se.alipsa.gade.utils;

public class ClassUtils {

  public static ClassLoader getBootstrapClassLoader() {
    ClassLoader bootstrapLoader = ClassLoader.getSystemClassLoader();
    while (bootstrapLoader.getParent() != null) {
      bootstrapLoader = bootstrapLoader.getParent();
    }
    return bootstrapLoader;
  }
}
