package se.alipsa.gade.code.completion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.code.completion.groovy.GroovyCompletionEngine;

class GroovyCompletionEngineTest {

  @Test
  void completesMemberAccessOnMethodParameter() {
    String text = String.join("\n",
        "import groovy.transform.CompileStatic",
        "",
        "@CompileStatic",
        "class Test {",
        "  static Number distance(Number dx, Number dy) {",
        "    Math.sqrt(Math.pow(dx.))",
        "  }",
        "}",
        ""
    );

    int caret = text.indexOf("dx.") + "dx.".length();
    CompletionContext ctx = CompletionContext.builder()
        .fullText(text)
        .caretPosition(caret)
        .build();

    List<CompletionItem> items = GroovyCompletionEngine.getInstance().complete(ctx);

    assertFalse(items.isEmpty(), "Expected member completions for method parameter");
    assertTrue(items.stream().anyMatch(item -> "doubleValue".equals(item.completion())),
        "Expected Number methods to be suggested");
  }

  @Test
  void noArgMethodsKeepCursorAfterParens() {
    String text = String.join("\n",
        "class Test {",
        "  static Number distance(Number dx) {",
        "    dx.",
        "  }",
        "}",
        ""
    );

    int caret = text.indexOf("dx.") + "dx.".length();
    CompletionContext ctx = CompletionContext.builder()
        .fullText(text)
        .caretPosition(caret)
        .build();

    List<CompletionItem> items = GroovyCompletionEngine.getInstance().complete(ctx);
    CompletionItem doubleValue = items.stream()
        .filter(item -> "doubleValue".equals(item.completion()))
        .findFirst()
        .orElseThrow();

    assertTrue(doubleValue.insertText().endsWith("()"), "Expected method call insertion");
    assertTrue(doubleValue.cursorOffset() == 0, "Expected cursor after () for no-arg method");
  }

  @Test
  void parameterizedMethodsPlaceCursorInsideParens() {
    String text = String.join("\n",
        "class Test {",
        "  static void demo() {",
        "    def s = \"abc\"",
        "    s.",
        "  }",
        "}",
        ""
    );

    int caret = text.indexOf("s.") + "s.".length();
    CompletionContext ctx = CompletionContext.builder()
        .fullText(text)
        .caretPosition(caret)
        .build();

    List<CompletionItem> items = GroovyCompletionEngine.getInstance().complete(ctx);
    CompletionItem substring = items.stream()
        .filter(item -> "substring".equals(item.completion()))
        .findFirst()
        .orElseThrow();

    assertTrue(substring.insertText().endsWith("()"), "Expected method call insertion");
    assertTrue(substring.cursorOffset() == -1, "Expected cursor inside () for method with params");
  }

  @Test
  void suggestsImplicitPrintln() {
    String text = "printl";
    CompletionContext ctx = CompletionContext.builder()
        .fullText(text)
        .caretPosition(text.length())
        .tokenPrefix("printl")
        .build();

    List<CompletionItem> items = GroovyCompletionEngine.getInstance().complete(ctx);
    assertTrue(items.stream().anyMatch(item -> "println".equals(item.completion())),
        "Expected println to be suggested for implicit receiver");
  }

  @Test
  void completesMemberAccessOnUntypedParamUsingUsage() {
    String text = String.join("\n",
        "import groovy.transform.CompileStatic",
        "",
        "@CompileStatic",
        "class Test {",
        "  static Number distance(dx, dy) {",
        "    Math.sqrt(Math.pow(dx.))",
        "  }",
        "}",
        ""
    );

    int caret = text.indexOf("dx.") + "dx.".length();
    CompletionContext ctx = CompletionContext.builder()
        .fullText(text)
        .caretPosition(caret)
        .build();

    List<CompletionItem> items = GroovyCompletionEngine.getInstance().complete(ctx);

    assertFalse(items.isEmpty(), "Expected member completions for untyped parameter");
    assertTrue(items.stream().anyMatch(item -> "doubleValue".equals(item.completion())),
        "Expected inferred Number methods to be suggested");
  }
}
