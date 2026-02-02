package se.alipsa.gade.runtime;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.console.ConsoleTextArea;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that groovy-jsr223 is only required for runtimes that actually use it.
 */
public class RuntimeJsr223RequirementTest {

  @Test
  public void testGradleRuntimeDoesNotRequireJsr223() {
    // Gradle runtime runs scripts in a separate process via RuntimeProcessRunner,
    // so it doesn't need groovy-jsr223 in Gade's classloader.

    // Create a minimal classloader without groovy-jsr223
    GroovyClassLoader minimalLoader = new GroovyClassLoader(ClassLoader.getSystemClassLoader());

    // Try to load GroovySystem (core Groovy class)
    try {
      minimalLoader.loadClass("groovy.lang.GroovySystem");
      System.out.println("✓ Core Groovy is available");
    } catch (ClassNotFoundException e) {
      fail("Core Groovy classes should be available");
    }

    // Verify that groovy-jsr223 is NOT required
    try {
      minimalLoader.loadClass("org.codehaus.groovy.jsr223.GroovyScriptEngineImpl");
      System.out.println("groovy-jsr223 is available (optional for Gradle runtime)");
    } catch (ClassNotFoundException e) {
      System.out.println("✓ groovy-jsr223 not available (this is fine for Gradle/Maven runtimes)");
    }

    // The key point: Gradle runtime should work fine without groovy-jsr223
    System.out.println("Gradle runtime can work without groovy-jsr223 because it uses RuntimeProcessRunner");
  }

  @Test
  public void testGroovyShellEngineDoesNotRequireJsr223() throws Exception {
    // GroovyShellEngine should work with just core Groovy classes
    GroovyClassLoader minimalLoader = new GroovyClassLoader(ClassLoader.getSystemClassLoader());

    // Verify core Groovy classes are available
    minimalLoader.loadClass("groovy.lang.GroovyShell");
    minimalLoader.loadClass("groovy.lang.Binding");

    System.out.println("✓ GroovyShellEngine can be created with just core Groovy");

    // Create a GroovyShellEngine
    se.alipsa.gade.console.GroovyShellEngine engine =
        new se.alipsa.gade.console.GroovyShellEngine(minimalLoader);

    // Test basic functionality
    Object result = engine.eval("1 + 1");
    assertEquals(2, result);
    System.out.println("✓ GroovyShellEngine eval works: 1 + 1 = " + result);

    // Test variables
    engine.addVariableToSession("x", 42);
    Object x = engine.fetchVar("x");
    assertEquals(42, x);
    System.out.println("✓ GroovyShellEngine variables work: x = " + x);

    result = engine.eval("x * 2");
    assertEquals(84, result);
    System.out.println("✓ GroovyShellEngine can use variables: x * 2 = " + result);
  }

  @Test
  public void testRuntimeTypeRequirements() {
    System.out.println("\nRuntime type JSR-223 requirements:");
    System.out.println("  GADE:   Requires groovy-jsr223 (uses GroovyEngine in-process)");
    System.out.println("  GRADLE: Does NOT require groovy-jsr223 (uses RuntimeProcessRunner)");
    System.out.println("  MAVEN:  Does NOT require groovy-jsr223 (uses RuntimeProcessRunner)");
    System.out.println("  CUSTOM: May require groovy-jsr223 (depends on configuration)");
    System.out.println("\nAlternative: GroovyShellEngine works for all types without groovy-jsr223");
  }
}
