package se.alipsa.gade.code.jstab;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.engine.RhinoScriptEngineFactory;

import javax.script.ScriptEngine;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

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
    assertArrayEquals(new Object[]{1, 2}, matrix[0]);
    assertArrayEquals(new Object[]{3, 4}, matrix[1]);
  }

  @Test
  void bundledViewHelperUsesRhinoValuesWithoutJavaTo() throws Exception {
    ScriptEngine engine = new RhinoScriptEngineFactory().getScriptEngine();
    ViewTarget target = new ViewTarget();
    engine.put("io", target);
    engine.eval(readBundledInitScript());

    engine.eval("View([[1, 2], [3, 4]], 'sample')");

    Object[][] matrix = RhinoValueConverter.toObjectMatrix(target.value);
    assertEquals("list", target.overload);
    assertEquals("sample", target.title);
    assertArrayEquals(new Object[]{1, 2}, matrix[0]);
    assertArrayEquals(new Object[]{3, 4}, matrix[1]);
  }

  @Test
  void convertsSparseAndUndefinedValuesToNull() throws Exception {
    ScriptEngine engine = new RhinoScriptEngineFactory().getScriptEngine();

    Object[][] matrix = RhinoValueConverter.toObjectMatrix(
        engine.eval("[[1, , 3], [undefined, NaN, true]]"));

    assertArrayEquals(new Object[]{1, null, 3}, matrix[0]);
    assertArrayEquals(new Object[]{null, Double.NaN, true}, matrix[1]);
  }

  @Test
  void accessesJavaFxClassesThroughRhinoPackages() throws Exception {
    ScriptEngine engine = new RhinoScriptEngineFactory().getScriptEngine();

    Object types = engine.eval("""
        var fx = new JavaImporter(Packages.javafx.scene.chart, Packages.javafx.collections);
        with (fx) {
          [typeof PieChart, typeof FXCollections, typeof PieChart.Data].join(',');
        }
        """);

    assertEquals("function,function,function", types);
  }

  private static String readBundledInitScript() throws IOException {
    try (InputStream input = RhinoScriptEngineTest.class.getResourceAsStream("/js/init.js")) {
      return new String(Objects.requireNonNull(input, "Missing bundled init.js").readAllBytes(), StandardCharsets.UTF_8);
    }
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
    private String overload;

    public void view(List<List<?>> value, String title) {
      this.value = value;
      this.title = title;
      this.overload = "list";
    }

    public void view(Object value, String title) {
      this.value = value;
      this.title = title;
      this.overload = "object";
    }
  }
}
