package se.alipsa.gade.console;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * GroovyEngine implementation using GroovyShell instead of GroovyScriptEngineImpl.
 * This implementation doesn't require groovy-jsr223, only core Groovy classes.
 */
public class GroovyShellEngine implements GroovyEngine {
  private static final Logger log = LoggerFactory.getLogger(GroovyShellEngine.class);

  private final Object shell;
  private final Object binding;
  private final GroovyClassLoader classLoader;

  public GroovyShellEngine(GroovyClassLoader classLoader) {
    this.classLoader = classLoader;
    try {
      // Load classes from the runtime classloader
      Class<?> bindingClass = classLoader.loadClass("groovy.lang.Binding");
      Class<?> shellClass = classLoader.loadClass("groovy.lang.GroovyShell");
      Class<?> configClass = classLoader.loadClass("org.codehaus.groovy.control.CompilerConfiguration");

      // Create binding and configuration
      this.binding = bindingClass.getDeclaredConstructor().newInstance();
      Object config = configClass.getDeclaredConstructor().newInstance();

      // Create shell using the runtime classloader
      Constructor<?> shellConstructor = shellClass.getDeclaredConstructor(
          ClassLoader.class, bindingClass, configClass);
      this.shell = shellConstructor.newInstance(classLoader, binding, config);

      log.debug("Created GroovyShell using runtime classloader");
    } catch (Exception e) {
      throw new GroovyEngineException("Failed to create GroovyShell from runtime classloader", e);
    }
  }

  @Override
  public Object eval(String script) throws ScriptException {
    try {
      // Call shell.evaluate(script)
      return shell.getClass()
          .getMethod("evaluate", String.class)
          .invoke(shell, script);
    } catch (Exception e) {
      Throwable cause = e.getCause();
      if (cause != null) {
        // Wrap Groovy exceptions in ScriptException for consistency with JSR-223
        throw new ScriptException(cause.getMessage());
      }
      throw new ScriptException(e.getMessage());
    }
  }

  @Override
  public void setOutputWriters(PrintWriter out, PrintWriter err) {
    try {
      // binding.setProperty("out", out)
      // binding.setProperty("err", err)
      binding.getClass()
          .getMethod("setProperty", String.class, Object.class)
          .invoke(binding, "out", out);
      binding.getClass()
          .getMethod("setProperty", String.class, Object.class)
          .invoke(binding, "err", err);
    } catch (Exception e) {
      throw new GroovyEngineException("Failed to set output writers", e);
    }
  }

  @Override
  public Object fetchVar(String varName) {
    try {
      // binding.getVariable(varName)
      return binding.getClass()
          .getMethod("getVariable", String.class)
          .invoke(binding, varName);
    } catch (Exception e) {
      // Variable not found, return null like ScriptEngine does
      return null;
    }
  }

  @Override
  public Map<String, Object> getContextObjects() {
    try {
      // binding.getVariables() returns Map<String, Object>
      @SuppressWarnings("unchecked")
      Map<String, Object> variables = (Map<String, Object>) binding.getClass()
          .getMethod("getVariables")
          .invoke(binding);

      log.info("Bindings size: {}", variables.size());
      return new HashMap<>(variables);
    } catch (Exception e) {
      throw new GroovyEngineException("Failed to get context objects", e);
    }
  }

  @Override
  public void addVariableToSession(String key, Object value) {
    try {
      // binding.setVariable(key, value)
      binding.getClass()
          .getMethod("setVariable", String.class, Object.class)
          .invoke(binding, key, value);
    } catch (Exception e) {
      throw new GroovyEngineException("Failed to add variable to session", e);
    }
  }

  @Override
  public void removeVariableFromSession(String varName) {
    try {
      // binding.getVariables().remove(varName)
      @SuppressWarnings("unchecked")
      Map<String, Object> variables = (Map<String, Object>) binding.getClass()
          .getMethod("getVariables")
          .invoke(binding);
      variables.remove(varName);
    } catch (Exception e) {
      throw new GroovyEngineException("Failed to remove variable from session", e);
    }
  }
}
