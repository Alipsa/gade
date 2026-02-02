package se.alipsa.gade.code.completion.javascript;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import se.alipsa.gade.code.completion.CompletionContext;
import se.alipsa.gade.code.completion.CompletionEngine;
import se.alipsa.gade.code.completion.CompletionItem;

public final class JavascriptCompletionEngine implements CompletionEngine {

  private static final List<String> KEYWORDS = List.of(
      "await", "break", "case", "catch", "class",
      "const", "continue", "debugger", "default", "delete",
      "do", "else", "enum", "export", "extends",
      "false", "finally", "for", "function",
      "if", "implements", "import", "in", "instanceof", "interface",
      "let", "new", "null", "package", "private",
      "protected", "public", "return", "super", "switch",
      "static", "this", "throw", "try", "true",
      "typeof", "var", "void", "while", "with",
      "yield"
  );

  @Override
  public List<CompletionItem> complete(CompletionContext context) {
    String prefix = context == null || context.tokenPrefix() == null ? "" : context.tokenPrefix();
    String low = prefix.toLowerCase(Locale.ROOT);
    List<CompletionItem> items = new ArrayList<>();
    for (String keyword : KEYWORDS) {
      if (keyword.startsWith(low)) {
        items.add(new CompletionItem(keyword, CompletionItem.Kind.KEYWORD));
      }
    }
    return items;
  }

  @Override
  public Set<String> supportedLanguages() {
    return Set.of("javascript", "js");
  }
}
