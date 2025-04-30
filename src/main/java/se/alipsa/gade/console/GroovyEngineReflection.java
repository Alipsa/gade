package se.alipsa.gade.console;

import groovy.lang.GroovyClassLoader;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import javax.script.ScriptContext;
import javax.script.ScriptException;

// TODO: investigate invokevirtual and invokedynamic as alternatives to reflection invocation
public class GroovyEngineReflection implements GroovyEngine {
  private final Object engine;
  private final Class<?> engineClass;

  GroovyEngineReflection(GroovyClassLoader classLoader) {
    try {
      engineClass = classLoader.loadClass("org.codehaus.groovy.jsr223.GroovyScriptEngineImpl");
      engine = engineClass.getDeclaredConstructor().newInstance();
    } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
             IllegalAccessException | InstantiationException e) {
      throw new GroovyEngineException("Failed to load 'org.codehaus.groovy.jsr223.GroovyScriptEngineImpl'", e);
    }
  }

  @Override
  public Object eval(String script) throws ScriptException {
    try {
      return engineClass.getMethod("eval", String.class).invoke(engine, script);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ScriptException) {
        throw (ScriptException) e.getCause();
      }
      throw new GroovyEngineException("Failed to evaluate script", cause);
    } catch (IllegalAccessException | NoSuchMethodException e) {
      throw new GroovyEngineException("Failed to invoke 'eval' method on engine", e);
    }
  }

  @Override
  public void setOutputWriters(PrintWriter out, PrintWriter err) {
    try {
      var context = engineClass.getMethod("getContext").invoke(engine);
      context.getClass().getMethod("setWriter", Writer.class).invoke(context, out);
      context.getClass().getMethod("setErrorWriter", Writer.class).invoke(context, err);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new GroovyEngineException("Failed to invoke 'getContext' method on engine", e);
    }
  }

  @Override
  public Object fetchVar(String varName) {
    try {
    return engineClass.getMethod("get", String.class).invoke(engine, varName);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new GroovyEngineException("Failed to invoke 'get' method on engine", e);
    }
  }

  @Override
  public Map<String, Object> getContextObjects() {
    try {
      Map bindings = (Map)engineClass
          .getMethod("getBindings", int.class)
          .invoke(engine, ScriptContext.ENGINE_SCOPE);
      //contextObjects.putAll(engine.getBindings(ScriptContext.ENGINE_SCOPE));
      return new HashMap<>(bindings);
    } catch (IllegalAccessException | InvocationTargetException | SecurityException |
             NoSuchMethodException e) {
      throw new GroovyEngineException("Failed to invoke 'getBindings' method on engine", e);
    }
  }

  @Override
  public void addVariableToSession(String key, Object value) {
    try {
      engineClass.getMethod("put", String.class, Object.class)
          .invoke(engine, key, value);
    } catch (Exception e) {
      throw new GroovyEngineException("Failed to invoke 'put' method on engine", e);
    }
  }

  /**
   * Equivalent to engine.getBindings(ScriptContext.ENGINE_SCOPE).remove(varName);
   * @param varName the bound variable to remove.
   */
  @Override
  public void removeVariableFromSession(String varName) {
    try {
      var bindings = engineClass.getMethod("getBindings", int.class)
          .invoke(engine, ScriptContext.ENGINE_SCOPE);
      bindings.getClass().getMethod("remove", String.class)
          .invoke(bindings, varName);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw new GroovyEngineException("Failed to invoke 'getBindings' method on engine", e);
    }
  }
}
