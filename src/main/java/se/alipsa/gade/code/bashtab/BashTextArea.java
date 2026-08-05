package se.alipsa.gade.code.bashtab;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import se.alipsa.gade.code.CodeTextArea;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BashTextArea extends CodeTextArea {

  private static final String[] KEYWORDS = new String[]{
      "if", "then", "else", "elif", "fi", "case", "esac", "for", "while", "until",
      "do", "done", "in", "function", "select", "time", "return", "exit", "break",
      "continue", "shift", "source", "alias", "unalias", "export", "readonly",
      "local", "declare", "typeset", "trap", "wait", "kill", "bg", "fg", "jobs",
      "exec", "eval", "set", "unset", "env"
  };

  private static final String[] FUNCTIONS = new String[]{
      "echo", "printf", "read", "cd", "pwd", "pushd", "popd", "dirs", "umask",
      "ulimit", "test", "command", "type", "hash", "getopts", "shopt", "builtin",
      "compgen", "complete", "true", "false"
  };

  private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
  private static final String FUNCTIONS_PATTERN = "\\b(" + String.join("|", FUNCTIONS) + ")\\b";
  private static final String PAREN_PATTERN = "\\(|\\)";
  private static final String BRACE_PATTERN = "\\{|\\}";
  private static final String BRACKET_PATTERN = "\\[|\\]";
  private static final String VARIABLE_PATTERN = "\\$\\{[^}]*\\}|\\$[A-Za-z_][A-Za-z0-9_]*";
  private static final String OPERATOR_PATTERN = "&&|\\|\\||;;|<<|>>|<=|>=|==|!=|=|!|&|\\||;|<|>|\\+|\\*|-|/|%|~";
  private static final String DIGIT_PATTERN = "\\b\\d+\\b";
  private static final String STRING_PATTERN = "\"[^\"]*\"|'[^']*'";
  private static final String COMMENT_PATTERN = "#[^\\n]*";

  private static final Pattern PATTERN = Pattern.compile(
      "(?<COMMENT>" + COMMENT_PATTERN + ")"
          + "|(?<STRING>" + STRING_PATTERN + ")"
          + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
          + "|(?<FUNCTIONS>" + FUNCTIONS_PATTERN + ")"
          + "|(?<VARIABLE>" + VARIABLE_PATTERN + ")"
          + "|(?<PAREN>" + PAREN_PATTERN + ")"
          + "|(?<BRACE>" + BRACE_PATTERN + ")"
          + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
          + "|(?<OPERATOR>" + OPERATOR_PATTERN + ")"
          + "|(?<DIGIT>" + DIGIT_PATTERN + ")"
  );

  public BashTextArea() {
  }

  public BashTextArea(BashTab parent) {
    super(parent);
  }

  public static StyleSpans<Collection<String>> computeHighlightingFor(String text) {
    Matcher matcher = PATTERN.matcher(text);
    int lastKwEnd = 0;
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
    while (matcher.find()) {
      String styleClass =
          matcher.group("COMMENT") != null ? "comment" :
              matcher.group("STRING") != null ? "string" :
                  matcher.group("KEYWORD") != null ? "keyword" :
                      matcher.group("FUNCTIONS") != null ? "function" :
                          matcher.group("VARIABLE") != null ? "function" :
                              matcher.group("PAREN") != null ? "paren" :
                                  matcher.group("BRACE") != null ? "brace" :
                                      matcher.group("BRACKET") != null ? "bracket" :
                                          matcher.group("OPERATOR") != null ? "operator" :
                                              matcher.group("DIGIT") != null ? "digit" :
                                                  null; /* never happens */
      assert styleClass != null;
      spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
      spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
      lastKwEnd = matcher.end();
    }
    spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
    return spansBuilder.create();
  }

  @Override
  protected final StyleSpans<Collection<String>> computeHighlighting(String text) {
    return computeHighlightingFor(text);
  }
}
