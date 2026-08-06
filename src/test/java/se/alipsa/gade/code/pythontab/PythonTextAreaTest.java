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

  @Test
  void keepsTripleQuotedStringContainingSingleQuoteCharsTogether() {
    String code = "doc = \"\"\"He said \"hi\" there\"\"\"\n"
        + "if True: pass\n";
    StyleSpans<Collection<String>> spans = PythonTextArea.computeHighlightingFor(code);

    assertTrue(stylesAt(spans, code.indexOf("He said")).contains("string"),
        "text before the inner quote should be a string");
    assertTrue(stylesAt(spans, code.indexOf("there")).contains("string"),
        "text after the inner quote should be a string");
    assertTrue(stylesAt(spans, code.indexOf("if True")).contains("keyword"),
        "the docstring should not leak into the following line");
  }

  @Test
  void keepsMultilineDocstringTogether() {
    String code = "doc = \"\"\"line one\nif not code\n\"\"\"\nreturn 1\n";
    StyleSpans<Collection<String>> spans = PythonTextArea.computeHighlightingFor(code);

    assertTrue(stylesAt(spans, code.indexOf("if not")).contains("string"),
        "keywords inside a docstring should stay string styled");
    assertTrue(stylesAt(spans, code.indexOf("return")).contains("keyword"),
        "code after the docstring should be highlighted again");
  }

  @Test
  void doesNotAllowUnterminatedTripleQuoteToSwallowFollowingLines() {
    String code = "bad = \"\"\"unterminated\nif True: pass\n";
    StyleSpans<Collection<String>> spans = PythonTextArea.computeHighlightingFor(code);

    assertTrue(stylesAt(spans, code.indexOf("if True")).contains("keyword"),
        "an unterminated docstring should not create a runaway string style");
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
