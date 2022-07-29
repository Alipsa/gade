package se.alipsa.gade.utils;


public final class JavaVersion {

  private JavaVersion() {
    // Utility class
  }

  /**
   *
   * @return the java version currently running
   * for java less than 9 the right-hand side will be returned e.g 1.8 will become 8
   */
  public static int specVersion() {
    double jvmVersion = Double.parseDouble(System.getProperty("java.specification.version"));
    if (jvmVersion < 2) {
      jvmVersion = (jvmVersion - 1) * 10;
    }
    return (int)Math.round(jvmVersion);
  }
}
