package se.alipsa.gade.utils;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class ReflectUtils {

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

    public Invokable invoke(String methodName) {
      try {
        return new Invokable(result.getClass().getMethod(methodName)
            .invoke(result));
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
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

  public static Invokable invoke(Object caller, String methodName, Map<Class<?>, Object> params) {
    try {
      Class<?>[] classParams = params.keySet().toArray(new Class[0]);
      Object[] values = params.values().toArray(new Object[0]);
      return new Invokable(caller.getClass().getMethod(methodName, classParams)
          .invoke(caller, values));
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  public static Invokable invoke(Object caller, String methodName) {
    try {
      return new Invokable(caller.getClass().getMethod(methodName)
          .invoke(caller));
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }
}
