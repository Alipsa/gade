package se.alipsa.gade.code.completion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Test suite for CompletionItem covering:
 * - Simple constructors
 * - Builder pattern
 * - Field accessors
 * - Label rendering
 * - All completion kinds
 */
class CompletionItemTest {

  // ========== Constructor Tests ==========

  @Test
  void testSimpleConstructor() {
    CompletionItem item = new CompletionItem("test", CompletionItem.Kind.KEYWORD);

    assertEquals("test", item.completion());
    assertEquals("test", item.display());
    assertEquals(CompletionItem.Kind.KEYWORD, item.kind());
    assertEquals("test", item.insertText(), "Should default to completion text");
    assertEquals(100, item.sortPriority(), "Should have default priority");
    assertEquals(0, item.cursorOffset(), "Should have default cursor offset");
  }

  @Test
  void testConstructorWithDisplay() {
    CompletionItem item = new CompletionItem("println", "println() - Print to console", CompletionItem.Kind.METHOD);

    assertEquals("println", item.completion());
    assertEquals("println() - Print to console", item.display());
    assertEquals(CompletionItem.Kind.METHOD, item.kind());
  }

  @Test
  void testFullConstructor() {
    CompletionItem item = new CompletionItem(
        "method",
        "method(String arg)",
        CompletionItem.Kind.METHOD,
        "Returns: void",
        "method(${1:arg})",
        50,
        -1
    );

    assertEquals("method", item.completion());
    assertEquals("method(String arg)", item.display());
    assertEquals(CompletionItem.Kind.METHOD, item.kind());
    assertEquals("Returns: void", item.detail());
    assertEquals("method(${1:arg})", item.insertText());
    assertEquals(50, item.sortPriority());
    assertEquals(-1, item.cursorOffset());
  }

  // ========== Builder Pattern Tests ==========

  @Test
  void testBuilder() {
    CompletionItem item = CompletionItem.builder()
        .completion("forEach")
        .display("forEach(callback)")
        .kind(CompletionItem.Kind.METHOD)
        .detail("Array method")
        .insertText("forEach()")
        .sortPriority(10)
        .cursorOffset(-1)
        .build();

    assertEquals("forEach", item.completion());
    assertEquals("forEach(callback)", item.display());
    assertEquals(CompletionItem.Kind.METHOD, item.kind());
    assertEquals("Array method", item.detail());
    assertEquals("forEach()", item.insertText());
    assertEquals(10, item.sortPriority());
    assertEquals(-1, item.cursorOffset());
  }

  @Test
  void testBuilderDefaultsDisplayToCompletion() {
    CompletionItem item = CompletionItem.builder()
        .completion("test")
        .build();

    assertEquals("test", item.display(),
        "Display should default to completion if not set");
  }

  @Test
  void testBuilderDefaultKind() {
    CompletionItem item = CompletionItem.builder()
        .completion("test")
        .build();

    assertEquals(CompletionItem.Kind.KEYWORD, item.kind(),
        "Should default to KEYWORD kind");
  }

  @Test
  void testBuilderDefaultPriority() {
    CompletionItem item = CompletionItem.builder()
        .completion("test")
        .build();

    assertEquals(100, item.sortPriority(),
        "Should have default priority of 100");
  }

  // ========== InsertText Tests ==========

  @Test
  void testInsertTextDefaultsToCompletion() {
    CompletionItem item = new CompletionItem("variable", CompletionItem.Kind.VARIABLE);

    assertEquals("variable", item.insertText(),
        "InsertText should default to completion");
  }

  @Test
  void testInsertTextCustom() {
    CompletionItem item = CompletionItem.builder()
        .completion("if")
        .insertText("if () {\n\t\n}")
        .kind(CompletionItem.Kind.SNIPPET)
        .build();

    assertEquals("if () {\n\t\n}", item.insertText(),
        "Should use custom insertText");
  }

  // ========== Label Rendering Tests ==========

  @Test
  void testRenderLabelKeyword() {
    CompletionItem item = new CompletionItem("while", CompletionItem.Kind.KEYWORD);
    String label = item.renderLabel();

    assertTrue(label.contains("while"), "Should contain completion text");
    assertTrue(label.contains("keyword"), "Should contain kind label");
  }

  @Test
  void testRenderLabelMethod() {
    CompletionItem item = new CompletionItem(
        "toString",
        "toString()",
        CompletionItem.Kind.METHOD
    );
    String label = item.renderLabel();

    assertTrue(label.contains("toString()"), "Should use display for method");
    assertTrue(label.contains("method"), "Should contain method label");
  }

  @Test
  void testRenderLabelWithDetail() {
    CompletionItem item = CompletionItem.builder()
        .completion("String")
        .display("String")
        .kind(CompletionItem.Kind.CLASS)
        .detail("java.lang")
        .build();
    String label = item.renderLabel();

    assertTrue(label.contains("String"), "Should contain class name");
    assertTrue(label.contains("java.lang"), "Should contain detail");
    assertTrue(label.contains("class"), "Should contain kind");
  }

  @Test
  void testRenderLabelField() {
    CompletionItem item = CompletionItem.builder()
        .completion("name")
        .display("name: String")
        .kind(CompletionItem.Kind.FIELD)
        .detail("java.lang.String")
        .build();
    String label = item.renderLabel();

    assertTrue(label.contains("name"), "Should contain field name");
    assertTrue(label.contains("field"), "Should indicate field type");
  }

  // ========== All Completion Kinds Tests ==========

  @Test
  void testAllCompletionKinds() {
    CompletionItem.Kind[] allKinds = {
        CompletionItem.Kind.KEYWORD,
        CompletionItem.Kind.CLASS,
        CompletionItem.Kind.METHOD,
        CompletionItem.Kind.FIELD,
        CompletionItem.Kind.FUNCTION,
        CompletionItem.Kind.TABLE,
        CompletionItem.Kind.COLUMN,
        CompletionItem.Kind.SNIPPET,
        CompletionItem.Kind.VARIABLE,
        CompletionItem.Kind.PARAMETER,
        CompletionItem.Kind.PROPERTY,
        CompletionItem.Kind.CONSTANT,
        CompletionItem.Kind.INTERFACE,
        CompletionItem.Kind.ENUM,
        CompletionItem.Kind.MODULE
    };

    for (CompletionItem.Kind kind : allKinds) {
      CompletionItem item = new CompletionItem("test", kind);
      assertNotNull(item.renderLabel(),
          "Kind " + kind + " should render label");
      assertEquals(kind, item.kind());
    }
  }

  @Test
  void testTableKind() {
    CompletionItem item = new CompletionItem("users", CompletionItem.Kind.TABLE);
    String label = item.renderLabel();

    assertTrue(label.contains("table"), "Should indicate table type");
  }

  @Test
  void testColumnKind() {
    CompletionItem item = CompletionItem.builder()
        .completion("user_id")
        .display("user_id")
        .kind(CompletionItem.Kind.COLUMN)
        .detail("INTEGER")
        .build();
    String label = item.renderLabel();

    assertTrue(label.contains("column"), "Should indicate column type");
    assertTrue(label.contains("INTEGER"), "Should include data type");
  }

  @Test
  void testSnippetKind() {
    CompletionItem item = CompletionItem.builder()
        .completion("for")
        .display("for (int i = 0; i < n; i++)")
        .kind(CompletionItem.Kind.SNIPPET)
        .insertText("for (int ${1:i} = 0; $1 < ${2:n}; $1++) {\n\t$0\n}")
        .build();

    assertEquals(CompletionItem.Kind.SNIPPET, item.kind());
    assertTrue(item.insertText().contains("${1:i}"),
        "Snippet should contain placeholders");
  }

  @Test
  void testInterfaceKind() {
    CompletionItem item = new CompletionItem("Runnable", CompletionItem.Kind.INTERFACE);
    String label = item.renderLabel();

    assertTrue(label.contains("interface"), "Should indicate interface type");
  }

  @Test
  void testEnumKind() {
    CompletionItem item = new CompletionItem("DayOfWeek", CompletionItem.Kind.ENUM);
    String label = item.renderLabel();

    assertTrue(label.contains("enum"), "Should indicate enum type");
  }

  @Test
  void testModuleKind() {
    CompletionItem item = new CompletionItem("java.util", CompletionItem.Kind.MODULE);
    String label = item.renderLabel();

    assertTrue(label.contains("module"), "Should indicate module type");
  }

  // ========== Sort Priority Tests ==========

  @Test
  void testSortPriorityComparison() {
    CompletionItem highPriority = CompletionItem.builder()
        .completion("important")
        .sortPriority(1)
        .build();

    CompletionItem lowPriority = CompletionItem.builder()
        .completion("lessImportant")
        .sortPriority(100)
        .build();

    assertTrue(highPriority.sortPriority() < lowPriority.sortPriority(),
        "Lower priority value should come first");
  }

  // ========== Cursor Offset Tests ==========

  @Test
  void testCursorOffsetForMethodWithParens() {
    CompletionItem item = CompletionItem.builder()
        .completion("method")
        .insertText("method()")
        .cursorOffset(-1) // Place cursor inside parens
        .build();

    assertEquals(-1, item.cursorOffset(),
        "Should position cursor inside parentheses");
  }

  @Test
  void testCursorOffsetAtEnd() {
    CompletionItem item = CompletionItem.builder()
        .completion("variable")
        .cursorOffset(0) // Cursor at end
        .build();

    assertEquals(0, item.cursorOffset(),
        "Cursor offset 0 means cursor at end");
  }

  // ========== Edge Cases ==========

  @Test
  void testEmptyDetail() {
    CompletionItem item = CompletionItem.builder()
        .completion("test")
        .display("test")
        .kind(CompletionItem.Kind.VARIABLE)
        .detail("")
        .build();
    String label = item.renderLabel();

    assertNotNull(label);
    assertTrue(label.contains("variable"), "Should still render without detail");
  }

  @Test
  void testNullDetail() {
    CompletionItem item = CompletionItem.builder()
        .completion("test")
        .display("test")
        .kind(CompletionItem.Kind.CLASS)
        .detail(null)
        .build();
    String label = item.renderLabel();

    assertNotNull(label);
    assertTrue(label.contains("test"), "Should render without detail");
  }

  @Test
  void testBuilderChaining() {
    CompletionItem item = CompletionItem.builder()
        .completion("test")
        .kind(CompletionItem.Kind.METHOD)
        .detail("detail")
        .sortPriority(50)
        .build();

    assertNotNull(item);
    assertEquals("test", item.completion());
    assertEquals(CompletionItem.Kind.METHOD, item.kind());
    assertEquals("detail", item.detail());
    assertEquals(50, item.sortPriority());
  }

  @Test
  void testPropertyKind() {
    CompletionItem item = new CompletionItem("length", CompletionItem.Kind.PROPERTY);
    String label = item.renderLabel();

    assertTrue(label.contains("property"), "Should indicate property type");
  }

  @Test
  void testParameterKind() {
    CompletionItem item = new CompletionItem("arg", CompletionItem.Kind.PARAMETER);
    String label = item.renderLabel();

    assertTrue(label.contains("parameter"), "Should indicate parameter type");
  }

  @Test
  void testConstantKind() {
    CompletionItem item = new CompletionItem("MAX_VALUE", CompletionItem.Kind.CONSTANT);
    String label = item.renderLabel();

    assertTrue(label.contains("constant"), "Should indicate constant type");
  }

  @Test
  void testFunctionKind() {
    CompletionItem item = new CompletionItem("map", CompletionItem.Kind.FUNCTION);
    String label = item.renderLabel();

    assertTrue(label.contains("function"), "Should indicate function type");
  }

  @Test
  void testVariableKind() {
    CompletionItem item = new CompletionItem("count", CompletionItem.Kind.VARIABLE);
    String label = item.renderLabel();

    assertTrue(label.contains("variable"), "Should indicate variable type");
  }
}
