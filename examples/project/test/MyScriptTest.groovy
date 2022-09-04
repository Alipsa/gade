import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import tech.tablesaw.api.Table
import org.codehaus.groovy.runtime.InvokerHelper

class MyScriptTest {

  @Test
  void testMyScript() {
    // This is equivalent of passing args to the main method
    // but with the added benefit that any variable bound will be accessible
    Binding context = new Binding("John");
    Script script = InvokerHelper.createScript(MyScript.class, context);
    script.run()

    Table table = context.getVariable("table") as Table
    Assertions.assertEquals("John", table.column("borrower").getString(0), "Who borrowed the first book")
  }
}
