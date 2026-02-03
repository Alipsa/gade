package se.alipsa.gade.code.completion;

import java.util.List;
import java.util.Set;

/**
 * Interface for language-specific code completion engines.
 * <p>
 * Each language (Groovy, SQL, JavaScript, etc.) implements this interface
 * to provide contextual code completion suggestions. Implementations are
 * registered with {@link CompletionRegistry} and invoked when the user
 * requests completions (typically via Ctrl+Space).
 * </p>
 *
 * <h2>Implementation Example</h2>
 * <pre>{@code
 * public class MyLanguageCompletionEngine implements CompletionEngine {
 *
 *     @Override
 *     public List<CompletionItem> complete(CompletionContext context) {
 *         List<CompletionItem> items = new ArrayList<>();
 *
 *         // Get text before cursor
 *         String beforeCaret = context.textBeforeCaret();
 *
 *         // Check if completing member access (e.g., "object.")
 *         if (context.isMemberAccess()) {
 *             String objectName = extractObjectName(beforeCaret);
 *             Class<?> objectClass = resolveType(objectName, context.classLoader());
 *
 *             // Add method completions
 *             for (Method method : objectClass.getMethods()) {
 *                 items.add(CompletionItem.builder()
 *                     .label(method.getName())
 *                     .kind(CompletionKind.METHOD)
 *                     .detail(method.getReturnType().getSimpleName())
 *                     .insertText(method.getName() + "()")
 *                     .cursorOffset(-1)  // Place cursor inside parens
 *                     .build());
 *             }
 *         }
 *
 *         // Add keyword completions
 *         if (shouldShowKeywords(context)) {
 *             items.add(CompletionItem.keyword("if"));
 *             items.add(CompletionItem.keyword("for"));
 *             items.add(CompletionItem.keyword("while"));
 *         }
 *
 *         return items;
 *     }
 *
 *     @Override
 *     public Set<String> supportedLanguages() {
 *         return Set.of("mylang");
 *     }
 *
 *     @Override
 *     public void invalidateCache() {
 *         // Clear any cached type information
 *         typeCache.clear();
 *     }
 * }
 * }</pre>
 *
 * <h2>Registration</h2>
 * <p>
 * Register your engine on application startup:
 * </p>
 * <pre>{@code
 * CompletionRegistry registry = CompletionRegistry.getInstance();
 * registry.register(new MyLanguageCompletionEngine());
 * }</pre>
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li>Target response time: &lt;100ms for user-facing completion</li>
 *   <li>Use caching for expensive operations (class scanning, parsing)</li>
 *   <li>Invalidate caches when {@link #invalidateCache()} is called</li>
 *   <li>Return early if context is inside string or comment (check {@link CompletionContext#isInsideString()})</li>
 * </ul>
 *
 * @see CompletionRegistry
 * @see CompletionContext
 * @see CompletionItem
 * @since 1.0.0
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
   * Grab annotations processed, project dependencies updated).
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
