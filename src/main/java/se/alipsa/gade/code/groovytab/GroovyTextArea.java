package se.alipsa.gade.code.groovytab;

import io.github.classgraph.*;
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

  TreeSet<String> contextObjects = new TreeSet<>();

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
          /*
          if (parent.getGui().getPrefs().getBoolean(ADD_IMPORTS, true)) {
            gCode += getImports();
          }*/
          String selected = selectedTextProperty().getValue();
          // if text is selected then go with that
          if (selected != null && !"".equals(selected)) {
            gCode += codeComponent.getTextFromActiveTab();
          } else {
            gCode += getText(getCurrentParagraph()); // current line
          }
          if (parent instanceof GroovyTab groovyTab) {
            groovyTab.runGroovy(gCode);
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
    String currentText = line.substring(0, getCaretColumn());
    String lastWord;
    int index = currentText.indexOf(' ');
    if (index == -1 ) {
      lastWord = currentText;
    } else {
      lastWord = currentText.substring(currentText.lastIndexOf(' ') + 1);
    }
    index = lastWord.indexOf(',');
    if (index > -1) {
      lastWord = lastWord.substring(index+1);
    }
    index = lastWord.indexOf('(');
    if (index > -1) {
      lastWord = lastWord.substring(index+1);
    }
    index = lastWord.indexOf('[');
    if (index > -1) {
      lastWord = lastWord.substring(index+1);
    }
    index = lastWord.indexOf('{');
    if (index > -1) {
      lastWord = lastWord.substring(index+1);
    }

    //Gade.instance().getConsoleComponent().getConsole().appendFx("lastWord is " + lastWord, true);

    if (lastWord.length() > 0) {
      suggestCompletion(lastWord);
    }
  }

  private void suggestCompletion(String lastWord) {
    var consoleComponent = Gade.instance().getConsoleComponent();
    var console =  consoleComponent.getConsole();
    //console.appendFx("Getting suggestions for " + lastWord, true);
    TreeMap<String, Boolean> suggestions = new TreeMap<>();

    var contextObjects = Gade.instance().getConsoleComponent().getContextObjects();

    for (Map.Entry<String, Object> contextObject: contextObjects.entrySet()) {
      String key = contextObject.getKey();
      if (key.equals(lastWord)) {
        suggestions.put(".", Boolean.FALSE);
      } else if (key.startsWith(lastWord)) {
        suggestions.put(key, Boolean.FALSE);
      } else if (lastWord.startsWith(key) && lastWord.contains(".")) {
        int firstDot = lastWord.indexOf('.');
        String varName = lastWord.substring(0, lastWord.indexOf('.'));
        if (key.equals(varName)){
          suggestions.putAll(getInstanceMethods(contextObject.getValue(), lastWord.substring(firstDot+1)));
        }
      }
    }
    if (suggestions.size() > 0) {
      suggestCompletion(lastWord, suggestions, suggestionsPopup);
      return;
    }

    // Else it is probably package or Class related
    String searchWord = lastWord;
    boolean endsWithDot = false;
    if (searchWord.endsWith(".")) {
      searchWord = searchWord.substring(0, searchWord.length() -1);
      endsWithDot = true;
    }
    ClassLoader cl = Gade.instance().getConsoleComponent().getClassLoader();
    try {
      Class<?> clazz = cl.loadClass(searchWord);
      suggestions.putAll(getStaticMethods(clazz));
    } catch (ClassNotFoundException e) {
      try (ScanResult scanResult = new ClassGraph().enableClassInfo().addClassLoader(cl).scan()) {
        String finalSearchWord = searchWord;
        List<? extends Class<?>> exactMatches = scanResult.getAllClasses().stream()
            .filter(ci -> ci.getSimpleName().equals(finalSearchWord)).map(ClassInfo::loadClass)
            .toList();
        if (exactMatches.size() == 1) {
          String prefix = endsWithDot ? "" : ".";
          lastWord = endsWithDot ? lastWord : lastWord + ".";
          suggestions.putAll(getStaticMethods(exactMatches.get(0), prefix));
        } else if (exactMatches.size() > 1){
          console.appendWarningFx("Multiple matches for this class detected, cannot determine which one is meant");
          return;
        } else {
          List<String> possiblePackages = scanResult.getPackageInfo().stream()
              .map(PackageInfo::getName)
              .filter(name -> name.startsWith(finalSearchWord))
              .toList();
          if (possiblePackages.size() > 0) {
            Map<String, Boolean> packages = new TreeMap<>();
            lastWord = endsWithDot ? lastWord : lastWord + ".";
            for (String pkg : possiblePackages) {
              String suggestion = pkg.substring(finalSearchWord.length());
              if (endsWithDot && suggestion.startsWith(".")) {
                suggestion = suggestion.substring(1);
              }
              packages.put(suggestion, Boolean.FALSE);
            }
            suggestions.putAll(packages);
          }
        }
      }
    }
    if (suggestions.size() > 0) {
      suggestCompletion(lastWord, suggestions, suggestionsPopup);
    } else {
      console.appendFx("No matches found for " + searchWord, true);
    }
  }

  private Map<String, Boolean> getStaticMethods(Class<?> clazz, String... prefixOpt) {
    String prefix = prefixOpt.length > 0 ? prefixOpt[0] : "";
    Map<String, Boolean> staticMethods = new TreeMap<>();
    for(Method method : clazz.getMethods()) {
      //Gade.instance().getConsoleComponent().getConsole().appendFx(method.getName() + " and startWith '" + start + "'");
      if ( Modifier.isStatic(method.getModifiers())) {
        Boolean hasParams = method.getParameterCount() > 0;
        String suggestion = method.getName() + "()";
        if (Boolean.TRUE.equals(staticMethods.get(suggestion))) {
          hasParams = Boolean.TRUE;
        }
        staticMethods.put(prefix + suggestion, hasParams);
      }
    }
    return staticMethods;
  }

  private Map<String, Boolean> getInstanceMethods(Object obj, String start) {
    Map<String, Boolean> instanceMethods = new TreeMap<>();
    for(Method method : obj.getClass().getMethods()) {
      //Gade.instance().getConsoleComponent().getConsole().appendFx(method.getName() + " and startWith '" + start + "'");
      if ( !Modifier.isStatic(method.getModifiers()) && ("".equals(start) || method.getName().startsWith(start))) {
        Boolean hasParams = method.getParameterCount() > 0;
        String suggestion = method.getName() + "()";
        if (Boolean.TRUE.equals(instanceMethods.get(suggestion))) {
          hasParams = Boolean.TRUE;
        }
        instanceMethods.put(suggestion, hasParams);
      }
    }
    return instanceMethods;
  }

}
