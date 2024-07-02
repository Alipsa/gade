package code.groovy;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.util.GroovyScriptEngine;
import groovy.util.ResourceException;
import groovy.util.ScriptException;
import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressFBWarnings()
public class JdbcTest {

  private static final String dbDriver = "org.h2.Driver";
  private static final String dbUrl = "jdbc:h2:file:"+ new File(".").getAbsolutePath() + "/build/testdb";
  private static final String dbUser = "sa";
  private static final String dbPasswd = "123";

  private static final Map<String, Object> variables;

  static {
    variables = Map.of(
        "dbDriver", dbDriver,
        "dbUrl", dbUrl,
        "dbUser", dbUser,
        "dbPasswd", dbPasswd
    );
  }

  @BeforeAll
  public static void init() throws ScriptException, MalformedURLException, ResourceException, URISyntaxException {
    runWithGroovyScriptEngine(JdbcTest.class.getResource("/groovy/init.groovy"), variables);
  }

  @Test
  public void testSqlManual() throws IOException, ScriptException, ResourceException, URISyntaxException, javax.script.ScriptException {
    System.out.println("\nSqlManual");
    System.out.println("---------");
    var scriptPath = "/groovy/SqlManual.groovy";
    URL url = getClass().getResource(scriptPath);
    String groovyCode = new Scanner(url.openStream(), "UTF-8").useDelimiter("\\A").next();
    System.out.println("***** runWithShell");
    var idList = (List<?>)runWithShell(groovyCode, variables);
    assertEquals(2, idList.size());

    System.out.println("***** runWithGroovyScriptEngine");
    idList = (List<?>)runWithGroovyScriptEngine(url, variables);
    assertEquals(2, idList.size());

    System.out.println("***** runWithScriptEngine");
    idList = (List<?>)runWithScriptEngine(groovyCode, variables);
    assertEquals(2, idList.size());
  }

  @Test
  public void testSqlNewInstance() throws IOException, javax.script.ScriptException, ScriptException, ResourceException, URISyntaxException {
    Thread.currentThread().setContextClassLoader(new GroovyClassLoader());
    System.out.println("\nSqlNewInstance");
    System.out.println("--------------");
    var scriptPath = "/groovy/SqlNewInstance.groovy";
    URL url = getClass().getResource(scriptPath);
    assertNotNull(url, "failed to find " + scriptPath);
    String groovyCode;
    try (InputStream is = url.openStream()) {
      groovyCode = new Scanner(is, "UTF-8").useDelimiter("\\A").next();
    }
    System.out.println("***** runWithShell");
    //System.out.println(groovyCode);
    var idList = (List<?>)runWithShell(groovyCode, variables);
    assertEquals(2, idList.size());

    System.out.println("***** runWithGroovyScriptEngine");
    idList = (List<?>)runWithGroovyScriptEngine(url, variables);
    assertEquals(2, idList.size());

    System.out.println("***** runWithScriptEngine");
    idList = (List<?>)runWithScriptEngine(groovyCode, variables);
    assertEquals(2, idList.size());
  }

  @Test
  public void testSqlWithInstance() throws IOException, javax.script.ScriptException, ScriptException, ResourceException, URISyntaxException {
    Thread.currentThread().setContextClassLoader(new GroovyClassLoader());
    System.out.println("\nSqlWithInstance");
    System.out.println("--------------");
    var scriptPath = "/groovy/SqlWithInstance.groovy";
    URL url = getClass().getResource(scriptPath);
    assertNotNull(url, "failed to find " + scriptPath);
    String groovyCode;
    try (InputStream is = url.openStream()) {
      groovyCode = new Scanner(is, "UTF-8").useDelimiter("\\A").next();
    }
    System.out.println("***** runWithShell");
    var idList = (List<?>)runWithShell(groovyCode, variables);
    assertEquals(2, idList.size());

    System.out.println("***** runWithGroovyScriptEngine");
    idList = (List<?>)runWithGroovyScriptEngine(url, variables);
    assertEquals(2, idList.size());

    System.out.println("***** runWithScriptEngine");
    idList = (List<?>)runWithScriptEngine(groovyCode, variables);
    assertEquals(2, idList.size());
  }


  private static Object runWithShell(String groovyCode, Map<String, Object> variables) {
    GroovyClassLoader gcl = new GroovyClassLoader(Thread.currentThread().getContextClassLoader());
    GroovyShell groovyShell = new GroovyShell(gcl);
    variables.forEach(groovyShell::setProperty);
    return groovyShell.evaluate(groovyCode);
  }

  private static Object runWithGroovyScriptEngine(URL groovyCodeFile, Map<String, Object> variables) throws URISyntaxException, MalformedURLException, ScriptException, ResourceException {
    URI uri = groovyCodeFile.toURI();
    URI parent = uri.getPath().endsWith("/") ? uri.resolve("..") : uri.resolve(".");
    URL[] groovyCodeDir = new URL[] {parent.toURL()};
    GroovyClassLoader gcl = new GroovyClassLoader();
    Binding binding = new Binding();
    variables.forEach(binding::setProperty);
    var engine = new GroovyScriptEngine(groovyCodeDir, gcl);
    return engine.run(groovyCodeFile.getFile(), binding);
  }

  private static Object runWithScriptEngine(String groovyCode, Map<String, Object> variables) throws javax.script.ScriptException {
    var factory = new GroovyScriptEngineFactory();
    var engine = factory.getScriptEngine();
    variables.forEach(engine::put);
    return engine.eval(groovyCode);
  }
}
