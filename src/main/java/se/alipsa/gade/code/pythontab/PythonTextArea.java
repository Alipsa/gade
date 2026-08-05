package se.alipsa.gade.code.pythontab;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import se.alipsa.gade.code.CodeTextArea;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PythonTextArea extends CodeTextArea {

  private static final String[] KEYWORDS = new String[]{
      "False", "None", "True", "and", "as", "assert", "async", "await", "break",
      "class", "continue", "def", "del", "elif", "else", "except", "finally",
      "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
      "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
  };

  private static final String[] FUNCTIONS = new String[]{
      "abs", "all", "any", "bin", "bool", "breakpoint", "bytearray", "bytes",
      "callable", "chr", "classmethod", "compile", "complex", "delattr", "dict",
      "dir", "divmod", "enumerate", "eval", "exec", "filter", "float", "format",
      "frozenset", "getattr", "globals", "hasattr", "hash", "help", "hex", "id",
      "input", "int", "isinstance", "issubclass", "iter", "len", "list", "locals",
      "map", "max", "memoryview", "min", "next", "object", "oct", "open", "ord",
      "pow", "print", "property", "range", "repr", "reversed", "round", "set",
      "setattr", "slice", "sorted", "staticmethod", "str", "sum", "super", "tuple",
      "type", "vars", "zip"
  };

  private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
  private static final String FUNCTIONS_PATTERN = "\\b(" + String.join("|", FUNCTIONS) + ")\\b";
  private static final String PAREN_PATTERN = "\\(|\\)";
  private static final String BRACE_PATTERN = "\\{|\\}";
  private static final String BRACKET_PATTERN = "\\[|\\]";
  private static final String OPERATOR_PATTERN = "//|\\*\\*|\\+\\+|--|\\+=|-=|\\*=|/=|%=|&=|\\|=|\\^=|<<=|>>=|==|!=|<=|>=|<|>|=|\\+|-|\\*|/|%|&|\\||\\^|~|@";
  private static final String DIGIT_PATTERN = "\\b\\d+\\b";
  private static final String STRING_PATTERN = "\"\"\"[^\"]*\"\"\"|'[^']*'|\"[^\"]*\"";
  private static final String COMMENT_PATTERN = "#[^\\n]*";

  private static final Pattern PATTERN = Pattern.compile(
      "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
          + "|(?<FUNCTIONS>" + FUNCTIONS_PATTERN + ")"
          + "|(?<PAREN>" + PAREN_PATTERN + ")"
          + "|(?<BRACE>" + BRACE_PATTERN + ")"
          + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
          + "|(?<OPERATOR>" + OPERATOR_PATTERN + ")"
          + "|(?<DIGIT>" + DIGIT_PATTERN + ")"
          + "|(?<STRING>" + STRING_PATTERN + ")"
          + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
  );

  public PythonTextArea() {
  }

  public PythonTextArea(PythonTab parent) {
    super(parent);
  }

  public static StyleSpans<Collection<String>> computeHighlightingFor(String text) {
    Matcher matcher = PATTERN.matcher(text);
    int lastKwEnd = 0;
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
    while (matcher.find()) {
      String styleClass =
          matcher.group("KEYWORD") != null ? "keyword" :
              matcher.group("FUNCTIONS") != null ? "function" :
                  matcher.group("PAREN") != null ? "paren" :
                      matcher.group("BRACE") != null ? "brace" :
                          matcher.group("BRACKET") != null ? "bracket" :
                              matcher.group("OPERATOR") != null ? "operator" :
                                  matcher.group("DIGIT") != null ? "digit" :
                                      matcher.group("STRING") != null ? "string" :
                                          matcher.group("COMMENT") != null ? "comment" :
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
