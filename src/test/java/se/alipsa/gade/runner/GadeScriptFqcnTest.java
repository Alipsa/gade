package se.alipsa.gade.runner;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for FQCN resolution via GadeScript base class.
 * Verifies that inline fully-qualified class names produce helpful error messages
 * instead of confusing "No such property" errors.
 */
class GadeScriptFqcnTest {

  private GroovyShell shell;
  private Binding binding;

  @BeforeEach
  void setUp() {
    CompilerConfiguration config = new CompilerConfiguration();
    config.setScriptBaseClass(GadeScript.class.getName());
    binding = new Binding();
    shell = new GroovyShell(binding, config);
  }

  @Test
  void fqcnResolvesKnownJdkClass() {
    Object result = shell.evaluate("java.util.Collections.emptyList()");
    assertEquals(List.of(), result);
  }

  @Test
  void fqcnStaticMethodCall() {
    Object result = shell.evaluate("java.util.Collections.singletonList('hello')");
    assertEquals(List.of("hello"), result);
  }

  @Test
  void unresolvedFqcnProducesHelpfulError() {
    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> shell.evaluate("se.alipsa.groovy.charts.BoxChart.create('test')"));
    assertTrue(ex.getMessage().contains("Cannot resolve"),
        "Expected 'Cannot resolve' in message but got: " + ex.getMessage());
  }

  @Test
  void uppercaseUnresolvedClassGivesImmediateError() {
    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> shell.evaluate("com.nonexistent.FakeClass.doSomething()"));
    assertTrue(ex.getMessage().contains("com.nonexistent.FakeClass"),
        "Expected FQCN in message but got: " + ex.getMessage());
  }

  @Test
  void bindingVariablesTakePrecedence() {
    binding.setVariable("io", "boundValue");
    Object result = shell.evaluate("io");
    assertEquals("boundValue", result);
  }

  @Test
  void nonPackagePropertyThrowsMissingPropertyException() {
    assertThrows(MissingPropertyException.class,
        () -> shell.evaluate("nonExistentVariable"));
  }

  @Test
  void shortNameNotOnClasspathThrowsMissingPropertyException() {
    // 'zz' should not correspond to any package root directory on the classpath
    assertThrows(MissingPropertyException.class,
        () -> shell.evaluate("zz"));
  }

  @Test
  void normalScriptExecutionUnaffected() {
    Object result = shell.evaluate("def x = 42; x * 2");
    assertEquals(84, result);
  }
}
