package se.alipsa.gade.code.bashtab;

import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BashTextAreaTest {

  @Test
  void highlightsBashKeywordsStringsCommentsAndVariables() {
    String code = "#!/bin/bash\nif [ $x -eq 1 ]; then\n  echo 'hello' # greet\nfi\n";
    StyleSpans<Collection<String>> spans = BashTextArea.computeHighlightingFor(code);

    Set<String> styles = spans.stream()
        .flatMap(span -> span.getStyle().stream())
        .collect(Collectors.toSet());

    assertTrue(styles.contains("keyword"), "expected keyword style");
    assertTrue(styles.contains("function"), "expected function style");
    assertTrue(styles.contains("variable"), "expected variable style");
    assertTrue(styles.contains("string"), "expected string style");
    assertTrue(styles.contains("comment"), "expected comment style");
    assertFalse(styles.isEmpty(), "expected some styles");
  }

  @Test
  void doesNotAllowUnclosedQuoteToSwallowFollowingLines() {
    String code = "echo \"unclosed\nif true; then\n  echo done\nfi\n";
    StyleSpans<Collection<String>> spans = BashTextArea.computeHighlightingFor(code);

    Set<String> styles = spans.stream()
        .flatMap(span -> span.getStyle().stream())
        .collect(Collectors.toSet());

    assertTrue(styles.contains("keyword"), "expected keyword style on later lines");
    assertFalse(styles.contains("string"), "unclosed quote should not create a runaway string style");
  }

  @Test
  void treatsSpecialParametersAsVariablesRatherThanComments() {
    String code = "echo $# args here\n";
    StyleSpans<Collection<String>> spans = BashTextArea.computeHighlightingFor(code);

    assertTrue(stylesAt(spans, code.indexOf("$#")).contains("variable"),
        "$# should be highlighted as a variable");
    assertTrue(stylesAt(spans, code.indexOf("args")).isEmpty(),
        "the # of $# should not start a comment");
  }

  @Test
  void highlightsPositionalParameters() {
    String code = "target=$1\nshift\n";
    StyleSpans<Collection<String>> spans = BashTextArea.computeHighlightingFor(code);

    assertTrue(stylesAt(spans, code.indexOf("$1")).contains("variable"),
        "$1 should be highlighted as a variable");
  }

  @Test
  void stillTreatsAStandaloneHashAsAComment() {
    String code = "grep foo # real comment\n";
    StyleSpans<Collection<String>> spans = BashTextArea.computeHighlightingFor(code);

    assertTrue(stylesAt(spans, code.indexOf("# real")).contains("comment"),
        "expected comment style");
  }

  @Test
  void doesNotAllowComplexSingleQuoteIdiomToSpanLines() throws IOException {
    String code;
    try (InputStream is = getClass().getResourceAsStream("/bash/releaseSnippet.sh")) {
      code = new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
    StyleSpans<Collection<String>> spans = BashTextArea.computeHighlightingFor(code);

    // Find the end of the problematic grep/sed line (line 3 of the snippet).
    int firstNewline = code.indexOf('\n');
    int secondNewline = code.indexOf('\n', firstNewline + 1);
    int endOfProblematicLine = code.indexOf('\n', secondNewline + 1);

    long stringStyleAfterProblematicLine = 0;
    long pos = 0;
    for (int i = 0; i < spans.getSpanCount(); i++) {
      var span = spans.getStyleSpan(i);
      long start = pos;
      long end = pos + span.getLength();
      if (start > endOfProblematicLine && span.getStyle().contains("string")) {
        stringStyleAfterProblematicLine += span.getLength();
      }
      pos = end;
    }

    assertTrue(stringStyleAfterProblematicLine == 0,
        "string style should not span past the line containing the single-quote idiom");
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
