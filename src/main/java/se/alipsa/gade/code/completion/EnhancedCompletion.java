package se.alipsa.gade.code.completion;

import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import se.alipsa.gade.code.completion.groovy.GroovyCompletionEngine;
import se.alipsa.gade.code.completion.sql.SqlCompletionEngine;

/**
 * Facade used by editors to obtain completion items without
 * knowing about language-specific engines.
 */
public final class EnhancedCompletion {

  private EnhancedCompletion() {}

  public static List<CompletionItem> suggest(String lastWord, TreeMap<String, Boolean> keywords, String fullText, int caretPos) {
    Language lang = detectLanguage(keywords);
    switch (lang) {
      case SQL:
        return SqlCompletionEngine.complete(lastWord, fullText, caretPos);
      case GROOVY:
      default:
        return GroovyCompletionEngine.complete(lastWord, fullText, caretPos);
    }
  }

  private static Language detectLanguage(TreeMap<String, Boolean> keywords) {
    if (keywords == null || keywords.isEmpty()) return Language.GROOVY;
    StringBuilder sb = new StringBuilder();
    for (String k : keywords.keySet()) {
      sb.append(k).append(',');
    }
    String joined = sb.toString().toLowerCase(Locale.ROOT);
    if (joined.contains("select") || joined.contains("from") || joined.contains("where")) {
      return Language.SQL;
    }
    return Language.GROOVY;
  }

  private enum Language { GROOVY, SQL }
}
