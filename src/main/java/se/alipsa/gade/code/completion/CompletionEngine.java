package se.alipsa.gade.code.completion;

import java.util.List;
import java.util.Set;

/**
 * Interface for language-specific code completion engines.
 * Each language (Groovy, SQL, JavaScript, etc.) implements this interface
 * to provide contextual code completion suggestions.
 */
public interface CompletionEngine {

  /**
   * Returns completions for the given context.
   *
   * @param context the completion context containing text, caret position,
   *                classloader, and other relevant information
   * @return a list of completion items, sorted by relevance (most relevant first)
   */
  List<CompletionItem> complete(CompletionContext context);

  /**
   * Returns the language identifiers this engine supports.
   * These should match the language names used in the editor
   * (e.g., "groovy", "sql", "javascript").
   *
   * @return set of supported language identifiers
   */
  Set<String> supportedLanguages();

  /**
   * Called when the classpath changes (e.g., new dependencies added,
   * @Grab processed, project dependencies updated).
   * Engines should invalidate any cached class information.
   */
  default void invalidateCache() {
    // Default implementation does nothing
  }

  /**
   * Returns a human-readable name for this engine.
   * Used for logging and debugging purposes.
   *
   * @return engine name
   */
  default String name() {
    return getClass().getSimpleName();
  }
}
