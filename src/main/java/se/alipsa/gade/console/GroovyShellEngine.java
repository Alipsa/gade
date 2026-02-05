package se.alipsa.gade.console;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import javax.script.ScriptException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * GroovyEngine implementation using GroovyShell instead of GroovyScriptEngineImpl.
 * This implementation doesn't require groovy-jsr223, only core Groovy classes.
 */
public class GroovyShellEngine implements GroovyEngine {
  private static final Logger log = LogManager.getLogger(GroovyShellEngine.class);

  private final GroovyShell shell;
  private final Binding binding;
  private final GroovyClassLoader classLoader;

  public GroovyShellEngine(GroovyClassLoader classLoader) {
    this.classLoader = classLoader;

    // Create binding and shell directly (no reflection needed for GADE runtime)
    this.binding = new Binding();
    this.shell = new GroovyShell(classLoader, binding);

    log.debug("Created GroovyShell with classloader: {}", classLoader.getClass().getName());
  }

  @Override
  public Object eval(String script) throws ScriptException {
    try {
      return shell.evaluate(script);
    } catch (Exception e) {
      // Wrap Groovy exceptions in ScriptException for consistency with JSR-223
      String message = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
      ScriptException se = new ScriptException(message);
      se.initCause(e);
      throw se;
    }
  }

  @Override
  public void setOutputWriters(PrintWriter out, PrintWriter err) {
    binding.setProperty("out", out);
    binding.setProperty("err", err);
  }

  @Override
  public Object fetchVar(String varName) {
    try {
      return binding.getVariable(varName);
    } catch (Exception e) {
      // Variable not found, return null like ScriptEngine does
      return null;
    }
  }

  @Override
  public Map<String, Object> getContextObjects() {
    Map<String, Object> variables = binding.getVariables();
    log.info("Bindings size: {}", variables.size());
    return new HashMap<>(variables);
  }

  @Override
  public void addVariableToSession(String key, Object value) {
    binding.setVariable(key, value);
  }

  @Override
  public void removeVariableFromSession(String varName) {
    binding.getVariables().remove(varName);
  }
}
