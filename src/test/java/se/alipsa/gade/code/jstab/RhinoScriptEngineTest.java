package se.alipsa.gade.code.jstab;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.engine.RhinoScriptEngineFactory;

import javax.script.ScriptEngine;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RhinoScriptEngineTest {

  @Test
  void createsRhinoEngineThroughJsr223() {
    RhinoScriptEngineFactory factory = new RhinoScriptEngineFactory();
    ScriptEngine engine = factory.getScriptEngine();

    assertEquals("rhino", factory.getEngineName());
    assertEquals(12, ((Number) eval(engine, "let values = [1, 2, 3]; values.map(value => value * 2).reduce((a, b) => a + b, 0)")).intValue());
  }

  @Test
  void convertsRhinoArraysToJavaMatrices() throws Exception {
    ScriptEngine engine = new RhinoScriptEngineFactory().getScriptEngine();

    Object value = engine.eval("[[1, 2], [3, 4]]");

    assertInstanceOf(NativeArray.class, value);
    Object[][] matrix = RhinoValueConverter.toObjectMatrix(value);
    assertArrayEquals(new Object[]{1.0, 2.0}, matrix[0]);
    assertArrayEquals(new Object[]{3.0, 4.0}, matrix[1]);
  }

  @Test
  void bundledViewHelperUsesRhinoValuesWithoutJavaTo() throws Exception {
    ScriptEngine engine = new RhinoScriptEngineFactory().getScriptEngine();
    ViewTarget target = new ViewTarget();
    engine.put("inout", target);
    engine.eval(Files.readString(Path.of("src/main/resources/js/init.js")));

    engine.eval("View([[1, 2], [3, 4]], 'sample')");

    Object[][] matrix = RhinoValueConverter.toObjectMatrix(target.value);
    assertEquals("sample", target.title);
    assertArrayEquals(new Object[]{1.0, 2.0}, matrix[0]);
    assertArrayEquals(new Object[]{3.0, 4.0}, matrix[1]);
  }

  private static Object eval(ScriptEngine engine, String script) {
    try {
      return engine.eval(script);
    } catch (Exception e) {
      throw new AssertionError("JavaScript evaluation failed", e);
    }
  }

  public static class ViewTarget {
    private Object value;
    private String title;

    public void view(Object value, String title) {
      this.value = value;
      this.title = title;
    }
  }
}
