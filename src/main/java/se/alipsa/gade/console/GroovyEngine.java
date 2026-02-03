package se.alipsa.gade.console;

import java.io.PrintWriter;
import java.util.Map;
import javax.script.ScriptException;

public interface GroovyEngine {
  Object eval(String script) throws ScriptException;

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
  void setOutputWriters(PrintWriter out, PrintWriter err);

  Object fetchVar(String varName);

  Map<String, Object> getContextObjects();

  /**
   * Equivalent of engine.put(key, value);
   *
   * @param key
   * @param value
   */
  void addVariableToSession(String key, Object value);

  /**
   * Remove a binding from the engine.
   *
   * @param varName the bound variable to remove.
   */
  void removeVariableFromSession(String varName);
}
