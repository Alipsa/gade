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

  @Test
  void keepsEscapedAndTripleQuotedStringsTogether() {
    String code = "message = \"say \\\"if\\\"\"\n"
        + "doc = '''return\\nif'''\n";
    StyleSpans<Collection<String>> spans = PythonTextArea.computeHighlightingFor(code);

    assertTrue(stylesAt(spans, code.indexOf("if")).contains("string"),
        "escaped quote content should remain a string");
    int tripleQuotedIf = code.lastIndexOf("if");
    assertTrue(stylesAt(spans, tripleQuotedIf).contains("string"),
        "triple-single-quoted content should remain a string");
  }

  private static Set<String> stylesAt(StyleSpans<Collection<String>> spans, int position) {
    int offset = 0;
    for (int i = 0; i < spans.getSpanCount(); i++) {
      var span = spans.getStyleSpan(i);
      if (position >= offset && position < offset + span.getLength()) {
        return Set.copyOf(span.getStyle());
      }
      offset += span.getLength();
    }
    return Set.of();
  }
}
