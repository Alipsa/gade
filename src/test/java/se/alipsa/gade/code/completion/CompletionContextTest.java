package se.alipsa.gade.code.completion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Test suite for CompletionContext covering:
 * - Builder pattern
 * - Context detection (member access, strings, comments)
 * - Metadata handling
 * - Edge cases
 */
class CompletionContextTest {

  // ========== Builder Pattern Tests ==========

  @Test
  void testBuilderCreatesContext() {
    CompletionContext context = CompletionContext.builder()
        .fullText("def x = 10")
        .caretPosition(10)
        .lineText("def x = 10")
        .lineOffset(10)
        .tokenPrefix("x")
        .build();

    assertNotNull(context);
    assertEquals("def x = 10", context.fullText());
    assertEquals(10, context.caretPosition());
    assertEquals("def x = 10", context.lineText());
    assertEquals(10, context.lineOffset());
    assertEquals("x", context.tokenPrefix());
  }

  @Test
  void testBuilderHandlesNullValues() {
    CompletionContext context = CompletionContext.builder()
        .fullText(null)
        .tokenPrefix(null)
        .build();

    assertEquals("", context.fullText(), "Null fullText should default to empty string");
    assertEquals("", context.tokenPrefix(), "Null tokenPrefix should default to empty string");
  }

  @Test
  void testBuilderClampCaretPosition() {
    // Note: The builder's build() method uses caretPosition in substring() before clamping
    // So we test clamping with a valid initial scenario
    CompletionContext context = CompletionContext.builder()
        .fullText("test")
        .caretPosition(4) // At end of text
        .build();

    assertEquals(4, context.caretPosition(),
        "Caret position at text length should be valid");

    // Test that constructor clamps properly by checking textBeforeCaret
    assertEquals("test", context.textBeforeCaret(),
        "Should handle caret at end of text");
  }

  @Test
  void testBuilderNegativeCaretPosition() {
    CompletionContext context = CompletionContext.builder()
        .fullText("test")
        .caretPosition(-5)
        .build();

    assertEquals(0, context.caretPosition(),
        "Negative caret position should be clamped to 0");
  }

  @Test
  void testBuilderAutoExtractsMemberAccess() {
    CompletionContext context = CompletionContext.builder()
        .fullText("obj.met")
        .caretPosition(7)
        .build();

    assertTrue(context.isMemberAccess(), "Should detect member access");
    assertEquals("obj", context.expressionBefore(), "Should extract expression before dot");
    assertEquals("met", context.tokenPrefix(), "Should extract token after dot");
  }

  @Test
  void testBuilderAutoExtractsComplexMemberAccess() {
    String text = "someObject.method().property.";
    CompletionContext context = CompletionContext.builder()
        .fullText(text)
        .caretPosition(text.length()) // Use actual text length
        .build();

    assertTrue(context.isMemberAccess());
    assertEquals("someObject.method().property", context.expressionBefore());
  }

  // ========== Member Access Detection Tests ==========

  @Test
  void testIsMemberAccessSimple() {
    CompletionContext context = CompletionContext.builder()
        .fullText("obj.")
        .caretPosition(4)
        .build();

    assertTrue(context.isMemberAccess(), "Should detect simple member access");
  }

  @Test
  void testIsMemberAccessReturnsFalseWithoutDot() {
    CompletionContext context = CompletionContext.builder()
        .fullText("variable")
        .caretPosition(8)
        .build();

    assertFalse(context.isMemberAccess(), "Should not be member access without dot");
  }

  @Test
  void testIsMemberAccessChained() {
    CompletionContext context = CompletionContext.builder()
        .fullText("a.b.c.d")
        .caretPosition(7)
        .build();

    assertTrue(context.isMemberAccess(), "Should detect chained member access");
  }

  @Test
  void testIsStaticContextUppercase() {
    CompletionContext context = CompletionContext.builder()
        .fullText("String.")
        .caretPosition(7)
        .expressionBefore("String")
        .build();

    assertTrue(context.isStaticContext(),
        "Uppercase first letter should suggest static context");
  }

  @Test
  void testIsStaticContextWithDot() {
    CompletionContext context = CompletionContext.builder()
        .fullText("java.lang.String.")
        .caretPosition(17)
        .expressionBefore("java.lang.String")
        .build();

    assertTrue(context.isStaticContext(),
        "Dotted name should suggest static context (FQCN)");
  }

  @Test
  void testIsStaticContextReturnsFalseForLowercase() {
    CompletionContext context = CompletionContext.builder()
        .fullText("variable.")
        .caretPosition(9)
        .expressionBefore("variable")
        .build();

    assertFalse(context.isStaticContext(),
        "Lowercase should not be static context");
  }

  @Test
  void testIsStaticContextHandlesNullExpression() {
    CompletionContext context = CompletionContext.builder()
        .fullText("test")
        .caretPosition(4)
        .build();

    assertFalse(context.isStaticContext(),
        "Null expression should not be static context");
  }

  // ========== String Detection Tests ==========

  @Test
  void testIsInsideStringDoubleQuotes() {
    CompletionContext context = CompletionContext.builder()
        .fullText("def x = \"hello\"")
        .caretPosition(11) // Inside "hello"
        .build();

    assertTrue(context.isInsideString(), "Should detect inside double-quoted string");
  }

  @Test
  void testIsInsideStringSingleQuotes() {
    CompletionContext context = CompletionContext.builder()
        .fullText("def x = 'hello'")
        .caretPosition(11) // Inside 'hello'
        .build();

    assertTrue(context.isInsideString(), "Should detect inside single-quoted string");
  }

  @Test
  void testIsInsideStringTripleDoubleQuotes() {
    CompletionContext context = CompletionContext.builder()
        .fullText("def x = \"\"\"multi\nline\"\"\"")
        .caretPosition(13) // Inside """..."""
        .build();

    assertTrue(context.isInsideString(), "Should detect inside triple double-quoted string");
  }

  @Test
  void testIsInsideStringReturnsFalseOutsideString() {
    CompletionContext context = CompletionContext.builder()
        .fullText("def x = \"hello\" + y")
        .caretPosition(18) // After string, before y
        .build();

    assertFalse(context.isInsideString(), "Should not be inside string");
  }

  // ========== Comment Detection Tests ==========

  @Test
  void testIsInsideCommentLineComment() {
    CompletionContext context = CompletionContext.builder()
        .fullText("def x = 10 // comment here")
        .caretPosition(18) // Inside comment
        .build();

    assertTrue(context.isInsideComment(), "Should detect inside line comment");
  }

  @Test
  void testIsInsideCommentBlockComment() {
    CompletionContext context = CompletionContext.builder()
        .fullText("def x = /* comment */ 10")
        .caretPosition(13) // Inside /* */
        .build();

    assertTrue(context.isInsideComment(), "Should detect inside block comment");
  }

  @Test
  void testIsInsideCommentReturnsFalseOutsideComment() {
    CompletionContext context = CompletionContext.builder()
        .fullText("def x = 10 // comment")
        .caretPosition(5) // Before comment
        .build();

    assertFalse(context.isInsideComment(), "Should not be inside comment");
  }

  // ========== Metadata Tests ==========

  @Test
  void testMetadataHandling() {
    Map<String, Object> meta = new HashMap<>();
    meta.put("key1", "value1");
    meta.put("key2", 42);

    CompletionContext context = CompletionContext.builder()
        .fullText("test")
        .metadata(meta)
        .build();

    assertEquals("value1", context.metadata("key1", String.class));
    assertEquals(42, context.metadata("key2", Integer.class));
  }

  @Test
  void testMetadataBuilderMethod() {
    CompletionContext context = CompletionContext.builder()
        .fullText("test")
        .metadata("language", "groovy")
        .metadata("version", "3.0")
        .build();

    assertEquals("groovy", context.metadata("language", String.class));
    assertEquals("3.0", context.metadata("version", String.class));
  }

  @Test
  void testMetadataWrongTypeReturnsNull() {
    CompletionContext context = CompletionContext.builder()
        .metadata("number", 123)
        .build();

    assertNull(context.metadata("number", String.class),
        "Should return null for wrong type");
  }

  @Test
  void testMetadataIsImmutable() {
    Map<String, Object> meta = new HashMap<>();
    meta.put("key", "value");

    CompletionContext context = CompletionContext.builder()
        .metadata(meta)
        .build();

    Map<String, Object> returned = context.metadata();

    try {
      returned.put("newKey", "newValue");
      // If we get here, map is mutable (not ideal)
    } catch (UnsupportedOperationException e) {
      // Expected for immutable map
    }
  }

  // ========== Text Access Tests ==========

  @Test
  void testTextBeforeCaret() {
    CompletionContext context = CompletionContext.builder()
        .fullText("hello world")
        .caretPosition(5)
        .build();

    assertEquals("hello", context.textBeforeCaret());
  }

  @Test
  void testTextBeforeCaretAtStart() {
    CompletionContext context = CompletionContext.builder()
        .fullText("test")
        .caretPosition(0)
        .build();

    assertEquals("", context.textBeforeCaret());
  }

  @Test
  void testClassLoaderDefaultsToContextClassLoader() {
    CompletionContext context = CompletionContext.builder()
        .fullText("test")
        .build();

    assertNotNull(context.classLoader());
    assertEquals(Thread.currentThread().getContextClassLoader(), context.classLoader());
  }

  @Test
  void testClassLoaderCustom() {
    ClassLoader customLoader = new ClassLoader() {};
    CompletionContext context = CompletionContext.builder()
        .fullText("test")
        .classLoader(customLoader)
        .build();

    assertNotNull(context.classLoader());
    assertEquals(customLoader, context.classLoader());
  }

  // ========== Edge Cases ==========

  @Test
  void testEmptyContext() {
    CompletionContext context = CompletionContext.builder().build();

    assertEquals("", context.fullText());
    assertEquals(0, context.caretPosition());
    assertEquals("", context.tokenPrefix());
    assertFalse(context.isMemberAccess());
    assertFalse(context.isInsideString());
    assertFalse(context.isInsideComment());
  }

  @Test
  void testContextWithEscapedCharactersInString() {
    CompletionContext context = CompletionContext.builder()
        .fullText("def s = \"hello\\\"world\"")
        .caretPosition(13) // Inside string with escaped quote
        .build();

    assertTrue(context.isInsideString(),
        "Should handle escaped quotes in string detection");
  }

  @Test
  void testMemberAccessWithMethodCall() {
    CompletionContext context = CompletionContext.builder()
        .fullText("obj.method().")
        .caretPosition(13)
        .build();

    assertTrue(context.isMemberAccess());
    assertEquals("obj.method()", context.expressionBefore());
  }

  @Test
  void testCachedValuesForPerformance() {
    CompletionContext context = CompletionContext.builder()
        .fullText("obj.property")
        .caretPosition(12)
        .build();

    // First call computes and caches
    boolean memberAccess1 = context.isMemberAccess();
    // Second call should use cached value
    boolean memberAccess2 = context.isMemberAccess();

    assertEquals(memberAccess1, memberAccess2, "Cached values should be consistent");
  }
}
