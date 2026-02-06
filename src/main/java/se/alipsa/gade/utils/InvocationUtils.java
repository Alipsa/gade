package se.alipsa.gade.utils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class InvocationUtils {

  static MethodHandles.Lookup lookup = null;

  public static String callingMethod(int... elementNum) {
    int idx = elementNum.length > 0 ? elementNum[0] : 2;
    StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
    StackTraceElement e = stacktrace[idx];
    return e.getClassName() + "." + e.getMethodName();
  }

  public static MethodHandle getHandle(Class<?> caller, Class<?> returnType, String method, Class<?>... params) {
    try {
      var methodType = MethodType.methodType(returnType, params);
      return getLookup().findVirtual(caller, method, methodType);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new RuntimeException("Failed to invoke '" + method + "' method on " + caller.getSimpleName(), e);
    }
  }

  private static MethodHandles.Lookup getLookup() {
    if (lookup == null) {
      lookup = MethodHandles.lookup();
    }
    return lookup;
  }
}
