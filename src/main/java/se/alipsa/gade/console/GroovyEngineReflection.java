package se.alipsa.gade.console;

import groovy.lang.GroovyClassLoader;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.alipsa.gade.Gade;
import se.alipsa.gade.interaction.Dialogs;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.GuiUtils;

import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.script.ScriptContext;
import javax.script.ScriptException;

import static se.alipsa.gade.menu.GlobalOptions.USE_GRADLE_CLASSLOADER;
import static se.alipsa.gade.utils.ReflectUtils.*;

// TODO: investigate invokevirtual and invokedynamic as alternatives to reflection invocation
public class GroovyEngineReflection implements GroovyEngine {
  private static final Logger log = LoggerFactory.getLogger(GroovyEngineReflection.class);
  private final Object engine;
  //private final Class<?> engineClass;

  GroovyEngineReflection(GroovyClassLoader classLoader) {
    Object tmpEngine;
    try {
      var engineClass = classLoader.loadClass("org.codehaus.groovy.jsr223.GroovyScriptEngineImpl");
      tmpEngine = engineClass.getDeclaredConstructor().newInstance();
    } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
             IllegalAccessException | InstantiationException e) {
      if (Gade.instance().getPrefs().getBoolean(USE_GRADLE_CLASSLOADER, false)) {
        tmpEngine = new GroovyScriptEngineImpl(classLoader);
        Platform.runLater(()-> {
        Alert alert =
            new Alert(Alert.AlertType.WARNING,
                """
                    Failed to load 'org.codehaus.groovy.jsr223.GroovyScriptEngineImpl', 
                    Gradle classloader enabled but no groovy jsr223 dependency defined. 
                    Disable using gradle for dependencies?
                    """,
                ButtonType.YES,
                ButtonType.NO);
        alert.setTitle("Groovy Script engine creation error");
        GuiUtils.addStyle(Gade.instance(), alert);
        alert.showAndWait().ifPresent(r -> {
          if (r == ButtonType.YES) {
            Gade.instance().getPrefs().putBoolean(USE_GRADLE_CLASSLOADER, false);
            Gade.instance().getMainMenu().restartEngine();
          }
        });
        });
      } else {
        throw new GroovyEngineException("Failed to load 'org.codehaus.groovy.jsr223.GroovyScriptEngineImpl'", e);
      }
    }
    engine = tmpEngine;
  }

  @Override
  public Object eval(String script) throws ScriptException {
    try {
      return invoke(engine, "eval", params(String.class, script)).getResult();
      //return engineClass.getMethod("eval", String.class).invoke(engine, script);
    } catch (IllegalAccessException | NoSuchMethodException e) {
      throw new GroovyEngineException("Failed to invoke 'eval' method on engine", e);
    } catch (Exception e) {
      Throwable cause = e.getCause();
      if (cause instanceof ScriptException) {
        throw (ScriptException) e.getCause();
      }
      throw new GroovyEngineException("Failed to evaluate script", cause);
    }
  }

  @Override
  public void setOutputWriters(PrintWriter out, PrintWriter err) {
    try {
      //var context = engineClass.getMethod("getContext").invoke(engine);
      var context = invoke(engine, "getContext");
      context.invoke("setWriter", params(Writer.class, out));
      context.invoke("setErrorWriter", params(Writer.class, err));
      //context.getClass().getMethod("setWriter", Writer.class).invoke(context, out);
      //context.getClass().getMethod("setErrorWriter", Writer.class).invoke(context, err);
    } catch (Exception e) {
      throw new GroovyEngineException("Failed to set output writers", e);
    }
  }

  @Override
  public Object fetchVar(String varName) {
    try {
      //return engineClass.getMethod("get", String.class).invoke(engine, varName);
      return invoke(engine, "get", params(String.class, varName)).getResult();
    } catch (Exception e) {
      throw new GroovyEngineException("Failed to invoke 'get' method on engine", e);
    }
  }

  /**
   * Equivalent to engine.getBindings(ScriptContext.ENGINE_SCOPE);
   *
   * @return the bound variables in the engine.
   */
  @Override
  public Map<String, Object> getContextObjects() {
    try {
      // Note, must be Map as only boostrap classes are available between different classloaders in Gade
      Map<String, Object> bindings = invoke(engine, "getBindings", params(int.class, ScriptContext.ENGINE_SCOPE))
          .getResult(Map.class);
      log.info("Bindings size: {}", bindings.size());
      return new HashMap<>(bindings);
    } catch (Exception e) {
      throw new GroovyEngineException("Failed to invoke 'getBindings' method on engine", e);
    }
  }

  @Override
  public void addVariableToSession(String key, Object value) {
    try {
      invoke(engine, "put", params(String.class, key, Object.class, value));
      //engineClass.getMethod("put", String.class, Object.class)
      //    .invoke(engine, key, value);
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
      /*
      var bindings = engineClass.getMethod("getBindings", int.class)
          .invoke(engine, ScriptContext.ENGINE_SCOPE);
      bindings.getClass().getMethod("remove", String.class)
          .invoke(bindings, varName);
       */
      invoke(engine, "getBindings", params(int.class, ScriptContext.ENGINE_SCOPE))
          .invoke("remove", params(String.class, varName));
    } catch (Exception e) {
      throw new GroovyEngineException("Failed to invoke 'getBindings' method on engine", e);
    }
  }
}
