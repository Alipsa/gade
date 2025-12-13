package runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.runtime.RuntimeIsolation;
import se.alipsa.gade.runtime.RuntimeType;

class RuntimeIsolationTest {

  private static final String PROP = "groovy.grape.enable.system.classloader";
  private String previous = System.getProperty(PROP);

  @AfterEach
  void restoreProp() {
    if (previous == null) {
      System.clearProperty(PROP);
    } else {
      System.setProperty(PROP, previous);
    }
  }

  @Test
  void nonGadeRuntimeDisablesSystemClassloaderForGrab() throws Exception {
    System.setProperty(PROP, "true");
    var loader = new URLClassLoader(new java.net.URL[0], getClass().getClassLoader());
    AtomicReference<ClassLoader> seenLoader = new AtomicReference<>();
    String result = RuntimeIsolation.run(loader, RuntimeType.GRADLE, () -> {
      seenLoader.set(Thread.currentThread().getContextClassLoader());
      return System.getProperty(PROP);
    });
    assertEquals("false", result);
    assertSame(loader, seenLoader.get());
    assertEquals("true", System.getProperty(PROP));
  }

  @Test
  void gadeRuntimeLeavesSystemClassloaderFlagUntouched() throws Exception {
    System.clearProperty(PROP);
    var loader = new URLClassLoader(new java.net.URL[0], getClass().getClassLoader());
    AtomicReference<ClassLoader> seenLoader = new AtomicReference<>();
    String result = RuntimeIsolation.run(loader, RuntimeType.GADE, () -> {
      seenLoader.set(Thread.currentThread().getContextClassLoader());
      return System.getProperty(PROP);
    });
    assertNull(result);
    assertSame(loader, seenLoader.get());
    assertNull(System.getProperty(PROP));
  }
}
