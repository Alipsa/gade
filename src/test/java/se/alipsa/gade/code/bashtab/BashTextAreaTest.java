package se.alipsa.gade.code.bashtab;

import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.Test;

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
}
