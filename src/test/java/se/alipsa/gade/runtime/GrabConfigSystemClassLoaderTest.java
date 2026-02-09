package se.alipsa.gade.runtime;

import org.junit.jupiter.api.Test;
import se.alipsa.gade.runner.GroovyProcessRootLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test: verifies GroovyProcessRootLoader is accepted by
 * GrapeIvy's isValidTargetClassLoaderClass() for @GrabConfig(systemClassLoader=true).
 */
public class GrabConfigSystemClassLoaderTest {

  @Test
  void groovyProcessRootLoaderPassesGrapeValidation() {
    // Replicate GrapeIvy.isValidTargetClassLoaderClass logic
    GroovyProcessRootLoader loader = new GroovyProcessRootLoader(
        ClassLoader.getSystemClassLoader());
    assertTrue(isValidTargetClassLoaderClass(loader.getClass()),
        "GroovyProcessRootLoader must be recognized as a valid Grape target");
  }

  @Test
  void processRootLoaderFailsGrapeValidation() {
    // Confirm the original ProcessRootLoader does NOT pass
    assertFalse(isValidTargetClassLoaderClass(
        se.alipsa.gade.runner.ProcessRootLoader.class),
        "ProcessRootLoader should not pass Grape validation (documents the root cause)");
  }

  /** Mirror of GrapeIvy.isValidTargetClassLoaderClass */
  private static boolean isValidTargetClassLoaderClass(Class<?> loaderClass) {
    return loaderClass != null
        && ("groovy.lang.GroovyClassLoader".equals(loaderClass.getName())
            || "org.codehaus.groovy.tools.RootLoader".equals(loaderClass.getName())
            || isValidTargetClassLoaderClass(loaderClass.getSuperclass()));
  }
}
