package se.alipsa.gade.grape;

import groovy.grape.Grape;
import groovy.grape.GrapeEngine;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Installs a {@link ScriptFriendlyGrapeEngine} so {@code @Grab} annotations respect the isolated
 * script class loader hierarchy instead of mutating the IDE/application loader.
 */
public final class GrapeEngineInstaller {

  private static final Logger LOG = LogManager.getLogger(GrapeEngineInstaller.class);
  private static final AtomicBoolean INSTALLED = new AtomicBoolean();

  private GrapeEngineInstaller() {
    // Utility class
  }

  /**
   * Ensure the {@link ScriptFriendlyGrapeEngine} wrapper is installed.
   */
  public static void install() {
    if (!INSTALLED.compareAndSet(false, true)) {
      return;
    }
    try {
      Field field = Grape.class.getDeclaredField("instance");
      field.setAccessible(true);
      GrapeEngine current = (GrapeEngine) field.get(null);
      if (current == null) {
        current = Grape.getInstance();
      }
      if (current instanceof ScriptFriendlyGrapeEngine) {
        return;
      }
      field.set(null, new ScriptFriendlyGrapeEngine(current));
    } catch (ReflectiveOperationException e) {
      LOG.warn("Failed to install script friendly Grape engine", e);
    }
  }
}
