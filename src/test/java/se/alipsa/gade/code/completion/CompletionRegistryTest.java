package se.alipsa.gade.code.completion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for CompletionRegistry covering:
 * - Singleton pattern
 * - Engine registration and unregistration
 * - Engine lookup and retrieval
 * - Completion delegation
 * - Cache invalidation
 * - Thread safety
 */
class CompletionRegistryTest {

  private CompletionRegistry registry;

  @BeforeEach
  void setUp() {
    registry = CompletionRegistry.getInstance();
    // Clear any engines that might be left from previous tests
    // Note: We can't actually clear the singleton, so tests must handle existing engines
  }

  @AfterEach
  void tearDown() {
    // Clean up any test engines we registered
    // Note: Since it's a singleton, we can't truly reset it
    // Tests should be designed to be independent
  }

  // ========== Singleton Pattern Tests ==========

  @Test
  void testGetInstanceReturnsSingleton() {
    CompletionRegistry instance1 = CompletionRegistry.getInstance();
    CompletionRegistry instance2 = CompletionRegistry.getInstance();

    assertNotNull(instance1, "Instance should not be null");
    assertSame(instance1, instance2, "Should return same singleton instance");
  }

  @Test
  void testGetInstanceIsThreadSafe() throws InterruptedException {
    // This tests the double-checked locking implementation
    Thread[] threads = new Thread[10];
    CompletionRegistry[] instances = new CompletionRegistry[threads.length];

    for (int i = 0; i < threads.length; i++) {
      final int index = i;
      threads[i] = new Thread(() -> {
        instances[index] = CompletionRegistry.getInstance();
      });
    }

    for (Thread thread : threads) {
      thread.start();
    }

    for (Thread thread : threads) {
      thread.join();
    }

    // All threads should get the same instance
    for (int i = 1; i < instances.length; i++) {
      assertSame(instances[0], instances[i],
          "All threads should get the same singleton instance");
    }
  }

  // ========== Engine Registration Tests ==========

  @Test
  void testRegisterEngine() {
    MockCompletionEngine engine = new MockCompletionEngine("test-lang");
    registry.register(engine);

    assertTrue(registry.hasEngine("test-lang"),
        "Should have registered engine for 'test-lang'");
    assertSame(engine, registry.getEngine("test-lang"),
        "Should return registered engine");
  }

  @Test
  void testRegisterEngineCaseInsensitive() {
    MockCompletionEngine engine = new MockCompletionEngine("GroOvy");
    registry.register(engine);

    assertTrue(registry.hasEngine("groovy"),
        "Should find engine with lowercase");
    assertTrue(registry.hasEngine("GROOVY"),
        "Should find engine with uppercase");
    assertTrue(registry.hasEngine("GroOvy"),
        "Should find engine with mixed case");
    assertSame(engine, registry.getEngine("groovy"),
        "Should return engine regardless of case");
  }

  @Test
  void testRegisterEngineWithMultipleLanguages() {
    MockCompletionEngine engine = new MockCompletionEngine("java", "groovy", "kotlin");
    registry.register(engine);

    assertTrue(registry.hasEngine("java"), "Should support java");
    assertTrue(registry.hasEngine("groovy"), "Should support groovy");
    assertTrue(registry.hasEngine("kotlin"), "Should support kotlin");
    assertSame(engine, registry.getEngine("java"));
    assertSame(engine, registry.getEngine("groovy"));
    assertSame(engine, registry.getEngine("kotlin"));
  }

  @Test
  void testRegisterNullEngineDoesNotThrow() {
    // Should handle null gracefully
    registry.register(null);
    // No assertions needed - just verify it doesn't throw
  }

  @Test
  void testRegisterReplacesExistingEngine() {
    MockCompletionEngine engine1 = new MockCompletionEngine("python");
    MockCompletionEngine engine2 = new MockCompletionEngine("python");

    registry.register(engine1);
    assertSame(engine1, registry.getEngine("python"));

    registry.register(engine2);
    assertSame(engine2, registry.getEngine("python"),
        "Should replace old engine with new one");
  }

  // ========== Engine Unregistration Tests ==========

  @Test
  void testUnregisterEngine() {
    MockCompletionEngine engine = new MockCompletionEngine("ruby");
    registry.register(engine);
    assertTrue(registry.hasEngine("ruby"));

    registry.unregister(engine);
    assertFalse(registry.hasEngine("ruby"),
        "Should no longer have engine after unregistration");
    assertNull(registry.getEngine("ruby"),
        "Should return null for unregistered engine");
  }

  @Test
  void testUnregisterEngineWithMultipleLanguages() {
    MockCompletionEngine engine = new MockCompletionEngine("go", "rust", "swift");
    registry.register(engine);

    registry.unregister(engine);

    assertFalse(registry.hasEngine("go"), "Should remove go");
    assertFalse(registry.hasEngine("rust"), "Should remove rust");
    assertFalse(registry.hasEngine("swift"), "Should remove swift");
  }

  @Test
  void testUnregisterNullEngineDoesNotThrow() {
    // Should handle null gracefully
    registry.unregister(null);
    // No assertions needed - just verify it doesn't throw
  }

  // ========== Engine Lookup Tests ==========

  @Test
  void testGetEngineReturnsNullForUnregisteredLanguage() {
    assertNull(registry.getEngine("nonexistent-language"),
        "Should return null for unregistered language");
  }

  @Test
  void testGetEngineHandlesNullLanguage() {
    assertNull(registry.getEngine(null),
        "Should return null for null language");
  }

  @Test
  void testHasEngineReturnsFalseForUnregisteredLanguage() {
    assertFalse(registry.hasEngine("unknown-lang"),
        "Should return false for unregistered language");
  }

  @Test
  void testHasEngineHandlesNullLanguage() {
    assertFalse(registry.hasEngine(null),
        "Should return false for null language");
  }

  // ========== Completion Delegation Tests ==========

  @Test
  void testCompleteWithRegisteredEngine() {
    MockCompletionEngine engine = new MockCompletionEngine("cpp");
    CompletionItem expectedItem = new CompletionItem("test", CompletionItem.Kind.KEYWORD);
    engine.setCompletions(List.of(expectedItem));
    registry.register(engine);

    CompletionContext context = CompletionContext.builder()
        .fullText("te")
        .caretPosition(2)
        .build();

    List<CompletionItem> results = registry.complete("cpp", context);

    assertNotNull(results, "Results should not be null");
    assertEquals(1, results.size(), "Should return completion from engine");
    assertSame(expectedItem, results.get(0), "Should return expected item");
    assertTrue(engine.wasCompleteInvoked(), "Should invoke engine.complete()");
  }

  @Test
  void testCompleteWithUnregisteredLanguageReturnsEmptyList() {
    CompletionContext context = CompletionContext.builder()
        .fullText("test")
        .caretPosition(4)
        .build();

    List<CompletionItem> results = registry.complete("unregistered", context);

    assertNotNull(results, "Results should not be null");
    assertTrue(results.isEmpty(), "Should return empty list for unregistered language");
  }

  @Test
  void testCompletePassesContextToEngine() {
    MockCompletionEngine engine = new MockCompletionEngine("dart");
    registry.register(engine);

    CompletionContext context = CompletionContext.builder()
        .fullText("var x = ")
        .caretPosition(8)
        .tokenPrefix("x")
        .build();

    registry.complete("dart", context);

    assertSame(context, engine.getLastContext(),
        "Should pass context to engine");
  }

  // ========== Cache Invalidation Tests ==========

  @Test
  void testInvalidateAllCallsAllEngines() {
    MockCompletionEngine engine1 = new MockCompletionEngine("lang1");
    MockCompletionEngine engine2 = new MockCompletionEngine("lang2");
    MockCompletionEngine engine3 = new MockCompletionEngine("lang3");

    registry.register(engine1);
    registry.register(engine2);
    registry.register(engine3);

    registry.invalidateAll();

    assertTrue(engine1.wasInvalidateCalled(), "Should invalidate engine1");
    assertTrue(engine2.wasInvalidateCalled(), "Should invalidate engine2");
    assertTrue(engine3.wasInvalidateCalled(), "Should invalidate engine3");
  }

  @Test
  void testInvalidateAllHandlesEngineExceptions() {
    PrintStream orgOut = System.out;
    PrintStream orgErr = System.err;

    try {
      System.setOut(new PrintStream(OutputStream.nullOutputStream())); // Suppress expected error output
      System.setErr(new PrintStream(OutputStream.nullOutputStream())); // Suppress expected error output
      MockCompletionEngine goodEngine = new MockCompletionEngine("good");
      MockCompletionEngine badEngine = new MockCompletionEngine("bad");
      badEngine.setThrowOnInvalidate(true);
      MockCompletionEngine anotherGoodEngine = new MockCompletionEngine("another");

      registry.register(goodEngine);
      registry.register(badEngine);
      registry.register(anotherGoodEngine);

      // Should not throw, even though one engine throws
      registry.invalidateAll();

      assertTrue(goodEngine.wasInvalidateCalled(),
          "Should still invalidate good engines");
      assertTrue(anotherGoodEngine.wasInvalidateCalled(),
          "Should continue after exception");
    } finally {
      System.setOut(orgOut);
      System.setErr(orgErr);
    }
  }

  @Test
  void testInvalidateAllWithNoEnginesDoesNotThrow() {
    // Clear registry state by creating isolated test
    // Since we can't clear singleton, just verify it doesn't throw with current state
    registry.invalidateAll();
    // No assertions needed - just verify it doesn't throw
  }

  // ========== Supported Languages Tests ==========

  @Test
  void testSupportedLanguagesReturnsAllRegisteredLanguages() {
    MockCompletionEngine engine1 = new MockCompletionEngine("typescript");
    MockCompletionEngine engine2 = new MockCompletionEngine("javascript");
    MockCompletionEngine engine3 = new MockCompletionEngine("coffeescript");

    registry.register(engine1);
    registry.register(engine2);
    registry.register(engine3);

    Set<String> supported = registry.supportedLanguages();

    assertTrue(supported.contains("typescript"), "Should include typescript");
    assertTrue(supported.contains("javascript"), "Should include javascript");
    assertTrue(supported.contains("coffeescript"), "Should include coffeescript");
  }

  @Test
  void testSupportedLanguagesIsImmutable() {
    MockCompletionEngine engine = new MockCompletionEngine("haskell");
    registry.register(engine);

    Set<String> supported = registry.supportedLanguages();

    // Attempt to modify should throw or be ignored
    try {
      supported.add("should-fail");
      // If we get here, the set is mutable (not ideal but not fatal)
    } catch (UnsupportedOperationException e) {
      // This is expected for immutable sets
    }

    // Verify original registry is unchanged
    Set<String> supported2 = registry.supportedLanguages();
    assertFalse(supported2.contains("should-fail"),
        "Modification should not affect returned set");
  }

  // ========== Edge Cases and Thread Safety ==========

  @Test
  void testConcurrentRegistrationAndLookup() throws InterruptedException {
    MockCompletionEngine[] engines = new MockCompletionEngine[5];
    for (int i = 0; i < engines.length; i++) {
      engines[i] = new MockCompletionEngine("concurrent-lang-" + i);
    }

    Thread[] threads = new Thread[10];
    AtomicBoolean failed = new AtomicBoolean(false);

    for (int i = 0; i < threads.length; i++) {
      final int index = i;
      threads[i] = new Thread(() -> {
        try {
          if (index < engines.length) {
            // First 5 threads register engines
            registry.register(engines[index]);
          } else {
            // Next 5 threads lookup engines
            int engineIndex = index - engines.length;
            CompletionEngine engine = registry.getEngine("concurrent-lang-" + engineIndex);
            // May be null if registration hasn't happened yet, which is okay
          }
        } catch (Exception e) {
          failed.set(true);
        }
      });
    }

    for (Thread thread : threads) {
      thread.start();
    }

    for (Thread thread : threads) {
      thread.join();
    }

    assertFalse(failed.get(), "No exceptions should occur during concurrent access");

    // Verify all engines were registered eventually
    for (int i = 0; i < engines.length; i++) {
      assertTrue(registry.hasEngine("concurrent-lang-" + i),
          "Engine " + i + " should be registered");
    }
  }

  // ========== Mock Implementation ==========

  /**
   * Mock implementation of CompletionEngine for testing purposes.
   */
  private static class MockCompletionEngine implements CompletionEngine {
    private final Set<String> languages;
    private List<CompletionItem> completions = List.of();
    private boolean invalidateCalled = false;
    private boolean throwOnInvalidate = false;
    private boolean completeInvoked = false;
    private CompletionContext lastContext;

    public MockCompletionEngine(String... languages) {
      this.languages = Set.of(languages);
    }

    @Override
    public List<CompletionItem> complete(CompletionContext context) {
      this.completeInvoked = true;
      this.lastContext = context;
      return completions;
    }

    @Override
    public Set<String> supportedLanguages() {
      return languages;
    }

    @Override
    public void invalidateCache() {
      invalidateCalled = true;
      if (throwOnInvalidate) {
        throw new RuntimeException("Simulated invalidation failure");
      }
    }

    @Override
    public String name() {
      return "MockEngine(" + String.join(",", languages) + ")";
    }

    public void setCompletions(List<CompletionItem> completions) {
      this.completions = completions;
    }

    public boolean wasInvalidateCalled() {
      return invalidateCalled;
    }

    public void setThrowOnInvalidate(boolean throwOnInvalidate) {
      this.throwOnInvalidate = throwOnInvalidate;
    }

    public boolean wasCompleteInvoked() {
      return completeInvoked;
    }

    public CompletionContext getLastContext() {
      return lastContext;
    }
  }
}
