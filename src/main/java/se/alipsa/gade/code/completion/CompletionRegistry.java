package se.alipsa.gade.code.completion;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing completion engine instances.
 * Provides centralized access to language-specific completion engines
 * and handles engine lifecycle (registration, lookup, invalidation).
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe. The singleton instance uses double-checked
 * locking with a volatile field to ensure safe publication across threads. All engine storage
 * uses {@link ConcurrentHashMap} for thread-safe concurrent access without external synchronization.
 * Multiple threads can safely register engines, perform lookups, and invalidate caches concurrently.
 *
 * @see ConcurrentHashMap
 * @threadsafe
 */
public final class CompletionRegistry {

  private static final Logger LOG = LogManager.getLogger(CompletionRegistry.class);

  // Singleton instance
  private static volatile CompletionRegistry instance;

  // Registered engines by language identifier
  private final Map<String, CompletionEngine> enginesByLanguage = new ConcurrentHashMap<>();

  // All registered engines (for invalidation)
  private final Set<CompletionEngine> allEngines = ConcurrentHashMap.newKeySet();

  private CompletionRegistry() {}

  /**
   * Returns the singleton instance.
   */
  public static CompletionRegistry getInstance() {
    if (instance == null) {
      synchronized (CompletionRegistry.class) {
        if (instance == null) {
          instance = new CompletionRegistry();
        }
      }
    }
    return instance;
  }

  /**
   * Registers a completion engine for its supported languages.
   *
   * @param engine the engine to register
   */
  public void register(CompletionEngine engine) {
    if (engine == null) return;

    allEngines.add(engine);
    for (String lang : engine.supportedLanguages()) {
      String key = lang.toLowerCase();
      CompletionEngine existing = enginesByLanguage.put(key, engine);
      if (existing != null && existing != engine) {
        LOG.info("Replaced completion engine for '{}': {} -> {}",
            lang, existing.name(), engine.name());
      } else {
        LOG.debug("Registered completion engine for '{}': {}", lang, engine.name());
      }
    }
  }

  /**
   * Unregisters a completion engine.
   *
   * @param engine the engine to unregister
   */
  public void unregister(CompletionEngine engine) {
    if (engine == null) return;

    allEngines.remove(engine);
    for (String lang : engine.supportedLanguages()) {
      enginesByLanguage.remove(lang.toLowerCase(), engine);
    }
  }

  /**
   * Returns the completion engine for the given language.
   *
   * @param language the language identifier (e.g., "groovy", "sql", "javascript")
   * @return the engine, or null if no engine is registered for this language
   */
  public CompletionEngine getEngine(String language) {
    if (language == null) return null;
    return enginesByLanguage.get(language.toLowerCase());
  }

  /**
   * Returns completion items for the given context and language.
   * Convenience method that looks up the engine and delegates.
   *
   * @param language the language identifier
   * @param context  the completion context
   * @return list of completion items, or empty list if no engine found
   */
  public List<CompletionItem> complete(String language, CompletionContext context) {
    CompletionEngine engine = getEngine(language);
    if (engine == null) {
      LOG.debug("No completion engine registered for language: {}", language);
      return List.of();
    }
    return engine.complete(context);
  }

  /**
   * Invalidates all cached data in all registered engines.
   * Should be called when the classpath changes.
   */
  public void invalidateAll() {
    LOG.debug("Invalidating all completion engine caches");
    for (CompletionEngine engine : allEngines) {
      try {
        engine.invalidateCache();
      } catch (Exception e) {
        LOG.warn("Failed to invalidate cache for engine: {}", engine.name(), e);
      }
    }
    ClasspathScanner.getInstance().invalidateAll();
  }

  /**
   * Returns true if an engine is registered for the given language.
   */
  public boolean hasEngine(String language) {
    return language != null && enginesByLanguage.containsKey(language.toLowerCase());
  }

  /**
   * Returns all supported languages.
   */
  public Set<String> supportedLanguages() {
    return Set.copyOf(enginesByLanguage.keySet());
  }
}
