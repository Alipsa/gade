import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.codehaus.groovy.runtime.InvokerHelper
import se.alipsa.groovy.matrix.Matrix

class MyScriptTest {

  @Test
  void testMyScript() {
    // This is equivalent of passing args to the main method
    // but with the added benefit that any variable bound will be accessible
    Binding context = new Binding("John");
    Script script = InvokerHelper.createScript(MyScript.class, context);
    script.run()

    Matrix table = context.getVariable("table") as Matrix
    Assertions.assertEquals("John", table[0, "borrower"], "Who borrowed the first book")
  }
}
