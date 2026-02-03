package se.alipsa.gade.console;

import groovy.lang.GroovyClassLoader;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;

public class GroovyEngineInvocation implements GroovyEngine {

  private final Class<?> engineClass;
  private final Object engine;
  MethodHandles.Lookup lookup;

  public GroovyEngineInvocation(GroovyClassLoader classLoader) {
    lookup = MethodHandles.publicLookup();
    try {
      engineClass = classLoader.loadClass("org.codehaus.groovy.jsr223.GroovyScriptEngineImpl");
      engine = engineClass.getDeclaredConstructor().newInstance();
    } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
             IllegalAccessException | InstantiationException e) {
      throw new GroovyEngineException("Failed to load 'org.codehaus.groovy.jsr223.GroovyScriptEngineImpl'", e);
    }
  }

  MethodHandle getHandle(Class<?> returnType, String method, Class<?>... params) {
    return getHandle(engineClass, returnType, method, params);
  }

  MethodHandle getHandle(Class<?> caller, Class<?> returnType, String method, Class<?>... params) {
    try {
      var methodType = MethodType.methodType(returnType, params);
      return lookup.findVirtual(caller, method, methodType);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new GroovyEngineException("Failed to invoke '" + method + "' method on " + caller.getSimpleName(), e);
    }
  }

  @Override
  public Object eval(String script) throws ScriptException {
    try {
      MethodHandle eval = getHandle(Object.class, "eval", String.class);
      return eval.invoke(engine, script);
    } catch (Throwable e) {
      throw new GroovyEngineException("Failed to evaluate script", e);
    }
  }

  /**
   * Equivalent of
   * <pre>
   * engine.getContext().setWriter(outputWriter);
   * engine.getContext().setErrorWriter(errWriter);
   * </pre>
   *
   * @param out the standard out PrintWriter
   * @param err the standard err PrintWriter
   */
  @Override
  public void setOutputWriters(PrintWriter out, PrintWriter err) {
    try {
      var contextHandle = getHandle(ScriptContext.class, "getContext");
      var context = contextHandle.invoke(engine);
      var setWriterHandle = getHandle(context.getClass(), void.class,"setWriter", Writer.class);
      setWriterHandle.invoke(context, out);
      var setErrrHandle = getHandle(context.getClass(), void.class,"setErrorWriter", Writer.class);
      setErrrHandle.invoke(context, err);
    } catch (Throwable e) {
      throw new GroovyEngineException("Failed to set output writers", e);
    }
  }

  @Override
  public Object fetchVar(String varName) {
    try {
      var getHandle = getHandle(Object.class, "get", String.class);
      return getHandle.invoke(engine, varName);
    } catch (Throwable e) {
      throw new GroovyEngineException("Failed to set variable " + varName, e);
    }
  }

  @Override
  public Map<String, Object> getContextObjects() {
    try {
      var contextObjectsHandle = getHandle(Bindings.class, "getBindings", int.class);
      Bindings bindings = (Bindings)contextObjectsHandle.invoke(engine, ScriptContext.ENGINE_SCOPE);
      return new HashMap<>(bindings);
    } catch (Throwable e) {
      throw new GroovyEngineException("Failed to getBindings", e);
    }
  }

  /**
   * Equivalent of engine.put(key, value);
   *
   * @param key the String identifier (variable name) for the variable
   * @param value the value to bind to the key
   */
  @Override
  public void addVariableToSession(String key, Object value) {
    try {
      var putHandle = getHandle(void.class, "put", String.class, Object.class);
      putHandle.invoke(engine, key, value);
    } catch (Throwable e) {
      throw new GroovyEngineException("Failed to put " + key, e);
    }
  }

  /**
   * Remove a binding from the engine.
   *
   * @param varName the bound variable to remove.
   */
  @Override
  public void removeVariableFromSession(String varName) {
    try {
      var contextObjectsHandle = getHandle(Bindings.class, "getBindings", int.class);
      Bindings bindings = (Bindings)contextObjectsHandle.invoke(engine, ScriptContext.ENGINE_SCOPE);
      var removeHandle = getHandle(bindings.getClass(), Object.class, "remove", Object.class);
      removeHandle.invoke(bindings.getClass(), varName);
    } catch (Throwable e) {
      throw new GroovyEngineException("Failed to remove " + varName, e);
    }
  }
}
