package code.groovy;

import org.junit.jupiter.api.Test;
import se.alipsa.gade.code.groovytab.GroovyTextArea;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class GroovyTextAreaHighlightRegexTest {

  @Test
  public void lineCommentWithAtShouldBeComment_notString() {
    String line1 = "//@GrabConfig(systemClassLoader=true)\n";
    String line2 = "//@Grab('org.apache.ant:ant:1.10.15')\n";

    Pattern comments = Pattern.compile(GroovyTextArea.COMMENT_PATTERN);
    Matcher m1 = comments.matcher(line1);
    Matcher m2 = comments.matcher(line2);

    assertTrue(m1.find(), "Line 1 should match COMMENT");
    assertEquals(0, m1.start());
    assertEquals(line1.trim().length(), m1.end()); // without newline

    assertTrue(m2.find(), "Line 2 should match COMMENT");
    assertEquals(0, m2.start());
    assertEquals(line2.trim().length(), m2.end());
  }

  @Test
  void slashyShouldNotMatchDoubleSlash() {
    Pattern slashy = Pattern.compile("/(?:\\\\/|[^/])+/");

    assertFalse(slashy.matcher("//").find(), "Bare '//' must not match a slashy string");
    assertTrue(slashy.matcher("/a/").find(), "Slashy '/a/' should match");
    assertTrue(slashy.matcher("/\\//").find(), "Escaped slash '/\\//' should match");
  }

  @Test
  void commentPrecedenceOverString_inCombinedPattern() {
    // Simulate the combined pattern with COMMENT first, then STRING
    Pattern combined = Pattern.compile(
        "(?<COMMENT>" + GroovyTextArea.COMMENT_PATTERN + ")"
            + "|(?<STRING>" + GroovyTextArea.STRING_PATTERN + ")"
    );

    String code = "//@Grab('x')\n";
    var m = combined.matcher(code);
    assertTrue(m.find(), "Should find a token");
    assertNotNull(m.group("COMMENT"), "COMMENT should be chosen over STRING");
    assertNull(m.group("STRING"), "STRING must not match for '//' lines");
  }
}
