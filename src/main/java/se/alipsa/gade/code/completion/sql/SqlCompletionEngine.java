package se.alipsa.gade.code.completion.sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import se.alipsa.gade.code.completion.CompletionItem;

public final class SqlCompletionEngine {

  private SqlCompletionEngine() {}

  private static SqlSchemaIntrospector schema = SqlSchemaIntrospector.NONE;

  public static void setSchemaIntrospector(SqlSchemaIntrospector s) {
    schema = (s == null ? SqlSchemaIntrospector.NONE : s);
  }

  private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
      "SELECT","FROM","WHERE","GROUP BY","HAVING","ORDER BY","LIMIT","OFFSET",
      "JOIN","LEFT JOIN","RIGHT JOIN","FULL JOIN","INNER JOIN","OUTER JOIN",
      "ON","USING","UNION","UNION ALL","EXCEPT","INTERSECT",
      "INSERT","INTO","VALUES","UPDATE","SET","DELETE",
      "CREATE","ALTER","DROP","TABLE","VIEW","INDEX","SCHEMA","DATABASE",
      "AND","OR","NOT","IN","IS NULL","IS NOT NULL","LIKE","BETWEEN","CASE","WHEN","THEN","ELSE","END","AS"
  ));

  private static final Set<String> FUNCTIONS = new HashSet<>(Arrays.asList(
      "COUNT","SUM","AVG","MIN","MAX","UPPER","LOWER","SUBSTRING","TRIM","COALESCE","NVL","ROUND","ABS","FLOOR","CEIL","CAST","CONCAT"
  ));

  // Flags: case-insensitive + proper Unicode classes for \w, etc.
  private static final int SQL_FLAGS =
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

  // Identifier forms
  private static final String UNQUOTED_IDENT = "[\\p{L}_][\\p{L}\\p{N}_$]*";
  private static final String DQ_IDENT      = "\"(?:[^\"]|\"\")+\"";       // "double-quoted", "" escapes
  private static final String BT_IDENT      = "`(?:[^`]|``)+`";            // `backticked`, `` escapes
  private static final String BR_IDENT      = "\\[(?:[^\\]]|\\]\\])+\\]";  // [bracketed], ]] escapes

  private static final String ANY_IDENT = "(?:" + DQ_IDENT + "|" + BT_IDENT + "|" + BR_IDENT + "|" + UNQUOTED_IDENT + ")";
  private static final String QUALIFIED_NAME = ANY_IDENT + "(?:\\." + ANY_IDENT + ")*";

  // FROM <qualified_table> [AS] <alias>
  private static final Pattern TABLE_ALIAS = Pattern.compile(
      "\\bfrom\\s+(" + QUALIFIED_NAME + ")\\s+(?:as\\s+)?" + "(" + ANY_IDENT + ")(?=\\s|,|$)", SQL_FLAGS);

  // JOIN <qualified_table> [AS] <alias>
  private static final Pattern JOIN_ALIAS = Pattern.compile(
      "\\bjoin\\s+(" + QUALIFIED_NAME + ")\\s+(?:as\\s+)?" + "(" + ANY_IDENT + ")(?=\\s|,|$)", SQL_FLAGS);

  // <alias>.<partialColumn>  (alias may be quoted; column prefix is unquoted while typing)
  private static final Pattern QUALIFIED_COLUMN = Pattern.compile(
      "(" + ANY_IDENT + ")\\.(\\w*)$", SQL_FLAGS);

  private static String unquoteIdent(String s) {
    if (s == null || s.isEmpty()) return s;
    char first = s.charAt(0), last = s.charAt(s.length() - 1);
    if (first == '"' && last == '"')  return s.substring(1, s.length()-1).replace("\"\"", "\"");
    if (first == '`' && last == '`')  return s.substring(1, s.length()-1).replace("``", "`");
    if (first == '[' && last == ']')  return s.substring(1, s.length()-1).replace("]]", "]");
    return s;
  }

  public static List<CompletionItem> complete(String lastWord, String fullText, int caretPos) {
    List<CompletionItem> out = new ArrayList<>();
    String prefix = (lastWord == null ? "" : lastWord);
    String low = prefix.toLowerCase(Locale.ROOT);

    Map<String, String> aliasToTable = extractAliases(fullText.substring(0, Math.max(0, caretPos)));

    Matcher q = QUALIFIED_COLUMN.matcher(fullText.substring(0, Math.max(0, caretPos)));
    if (q.find()) {
      String aliasRaw = q.group(1); // may be quoted/backticked/bracketed
      String aliasNorm = unquoteIdent(aliasRaw).toLowerCase(Locale.ROOT);
      String colPrefix = q.group(2).toLowerCase(Locale.ROOT);
      String table = aliasToTable.get(aliasNorm);
      if (table != null) {
        for (String col : schema.columns(table)) {
          if (col.toLowerCase(Locale.ROOT).startsWith(colPrefix)) {
            // Keep the original alias text for display/insertion
            out.add(new CompletionItem(col, aliasRaw + "." + col, CompletionItem.Kind.COLUMN));
          }
        }
        return out;
      }
    }

    String before = fullText.substring(0, Math.max(0, caretPos)).toLowerCase(Locale.ROOT);
    if (endsWithKeyword(before, "from") || endsWithKeyword(before, "join")) {
      for (String t : schema.tables()) {
        if (t.toLowerCase(Locale.ROOT).startsWith(low)) {
          out.add(new CompletionItem(t, CompletionItem.Kind.TABLE));
        }
      }
      if (!out.isEmpty()) return out;
    }

    int sel = before.lastIndexOf("select");
    int frm = before.lastIndexOf(" from ");
    if (sel >= 0 && (frm < 0 || sel > frm)) {
      for (Map.Entry<String, String> e : aliasToTable.entrySet()) {
        for (String col : schema.columns(e.getValue())) {
          if (col.toLowerCase(Locale.ROOT).startsWith(low)) {
            out.add(new CompletionItem(e.getKey() + "." + col, e.getKey() + "." + col, CompletionItem.Kind.COLUMN));
          }
        }
      }
      for (String f : FUNCTIONS) {
        if (f.toLowerCase(Locale.ROOT).startsWith(low)) {
          out.add(new CompletionItem(f + "(", f + "(...)", CompletionItem.Kind.FUNCTION));
        }
      }
      if (!out.isEmpty()) return out;
    }

    for (String kw : KEYWORDS) {
      if (kw.toLowerCase(Locale.ROOT).startsWith(low)) {
        out.add(new CompletionItem(kw, CompletionItem.Kind.KEYWORD));
      }
    }
    for (String f : FUNCTIONS) {
      if (f.toLowerCase(Locale.ROOT).startsWith(low)) {
        out.add(new CompletionItem(f + "(", f + "(...)", CompletionItem.Kind.FUNCTION));
      }
    }
    return out;
  }

  private static boolean endsWithKeyword(String s, String kw) {
    int idx = s.lastIndexOf(kw);
    if (idx < 0) return false;
    for (int i = idx + kw.length(); i < s.length(); i++) {
      if (!Character.isWhitespace(s.charAt(i))) return false;
    }
    return true;
  }

  private static Map<String, String> extractAliases(String text) {
    Map<String, String> map = new HashMap<>();
    Matcher m = TABLE_ALIAS.matcher(text);
    while (m.find()) {
      String table = unquoteIdent(m.group(1));
      String alias = unquoteIdent(m.group(2)).toLowerCase(Locale.ROOT);
      map.put(alias, table);
    }
    m = JOIN_ALIAS.matcher(text);
    while (m.find()) {
      String table = unquoteIdent(m.group(1));
      String alias = unquoteIdent(m.group(2)).toLowerCase(Locale.ROOT);
      map.put(alias, table);
    }
    return map;
  }
}
