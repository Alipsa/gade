package [groupId].[lowercaseProjectName];

import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.*;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

public class [className]Test {

  @Test
  public void test[className]() throws IOException, ScriptException {
    try (InputStreamReader in = new InputStreamReader(ScriptLoader.getScript().openStream())) {

      GroovyScriptEngineFactory factory = new GroovyScriptEngineFactory();
      ScriptEngine engine = factory.getScriptEngine();

      Object result = engine.eval(in);

      // Do some assertions to ensure the script is working correctly
      assertThat(result, is(notNullValue()));
    }
  }

}