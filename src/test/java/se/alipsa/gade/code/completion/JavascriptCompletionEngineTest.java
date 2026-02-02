package se.alipsa.gade.code.completion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import se.alipsa.gade.code.completion.javascript.JavascriptCompletionEngine;

class JavascriptCompletionEngineTest {

  @Test
  void suggestsKeywordsCaseInsensitive() {
    JavascriptCompletionEngine engine = new JavascriptCompletionEngine();
    CompletionContext context = CompletionContext.builder()
        .fullText("FUN")
        .caretPosition(3)
        .tokenPrefix("FUN")
        .build();

    List<CompletionItem> items = engine.complete(context);
    assertTrue(items.stream().anyMatch(item ->
        item.kind() == CompletionItem.Kind.KEYWORD &&
            "function".equals(item.completion().toLowerCase(Locale.ROOT))));
  }

  @Test
  void enhancedCompletionRoutesToJavascriptEngine() {
    CompletionContext context = CompletionContext.builder()
        .fullText("ret")
        .caretPosition(3)
        .tokenPrefix("ret")
        .build();

    List<CompletionItem> items = EnhancedCompletion.suggest(context, "javascript");
    assertTrue(items.stream().anyMatch(item ->
        item.kind() == CompletionItem.Kind.KEYWORD &&
            "return".equals(item.completion())));
  }
}
