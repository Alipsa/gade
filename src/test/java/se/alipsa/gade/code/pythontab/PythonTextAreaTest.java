package se.alipsa.gade.code.pythontab;

import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonTextAreaTest {

  @Test
  void highlightsPythonKeywordsStringsCommentsAndFunctions() {
    String code = "def hello():\n  # a comment\n  print('hi')\n  x = 42\n";
    StyleSpans<Collection<String>> spans = PythonTextArea.computeHighlightingFor(code);

    Set<String> styles = spans.stream()
        .flatMap(span -> span.getStyle().stream())
        .collect(Collectors.toSet());

    assertTrue(styles.contains("keyword"), "expected keyword style");
    assertTrue(styles.contains("function"), "expected function style");
    assertTrue(styles.contains("string"), "expected string style");
    assertTrue(styles.contains("comment"), "expected comment style");
    assertTrue(styles.contains("digit"), "expected digit style");
    assertFalse(styles.isEmpty(), "expected some styles");
  }
}
