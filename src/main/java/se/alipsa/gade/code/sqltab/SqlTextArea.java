package se.alipsa.gade.code.sqltab;

import javafx.scene.control.ContextMenu;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import se.alipsa.gade.code.CodeTextArea;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlTextArea extends CodeTextArea {

  private static final Logger log = LogManager.getLogger(SqlTextArea.class);
  private static final String[] KEYWORDS = new String[]{
      "absolute", "action", "add", "admin", "after", "aggregate", "alias", "all", "allocate", "alter",
      "and", "any", "are", "array", "as", "asc", "assertion", "assertion", "at", "atomic", "authorization",
      "avg",

      "before", "begin", "bigint", "binary", "bit", "blob", "boolean",
      "both", "breadth", "by", "call",

      "cascade", "cascaded", "case", "cast", "catalog", "char", "character", "check",
      "class", "clob", "close", "collate", "collation", "collect", "column", "commit",
      "completion", "condition", "connect", "connection", "constraint", "constraints", "constructor", "contains",
      "continue", "corresponding", "count", "create", "cross", "cube", "current", "current_date", "current_path",
      "current_role", "current_time", "current_timestamp", "current_user", "cursor", "cycle", "data", "datalink",

      "date", "day", "deallocate", "dec", "decimal", "declare", "default", "deferrable",
      "delete", "depth", "deref", "desc", "descriptor", "destructor", "diagnostics", "dictionary",
      "disconnect", "distinct", "do", "domain", "double", "drop",

      "element", "else", "end", "end-exec", "equals", "escape", "except", "exception", "execute",
      "exists", "exit", "expand", "expanding",

      "false", "first", "float", "for", "foreign", "free", "from", "function", "fusion",

      "general", "get", "global", "goto", "group", "grouping",

      "handler", "hash", "having", "hour",

      "identity", "if", "ignore", "immediate", "in", "indicator", "initialize", "initially", "inner",
      "inout", "input", "insert", "int", "integer", "intersect", "intersection", "interval", "into",
      "is", "isolation", "iterate", "join", "key",

      "language", "large", "last", "lateral", "leading", "leave", "left", "less",
      "level", "like", "limit", "local", "localtime", "localtimestamp", "locator", "loop",

      "match", "max", "member", "meets", "merge", "min", "minute", "modifies", "modify", "module", "month", "multiset",

      "names", "national", "natural", "nchar", "nclob", "new", "next", "no", "none", "normalize",
      "not", "null", "numeric",

      "object", "of", "off", "old", "on", "only", "open", "operation", "option",
      "or", "order", "ordinality", "out", "outer", "output", "over",

      "pad", "parameter", "parameters", "partial", "partition", "path", "period", "pivot",
      "postfix", "precedes", "precision", "prefix", "preorder", "prepare", "preserve", "primary",
      "prior", "privileges", "procedure", "public",

      "read", "reads", "real", "recursive", "redo", "ref", "references", "referencing",
      "relative", "repeat", "resignal", "restrict", "result", "return", "returns", "revoke",
      "right", "role", "rollback", "rollup", "routine", "row", "rownum", "rows",

      "savepoint", "schema", "scroll", "search", "second", "section", "select", "sequence", "session",
      "session_user", "set", "sets", "signal", "size", "smallint", "specific", "specifictype",
      "sql", "sqlexception", "sqlstate", "sqlwarning", "start", "state", "static", "structure",
      "submultiset", "succeeds", "sum", "system_user",

      "table", "tablesample", "temporary", "terminate", "than", "then", "time", "timestamp",
      "timezone_hour", "timezone_minute", "to", "top", "trailing", "transaction", "translation", "treat",
      "trigger", "true", "uescape",

      "under", "undo", "union", "unique", "unknown", "until", "unpivot", "update", "usage", "user", "using",

      "value", "values", "varchar", "variable", "varying", "view",

      "when", "whenever", "where", "while", "with", "write",

      "year",

      "zone"
  };

  private static final String KEYWORD_PATTERN = "(?i)\\b(" + String.join("|", KEYWORDS) + ")\\b";
  private static final String PAREN_PATTERN = "\\(|\\)";
  private static final String SEMICOLON_PATTERN = "\\;";
  //private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"" + "|" + "\'([^\'\\\\]|\\\\.)*\'";
  private static final String STRING_PATTERN = "\"\"|''|\"[^\"]+\"|'[^']+'";
  private static final String COMMENT_PATTERN = "--[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";

  private static final Pattern PATTERN = Pattern.compile(
      "(?<COMMENT>" + COMMENT_PATTERN + ")"
          + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
          + "|(?<PAREN>" + PAREN_PATTERN + ")"
          + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
          + "|(?<STRING>" + STRING_PATTERN + ")"

  );

  private final ContextMenu suggestionsPopup = new ContextMenu();

  public SqlTextArea(SqlTab parent) {
    super(parent);
    addEventHandler(KeyEvent.KEY_PRESSED, e -> {
      if (e.isControlDown()) {
        if (KeyCode.SPACE.equals(e.getCode())) {
          autoComplete();
        } else if (KeyCode.ENTER.equals(e.getCode())) {
          parent.executeQuery(getSelectedOrCurrentLine());
        }
      } else if (KeyCode.PERIOD.equals(e.getCode())) {
        suggestMetaData();
      }
    });
  }

  private String getSelectedOrCurrentLine() {
    String sql = getSelectedText();
    if (sql == null || "".equals(sql)) {
      sql = getText(getCurrentParagraph());
    }
    return sql;
  }

  @Override
  public void autoComplete() {
    String line = getText(getCurrentParagraph());
    String lastWord = line.replaceAll("^.*?(\\w+)\\W*$", "$1");
    if (line.endsWith(lastWord) && !"".equals(lastWord)) {
      TreeMap<String, Boolean> suggestions = new TreeMap<>();
      String lcLastWord = lastWord.toLowerCase();
      for (String keyWord : KEYWORDS) {
        if (keyWord.startsWith(lcLastWord)) {
          suggestions.put(keyWord, Boolean.FALSE);
        }
      }
      suggestCompletion(lastWord, suggestions, suggestionsPopup);
    }
  }

  private void suggestMetaData() {
    //TODO: Should suggest a column name here if a recognised table name is used based on meta data
  }


  // TODO, while this works most of the time, sometimes for highly complex SQl code matching
  //  goes into an infinite loop. An alternative approach is to either parse to an AST and apply
  //  styling on the AST model or create an eventparser (like SAX) and apply styling that way.
  @Override
  protected StyleSpans<Collection<String>> computeHighlighting(String text) {
    log.trace("Computing highlighting for sql");
    Matcher matcher = PATTERN.matcher(text);
    int lastKwEnd = 0;
    StyleSpansBuilder<Collection<String>> spansBuilder
        = new StyleSpansBuilder<>();
    while (matcher.find()) {
      String styleClass =
          matcher.group("COMMENT") != null ? "comment" :
            matcher.group("KEYWORD") != null ? "keyword" :
              matcher.group("PAREN") != null ? "paren" :
                  matcher.group("SEMICOLON") != null ? "semicolon" :
                      matcher.group("STRING") != null ? "string" :
                              null; /* never happens */
      assert styleClass != null;
      spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
      spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
      lastKwEnd = matcher.end();
    }
    spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
    return spansBuilder.create();
  }
}
