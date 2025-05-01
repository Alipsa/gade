package se.alipsa.gade.utils;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

public class ReflectUtils {

  static final Class<?>[] CLASS_ARRAY = new Class[0];
  static final Object[] OBJECT_ARRAY = new Object[0];

  public static class Invokable {
    Object result;
    public Invokable(Object result){
      this.result = result;
    }
    public Invokable invoke(String methodName, Map<Class<?>, Object> params) {
      try {
        Class<?>[] classParams = params.keySet().toArray(new Class[0]);
        Object[] values = params.values().toArray(new Object[0]);
        return new Invokable(result.getClass().getMethod(methodName, classParams)
            .invoke(result, values));
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }

    public Invokable invoke(String methodName) throws Exception {
      try {
        return new Invokable(result.getClass().getMethod(methodName)
            .invoke(result));
      } catch (InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      throw new RuntimeException(cause);
    } catch (IllegalAccessException | NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }

    public Object getResult() {
      return result;
    }

    public <T> T getResult(Class<T> type) {
      return type.cast(result);
    }
  }

  public static Invokable invoke(final Object caller, final String methodName, final LinkedHashMap<Class<?>, Object> params) throws Exception {
    try {
      final Class<?>[] classParams = params.keySet().toArray(CLASS_ARRAY);
      final Object[] values = params.values().toArray(OBJECT_ARRAY);
      return new Invokable(caller.getClass().getMethod(methodName, classParams)
          .invoke(caller, values));
    } catch (InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      throw new RuntimeException(cause);
    } catch (IllegalAccessException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  public static Invokable invoke(final Object caller, final String methodName) {
    try {
      return new Invokable(caller.getClass().getMethod(methodName)
          .invoke(caller));
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  public static LinkedHashMap<Class<?>, Object> params(final Class<?> type, final Object value) {
    final LinkedHashMap<Class<?>, Object> params = new LinkedHashMap<>();
    params.put(type, value);
    return params;
  }

  public static LinkedHashMap<Class<?>, Object> params(final Class<?> type1, final Object value1,
                                                       final Class<?> type2, final Object value2) {
    final LinkedHashMap<Class<?>, Object> params = params(type1, value1);
    params.put(type2, value2);
    return params;
  }

  public static LinkedHashMap<Class<?>, Object> params(final Class<?> type1, final Object value1,
                                                       final Class<?> type2, final Object value2,
                                                       final Class<?> type3, final Object value3) {
    final LinkedHashMap<Class<?>, Object> params = params(type1, value1, type2, value2);
    params.put(type3, value3);
    return params;
  }
}
