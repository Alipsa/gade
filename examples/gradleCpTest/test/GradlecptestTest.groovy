import org.junit.jupiter.api.*
import se.alipsa.groovy.matrix.*
import org.codehaus.groovy.runtime.InvokerHelper

class GradlecptestTest {

  void testGradlecptest() {
    // 1. Create a binding, possibly with parameters which will be equivalent to the main args.
    Binding context = new Binding();
    // 2. Create and invoke the script
    Script script = InvokerHelper.createScript(Gradlecptest.class, context);
    script.run()
    // 3. Access "global" (@Field) variables from the binding context, e.g:
    //Table table = context.getVariable("table") as Table

    //4. Make assertions on these variables as appropriate
  }
}
