package se.alipsa.gade.code.groovytab;

import io.github.classgraph.*;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import se.alipsa.gade.Constants;
import se.alipsa.gade.Gade;
import se.alipsa.gade.code.CodeComponent;
import se.alipsa.gade.code.CodeTextArea;
import se.alipsa.gade.code.TextAreaTab;
import se.alipsa.gade.model.GroovyCodeHeader;
import se.alipsa.gade.utils.Alerts;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static se.alipsa.gade.menu.GlobalOptions.ADD_IMPORTS;

public class GroovyTextArea extends CodeTextArea {

  private static final Pattern SIMPLE_TYPE = Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\b");

  private static final List<String> DEFAULT_PKGS = List.of("java.lang", "groovy.lang", "java.io",
      "java.net", "java.util", "groovy.util");

  // Matches: import pkg.Class
  private static final java.util.regex.Pattern IMPORT_NORMAL =
      java.util.regex.Pattern.compile("(?m)^\\s*import\\s+([\\w.]+)\\s*(?:;)?\\s*$");
  // Matches: import pkg.Class as Alias
  private static final java.util.regex.Pattern IMPORT_ALIAS =
      java.util.regex.Pattern.compile("(?m)^\\s*import\\s+([\\w.]+)\\s+as\\s+(\\w+)\\s*(?:;)?\\s*$");
  // Matches: import static pkg.Class.member  OR  import static pkg.Class.*
  private static final java.util.regex.Pattern IMPORT_STATIC =
      java.util.regex.Pattern.compile("(?m)^\\s*import\\s+static\\s+([\\w.]+)(?:\\.(\\w+)|\\.\\*)\\s*(?:;)?\\s*$");


  ContextMenu suggestionsPopup = new ContextMenu();
  private static final String[] KEYWORDS = new String[]{
          "abstract", "as", "assert",
          "boolean", "break", "byte",
          "case", "catch", "char", "class", "const", "continue",
          "def", "default", "do", "double",
          "else", "enum", "extends",
          "false", "final", "finally", "float", "for",
          "goto", "@Grab",
          "if", "implements", "import", "in", "instanceof", "int", "interface",
          "long",
          "native", "new", "null",
          "package", "private", "protected", "public",
          "return",
          "short", "static", "strictfp", "super", "switch", "synchronized",
          "this", "threadsafe", "throw", "throws",
          "transient", "true", "try",
          "var", "void", "volatile",
          "while"
  };

  private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
  private static final String PAREN_PATTERN = "\\(|\\)";
  private static final String BRACE_PATTERN = "\\{|\\}";
  private static final String BRACKET_PATTERN = "\\[|\\]";
  private static final String SEMICOLON_PATTERN = "\\;";
  private static final String STRING_PATTERN = "\"\"|''|\"[^\"]+\"|'[^']+'";
  private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";

  private static final Pattern PATTERN = Pattern.compile(
      "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
          + "|(?<PAREN>" + PAREN_PATTERN + ")"
          + "|(?<BRACE>" + BRACE_PATTERN + ")"
          + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
          + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
          + "|(?<STRING>" + STRING_PATTERN + ")"
          + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
  );

  public GroovyTextArea() {
  }

  public GroovyTextArea(TextAreaTab parent) {
    super(parent);
    addEventHandler(KeyEvent.KEY_PRESSED, e -> {
      if (e.isControlDown()) {
        if (KeyCode.ENTER.equals(e.getCode())) {
          CodeComponent codeComponent = parent.getGui().getCodeComponent();
          String gCode = "";
          String selected = selectedTextProperty().getValue();
          // if text is selected then go with that
          if (selected != null && !"".equals(selected)) {
            gCode += codeComponent.getTextFromActiveTab();
          } else {
            gCode += getText(getCurrentParagraph()); // current line
          }
          if (parent instanceof GroovyTab groovyTab) {
            groovyTab.runGroovy(gCode, true);
          } else {
            Alerts.warn("Run code", "Not implemented");
          }
          moveTo(getCurrentParagraph() + 1, 0);
          int totalLength = getAllTextContent().length();
          if (getCaretPosition() > totalLength) {
            moveTo(totalLength);
          }
        } else if (KeyCode.SPACE.equals(e.getCode()) || KeyCode.PERIOD.equals(e.getCode())) {
          autoComplete();
        }
      }
    });

    this.setOnContextMenuRequested(evt -> {
      int pos = this.hit(evt.getX(), evt.getY()).getInsertionIndex();
      String token = tokenAt(pos);
      if (token == null) return;

      // Only for capitalized simple names (type-ish)
      if (!token.matches("[A-Z][A-Za-z0-9_]*")) return;

      // If already resolvable, nothing to do
      var idx = se.alipsa.gade.code.completion.groovy.GroovyCompletionEngine.simpleNameIndex();
      var candidates = idx.getOrDefault(token, java.util.List.of());
      if (candidates.isEmpty()) return;

      // Filter out ones already resolvable via imports/defaults
      var menu = new javafx.scene.control.ContextMenu();
      for (String fqn : candidates) {
        if (isAlreadyImportedOrDefault(token, fqn)) continue;
        var mi = new javafx.scene.control.MenuItem("Import " + fqn);
        mi.setOnAction(ae -> addImportIfMissing(fqn));
        menu.getItems().add(mi);
      }
      if (!menu.getItems().isEmpty()) {
        menu.show(this, evt.getScreenX(), evt.getScreenY());
        evt.consume();
      }
    });
  }

  List<String> getDependencies() {
    List<String> headers = new ArrayList<>();
    try(Scanner scanner = new Scanner(getAllTextContent())) {
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine().trim();
        if (line.startsWith("@Grab")
            || line.startsWith("Grape.grab")
            || line.startsWith("groovy.grape.")
            || line.startsWith("io.addDependency")
        ) {
          headers.add(line);
        }
      }
    }
    return headers;
  }

  List<String> getImports() {
    List<String> headers = new ArrayList<>();
    try(Scanner scanner = new Scanner(getAllTextContent())) {
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine().trim();
        if (line.startsWith("import ")) {
          headers.add(line);
        }
      }
    }
    return headers;
  }

  protected final StyleSpans<Collection<String>> computeHighlighting(String text) {
    Matcher matcher = PATTERN.matcher(text);
    int lastKwEnd = 0;
    StyleSpansBuilder<Collection<String>> spansBuilder
        = new StyleSpansBuilder<>();
    while (matcher.find()) {
      String styleClass =
          matcher.group("KEYWORD") != null ? "keyword" :
              matcher.group("PAREN") != null ? "paren" :
                  matcher.group("BRACE") != null ? "brace" :
                      matcher.group("BRACKET") != null ? "bracket" :
                          matcher.group("SEMICOLON") != null ? "semicolon" :
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
  public void autoComplete() {
    String line = getText(getCurrentParagraph());
    String current = line.substring(0, getCaretColumn());

    // token after the last separator
    int cut = Math.max(
        Math.max(Math.max(current.lastIndexOf(' '), current.lastIndexOf('\t')),
            Math.max(current.lastIndexOf('('), current.lastIndexOf('{'))),
        Math.max(current.lastIndexOf('['), Math.max(current.lastIndexOf(','), current.lastIndexOf(';'))));

    String lastToken = cut >= 0 ? current.substring(cut + 1) : current;

    // if member access, only replace the part after '.'
    int dot = lastToken.lastIndexOf('.');
    String wordToReplace = dot >= 0 ? lastToken.substring(dot + 1) : lastToken;

    // allow empty member prefix after a trailing dot (e.g. "LocalDate.")
    if (!wordToReplace.isEmpty() || dot >= 0) {
      suggestCompletion(wordToReplace, new java.util.TreeMap<>(), suggestionsPopup);
    }
  }

  @Override
  public void highlightSyntax() {
    super.highlightSyntax();               // keep your existing coloring
    Platform.runLater(this::markUnresolvedTypes); // overlay diagnostics afterwards
    Platform.runLater(this::markUnusedImports);
  }

  private void markUnresolvedTypes() {
    String text = getText();
    var unresolved = findUnresolvedSimpleTypes(text);

    // Apply underline only to those ranges. These tokens don’t overlap keywords,
    // so overriding the style for just that range is OK.
    for (var r : unresolved) {
      setStyle(r.start, r.end, java.util.List.of("unresolved-token"));
    }
  }

  private String tokenAt(int caretPos) {
    String t = getText();
    if (caretPos < 0 || caretPos > t.length()) return null;
    int s = caretPos, e = caretPos;
    while (s > 0 && Character.isJavaIdentifierPart(t.charAt(s - 1))) s--;
    while (e < t.length() && Character.isJavaIdentifierPart(t.charAt(e))) e++;
    if (e > s) return t.substring(s, e);
    return null;
  }

  private boolean isAlreadyImportedOrDefault(String simple, String fqn) {
    // Check explicit imports present
    var imps = getImports(); // you already have this method; returns e.g. "import java.time.LocalDate"
    for (String imp : imps) {
      String fq = imp.substring("import ".length()).replace(";", "").trim();
      if (fq.startsWith("static ")) fq = fq.substring("static ".length()).trim();
      if (fq.endsWith(".*")) {
        String pkg = fq.substring(0, fq.length() - 2);
        if (fqn.startsWith(pkg + ".")) return true;
      } else if (fq.equals(fqn)) {
        return true;
      }
    }
    // default packages
    for (String pkg : DEFAULT_PKGS) {
      if (fqn.startsWith(pkg + ".")) return true;
    }
    return false;
  }

  private void addImportIfMissing(String fqn) {
    // Don’t duplicate (also respect wildcard imports that already cover it)
    var imports = getImports(); // your existing helper: lines starting with "import "
    for (String imp : imports) {
      String s = imp.trim();
      if (s.endsWith(";")) s = s.substring(0, s.length() - 1);
      if (s.startsWith("import ")) s = s.substring(7).trim();
      if (s.startsWith("static ")) s = s.substring(7).trim();
      if (s.equals(fqn)) return;
      if (s.endsWith(".*")) {
        String pkg = s.substring(0, s.length() - 2);
        if (fqn.startsWith(pkg + ".")) return; // covered by wildcard
      }
    }

    String all = getAllTextContent();
    String eol = all.contains("\r\n") ? "\r\n" : "\n";

    // Skip shebang if present
    int shebangEnd = 0;
    Matcher sh = Pattern.compile("^#!.*(?:\\r?\\n)").matcher(all);
    if (sh.find()) shebangEnd = sh.end();

    // After package (semicolon optional)
    int afterPackage = shebangEnd;
    Matcher pm = Pattern.compile("(?m)^\\s*package\\s+[\\w.]+\\s*;?\\s*$").matcher(all);
    if (pm.find()) afterPackage = pm.end();

    // After the last existing import (semicolon optional, static/wildcard supported)
    int afterImports = afterPackage;
    Matcher im = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?[\\w.]+(?:\\.\\*)?\\s*;?\\s*$").matcher(all);
    while (im.find()) {
      afterImports = im.end();
    }

    int insertPos = Math.max(afterImports, afterPackage);
    String toInsert = (insertPos == 0 ? "" : eol) + "import " + fqn + eol;
    replaceContentText(insertPos, insertPos, toInsert);
  }

  private static final class Range { final int start, end; Range(int s, int e){start=s; end=e;} }

  private java.util.List<Range> findUnresolvedSimpleTypes(String code) {
    // 1) Collect current imports and wildcard imports
    var explicitImports = new java.util.HashSet<String>();  // fqcn
    var wildcardPkgs    = new java.util.HashSet<String>();  // e.g. "java.time"

    String currentPkg = null;

    try (var scanner = new java.util.Scanner(code)) {
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine().trim();
        if (line.startsWith("package ")) {
          currentPkg = line.substring("package ".length()).replace(";", "").trim();
        } else if (line.startsWith("import ")) {
          String imp = line.substring("import ".length()).replace(";", "").trim();
          if (imp.startsWith("static ")) imp = imp.substring("static ".length()).trim(); // ignore static for types
          if (imp.endsWith(".*")) {
            wildcardPkgs.add(imp.substring(0, imp.length() - 2));
          } else {
            int lastDot = imp.lastIndexOf('.');
            if (lastDot > 0) {
              explicitImports.add(imp);
            }
          }
        }
      }
    }

    // Build quick lookups
    var simpleToFqns = se.alipsa.gade.code.completion.groovy.GroovyCompletionEngine.simpleNameIndex();
    var explicitSimple = new java.util.HashSet<String>();
    for (String fq : explicitImports) {
      String simple = fq.substring(fq.lastIndexOf('.') + 1);
      explicitSimple.add(simple);
    }

    // Precompute annotation name ranges so we can skip annotation identifiers reliably.
    var annotationRanges = new java.util.ArrayList<Range>();
    java.util.regex.Matcher ann = java.util.regex.Pattern.compile("@([A-Za-z_][A-Za-z0-9_]*)\\b").matcher(code);
    while (ann.find()) {
      annotationRanges.add(new Range(ann.start(1), ann.end(1)));
    }

    boolean hasGrab = code.contains("@Grab");

    // 2) Scan for candidate simple type names and test resolvability
    var out = new java.util.ArrayList<Range>();
    var m = SIMPLE_TYPE.matcher(code);
    while (m.find()) {
      String simple = m.group(1);

      // Skip if token is an annotation name
      boolean isAnnotation = false;
      for (Range ar : annotationRanges) {
        if (m.start(1) >= ar.start && m.end(1) <= ar.end) {
          isAnnotation = true;
          break;
        }
      }
      if (isAnnotation) continue;

      // Skip obvious non-type contexts
      if (simple.equals(simple.toUpperCase(Locale.ROOT))) continue;

      // If already imported explicitly, ok
      if (explicitSimple.contains(simple)) continue;

      // If it exists in default packages, ok
      if (resolvableFromDefaultPkgs(simple, DEFAULT_PKGS, simpleToFqns)) continue;

      // If available via wildcard import, ok
      if (resolvableFromWildcard(simple, wildcardPkgs, simpleToFqns)) continue;

      // If we have @Grab and this type could be in a wildcard package, skip
      if (hasGrab && isCoveredByWildcard(simple, wildcardPkgs)) continue;

      // If type defined in this file, ok
      if (definedLocally(code, simple)) continue;

      // Otherwise: mark unresolved
      out.add(new Range(m.start(1), m.end(1)));
    }
    return out;
  }

  // only skip if the wildcard could actually contain the type
  private boolean isCoveredByWildcard(String simple, java.util.Set<String> wildcardPkgs) {
    // We can't resolve it yet, but if there’s at least one wildcard import,
    // assume the class could be inside it when @Grab is used.
    // This avoids skipping too much in non-related packages.
    return !wildcardPkgs.isEmpty();
  }



  private boolean resolvableFromDefaultPkgs(String simple, List<String> pkgs, java.util.Map<String, java.util.List<String>> idx) {
    var fqns = idx.get(simple);
    if (fqns == null) return false;
    for (String fqn : fqns) {
      for (String pkg : pkgs) {
        if (fqn.startsWith(pkg + ".")) return true;
      }
    }
    return false;
  }

  private boolean resolvableFromWildcard(String simple, java.util.Set<String> wildcardPkgs,
                                         java.util.Map<String, java.util.List<String>> idx) {
    var fqns = idx.get(simple);
    if (fqns == null) return false;
    for (String fqn : fqns) {
      int lastDot = fqn.lastIndexOf('.');
      String pkg = lastDot > 0 ? fqn.substring(0, lastDot) : "";
      if (wildcardPkgs.contains(pkg)) return true;
    }
    return false;
  }

  private boolean definedLocally(String code, String name) {
    // quick & cheap: class/enum/interface/trait NAME
    return java.util.regex.Pattern.compile(
            "(?m)\\b(class|enum|interface|trait)\\s+" + java.util.regex.Pattern.quote(name) + "\\b")
        .matcher(code).find();
  }

  private void markUnusedImports() {
    final String code = getText();

    // Collect import entries with the exact range to style (just the identifier)
    record ImportUse(int start, int end, String id, Kind kind, String fqnOrClass) {
      enum Kind { NORMAL, ALIAS, STATIC_MEMBER, STATIC_STAR, WILDCARD_PKG }
    }
    java.util.List<ImportUse> imports = new java.util.ArrayList<>();

    // Normal + alias imports
    java.util.regex.Matcher mAlias = IMPORT_ALIAS.matcher(code);
    while (mAlias.find()) {
      String fqn   = mAlias.group(1);
      String alias = mAlias.group(2);
      int aliasStart = mAlias.start(2);
      int aliasEnd   = mAlias.end(2);
      imports.add(new ImportUse(aliasStart, aliasEnd, alias,
          ImportUse.Kind.ALIAS, fqn));
    }
    java.util.regex.Matcher mNorm = IMPORT_NORMAL.matcher(code);
    while (mNorm.find()) {
      // If it also matches alias pattern, skip here (already added above)
      if (IMPORT_ALIAS.matcher(mNorm.group(0)).matches()) continue;

      String fqn = mNorm.group(1);
      if (fqn.endsWith(".*")) {
        // Package wildcard: conservative — don't flag as unused
        imports.add(new ImportUse(-1, -1, "", ImportUse.Kind.WILDCARD_PKG, fqn));
      } else {
        int lastDot = fqn.lastIndexOf('.');
        String simple = lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
        // Style only the simple name portion
        int lineStart = mNorm.start(1);
        int simpleStart = lineStart + (lastDot >= 0 ? lastDot + 1 : 0);
        int simpleEnd   = lineStart + fqn.length();
        imports.add(new ImportUse(simpleStart, simpleEnd, simple,
            ImportUse.Kind.NORMAL, fqn));
      }
    }

    // Static imports
    Matcher mStat = IMPORT_STATIC.matcher(code);
    while (mStat.find()) {
      String cls = mStat.group(1);
      String member = mStat.group(2); // null if .* form

      if (member == null) {
        // static star: conservative — don't flag as unused
        imports.add(new ImportUse(-1, -1, "", ImportUse.Kind.STATIC_STAR, cls));
      } else {
        int memStart = mStat.start(2);
        int memEnd   = mStat.end(2);
        imports.add(new ImportUse(memStart, memEnd, member,
            ImportUse.Kind.STATIC_MEMBER, cls));
      }
    }

    // Build used identifier set (simple approach):
    //  - tokens in code, excluding imports and package lines
    //  - for class usage: we only count tokens NOT immediately preceded by '.'
    //    so "java.time.LocalDate" won't count as using LocalDate import
    final BitSet importLines = new BitSet(code.length());
    Matcher anyImportLine = java.util.regex.Pattern
        .compile("(?m)^\\s*import\\b.*$")
        .matcher(code);
    while (anyImportLine.find()) {
      importLines.set(anyImportLine.start(), anyImportLine.end());
    }
    Matcher pkgLine = Pattern
        .compile("(?m)^\\s*package\\b.*$")
        .matcher(code);
    while (pkgLine.find()) {
      importLines.set(pkgLine.start(), pkgLine.end());
    }

    Set<String> usedTokens = new HashSet<>();

    // --- Treat annotation names as used explicitly (handles @MyAnnotation)
    Matcher ann = java.util.regex.Pattern.compile("@([A-Za-z_][A-Za-z_0-9]*)\\b").matcher(code);
    while (ann.find()) {
      usedTokens.add(ann.group(1));
    }

    Matcher tok = Pattern
        .compile("\\b([A-Za-z_][A-Za-z_0-9]*)\\b")
        .matcher(code);
    while (tok.find()) {
      int s = tok.start(1);
      int e = tok.end(1);
      if (importLines.get(s, e).cardinality() > 0) {
        continue; // ignore tokens on import/package lines
      }
      // ignore tokens that are part of a qualified name: ".Name"
      if (s > 0 && code.charAt(s - 1) == '.') continue;

      String id = tok.group(1);
      usedTokens.add(id);
    }

    // Decide which imports are unused
    for (ImportUse iu : imports) {
      switch (iu.kind) {
        case NORMAL -> {
          // If the simple name never appears unqualified, consider unused
          if (!usedTokens.contains(iu.id)) {
            setStyle(iu.start, iu.end, java.util.List.of("unused-import"));
          }
        }
        case ALIAS -> {
          // Use of the alias name marks it as used
          if (!usedTokens.contains(iu.id)) {
            setStyle(iu.start, iu.end, java.util.List.of("unused-import"));
          }
        }
        case STATIC_MEMBER -> {
          // Only consider bare member usage (qualified Class.member means the import is redundant)
          if (!usedTokens.contains(iu.id)) {
            setStyle(iu.start, iu.end, java.util.List.of("unused-import"));
          }
        }
        case STATIC_STAR, WILDCARD_PKG -> {
          // Conservative: don't mark as unused (could be used in many places)
        }
      }
    }
  }

}
