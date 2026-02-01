package se.alipsa.gade.code.completion.groovy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.code.completion.ClasspathScanner;
import se.alipsa.gade.code.completion.CompletionContext;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves types of Groovy expressions for code completion.
 * Handles:
 * - Literal inference (12.0 → BigDecimal, "str" → String)
 * - Class name resolution (LocalDate → java.time.LocalDate)
 * - Method chain resolution (LocalDate.now().plusDays(1) → LocalDate)
 * - Basic variable tracking (def x = LocalDate.now())
 */
public final class GroovyTypeResolver {

  private static final Logger LOG = LogManager.getLogger(GroovyTypeResolver.class);

  // Patterns for literal detection
  private static final Pattern INTEGER_LITERAL = Pattern.compile("^-?\\d+[lLgG]?$");
  private static final Pattern DECIMAL_LITERAL = Pattern.compile("^-?\\d+\\.\\d*[dDfFgG]?$");
  private static final Pattern BIG_DECIMAL_LITERAL = Pattern.compile("^-?\\d+\\.\\d*[gG]$");
  private static final Pattern BIG_INTEGER_LITERAL = Pattern.compile("^-?\\d+[gG]$");
  private static final Pattern FLOAT_LITERAL = Pattern.compile("^-?\\d+\\.?\\d*[fF]$");
  private static final Pattern DOUBLE_LITERAL = Pattern.compile("^-?\\d+\\.\\d*[dD]?$");
  private static final Pattern LONG_LITERAL = Pattern.compile("^-?\\d+[lL]$");
  private static final Pattern STRING_LITERAL = Pattern.compile("^([\"']).*\\1$|^\"\"\".*\"\"\"$|^'''.*'''$", Pattern.DOTALL);
  private static final Pattern GSTRING_LITERAL = Pattern.compile("^\".*\\$.*\"$", Pattern.DOTALL);
  private static final Pattern LIST_LITERAL = Pattern.compile("^\\[.*\\]$", Pattern.DOTALL);
  private static final Pattern MAP_LITERAL = Pattern.compile("^\\[.*:.*\\]$", Pattern.DOTALL);
  private static final Pattern RANGE_LITERAL = Pattern.compile("^\\d+\\.\\.\\d+$");
  private static final Pattern BOOLEAN_LITERAL = Pattern.compile("^(true|false)$");

  // Pattern for method call: expr.method(args)
  private static final Pattern METHOD_CALL = Pattern.compile(
      "^(.+)\\.([\\p{L}_][\\p{L}\\p{N}_]*)\\(([^)]*)\\)$");

  // Pattern for method declaration (captures parameter list)
  private static final Pattern METHOD_DECL = Pattern.compile(
      "(?m)^\\s*(?:@[\\w$.]+\\s*)*" +
          "(?:public|protected|private|static|final|abstract|synchronized|native|def|[\\w$.<>\\[\\],\\s]+?)\\s+" +
          "[\\p{L}_][\\p{L}\\p{N}_]*\\s*\\(([^)]*)\\)\\s*\\{");

  private static final Pattern MATH_CALL = Pattern.compile(
      "\\bMath\\.[\\p{L}_][\\p{L}\\p{N}_]*\\s*\\(([^)]*)\\)");
  private static final Pattern MATH_CALL_INCOMPLETE = Pattern.compile(
      "\\bMath\\.[\\p{L}_][\\p{L}\\p{N}_]*\\s*\\(([^)]*)$");

  // Pattern for variable declaration: def/var/Type name = expr
  private static final Pattern VAR_DECL = Pattern.compile(
      "(?:def|var|final\\s+def|final\\s+var|([\\p{L}_][\\p{L}\\p{N}_.]*))" +
      "\\s+([\\p{L}_][\\p{L}\\p{N}_]*)\\s*=\\s*(.+?)\\s*$",
      Pattern.MULTILINE);

  // Common type mappings for Groovy defaults
  private static final Map<String, Class<?>> COMMON_TYPES = Map.ofEntries(
      Map.entry("String", String.class),
      Map.entry("Integer", Integer.class),
      Map.entry("Long", Long.class),
      Map.entry("Double", Double.class),
      Map.entry("Float", Float.class),
      Map.entry("Boolean", Boolean.class),
      Map.entry("BigDecimal", BigDecimal.class),
      Map.entry("BigInteger", BigInteger.class),
      Map.entry("List", List.class),
      Map.entry("ArrayList", ArrayList.class),
      Map.entry("Map", Map.class),
      Map.entry("HashMap", HashMap.class),
      Map.entry("LinkedHashMap", LinkedHashMap.class),
      Map.entry("Set", Set.class),
      Map.entry("HashSet", HashSet.class),
      Map.entry("Object", Object.class),
      Map.entry("Class", Class.class),
      Map.entry("File", java.io.File.class),
      Map.entry("Date", java.util.Date.class),
      Map.entry("LocalDate", java.time.LocalDate.class),
      Map.entry("LocalDateTime", java.time.LocalDateTime.class),
      Map.entry("LocalTime", java.time.LocalTime.class),
      Map.entry("Instant", java.time.Instant.class),
      Map.entry("Duration", java.time.Duration.class),
      Map.entry("Period", java.time.Period.class)
  );

  private GroovyTypeResolver() {}

  /**
   * Resolves the type of an expression.
   *
   * @param expression the expression to resolve (e.g., "12.0", "LocalDate.now()", "myVar")
   * @param context    completion context with variable bindings and classloader
   * @return the resolved Class, or null if unknown
   */
  public static Class<?> resolveType(String expression, CompletionContext context) {
    if (expression == null || expression.isBlank()) {
      return null;
    }

    String expr = expression.trim();

    // 1. Try literal inference
    Class<?> literalType = resolveLiteral(expr);
    if (literalType != null) {
      return literalType;
    }

    // 2. Try method call chain resolution
    if (expr.endsWith(")")) {
      Class<?> chainType = resolveMethodChain(expr, context);
      if (chainType != null) {
        return chainType;
      }
    }

    // 3. Try variable lookup from context
    Class<?> varType = resolveVariable(expr, context);
    if (varType != null) {
      return varType;
    }

    // 4. Try class name resolution
    return resolveClassName(expr, context.classLoader());
  }

  /**
   * Resolves the type of a literal expression.
   */
  public static Class<?> resolveLiteral(String expr) {
    if (expr == null || expr.isEmpty()) return null;

    // Boolean
    if (BOOLEAN_LITERAL.matcher(expr).matches()) {
      return Boolean.class;
    }

    // Groovy: untyped decimal literals are BigDecimal by default
    if (DECIMAL_LITERAL.matcher(expr).matches()) {
      if (BIG_DECIMAL_LITERAL.matcher(expr).matches()) {
        return BigDecimal.class;
      }
      if (FLOAT_LITERAL.matcher(expr).matches()) {
        return Float.class;
      }
      if (DOUBLE_LITERAL.matcher(expr).matches()) {
        // In Groovy, plain decimal without suffix is BigDecimal
        return BigDecimal.class;
      }
      return BigDecimal.class;
    }

    // Integer types
    if (INTEGER_LITERAL.matcher(expr).matches()) {
      if (BIG_INTEGER_LITERAL.matcher(expr).matches()) {
        return BigInteger.class;
      }
      if (LONG_LITERAL.matcher(expr).matches()) {
        return Long.class;
      }
      return Integer.class;
    }

    // Strings
    if (STRING_LITERAL.matcher(expr).matches()) {
      if (GSTRING_LITERAL.matcher(expr).matches()) {
        return groovy.lang.GString.class;
      }
      return String.class;
    }

    // Range
    if (RANGE_LITERAL.matcher(expr).matches()) {
      return groovy.lang.Range.class;
    }

    // Map (before list since [a:b] also matches [...])
    if (MAP_LITERAL.matcher(expr).matches()) {
      return LinkedHashMap.class;
    }

    // List
    if (LIST_LITERAL.matcher(expr).matches()) {
      return ArrayList.class;
    }

    return null;
  }

  /**
   * Resolves the type of a method call chain.
   * Example: "LocalDate.now().plusDays(1)" → LocalDate
   */
  public static Class<?> resolveMethodChain(String expr, CompletionContext context) {
    Matcher m = METHOD_CALL.matcher(expr);
    if (!m.matches()) {
      return null;
    }

    String baseExpr = m.group(1).trim();
    String methodName = m.group(2);
    String args = m.group(3).trim();

    // Recursively resolve base expression type
    Class<?> baseType;
    if (baseExpr.endsWith(")")) {
      baseType = resolveMethodChain(baseExpr, context);
    } else {
      // Try class name first, then variable
      baseType = resolveClassName(baseExpr, context.classLoader());
      if (baseType == null) {
        baseType = resolveVariable(baseExpr, context);
      }
    }

    if (baseType == null) {
      return null;
    }

    // Find method and return its return type
    Method method = findBestMethod(baseType, methodName, args);
    if (method != null) {
      Class<?> returnType = method.getReturnType();
      if (returnType != void.class && returnType != Void.TYPE) {
        return returnType;
      }
    }

    return null;
  }

  /**
   * Resolves a variable by scanning the code for its declaration.
   */
  public static Class<?> resolveVariable(String varName, CompletionContext context) {
    if (context == null || context.fullText() == null) {
      return null;
    }

    // Look for variable declarations before the caret
    String textBefore = context.textBeforeCaret();
    Matcher m = VAR_DECL.matcher(textBefore);

    String declaredType = null;
    String initExpr = null;

    while (m.find()) {
      String name = m.group(2);
      if (name.equals(varName)) {
        declaredType = m.group(1); // May be null for def/var
        initExpr = m.group(3);
      }
    }

    // If we found an explicit type declaration
    if (declaredType != null && !declaredType.isEmpty()) {
      Class<?> type = resolveClassName(declaredType, context.classLoader());
      if (type != null) {
        return type;
      }
    }

    // If we have an initializer, try to infer type from it
    if (initExpr != null) {
      // Avoid infinite recursion
      if (!initExpr.equals(varName)) {
        return resolveType(initExpr, context);
      }
    }

    // Try to resolve from enclosing method parameters
    ParamSpec paramSpec = findMethodParameterSpec(varName, textBefore);
    if (paramSpec != null) {
      if (paramSpec.typeName != null) {
        Class<?> type = resolveClassName(paramSpec.typeName, context.classLoader());
        if (type != null) {
          return type;
        }
      }
      if (paramSpec.defaultExpr != null && !paramSpec.defaultExpr.equals(varName)) {
        Class<?> type = resolveType(paramSpec.defaultExpr, context);
        if (type != null) {
          return type;
        }
      }

      Class<?> assignmentType = resolveAssignmentType(varName, textBefore, context);
      if (assignmentType != null) {
        return assignmentType;
      }

      Class<?> usageType = inferTypeFromUsage(varName, textBefore);
      if (usageType != null) {
        return usageType;
      }

      return Object.class;
    }

    Class<?> assignmentType = resolveAssignmentType(varName, textBefore, context);
    if (assignmentType != null) {
      return assignmentType;
    }

    return null;
  }

  private static ParamSpec findMethodParameterSpec(String varName, String textBefore) {
    if (varName == null || varName.isEmpty() || textBefore == null || textBefore.isEmpty()) {
      return null;
    }

    Matcher m = METHOD_DECL.matcher(textBefore);
    String params = null;
    while (m.find()) {
      params = m.group(1);
    }
    if (params == null || params.isBlank()) {
      return null;
    }

    for (ParamSpec spec : parseParamSpecs(params)) {
      if (varName.equals(spec.name)) {
        return spec;
      }
    }
    return null;
  }

  private static List<ParamSpec> parseParamSpecs(String params) {
    List<ParamSpec> result = new ArrayList<>();
    for (String raw : splitParams(params)) {
      ParamSpec spec = parseParamSpec(raw);
      if (spec != null) {
        result.add(spec);
      }
    }
    return result;
  }

  private static ParamSpec parseParamSpec(String raw) {
    if (raw == null) return null;
    String param = stripAnnotations(raw).trim();
    if (param.isEmpty()) return null;

    String defaultExpr = null;
    int eq = param.indexOf('=');
    if (eq >= 0) {
      defaultExpr = param.substring(eq + 1).trim();
      param = param.substring(0, eq).trim();
    }

    if (param.isEmpty()) return null;

    String[] parts = param.split("\\s+");
    if (parts.length == 1) {
      return new ParamSpec(parts[0], null, defaultExpr);
    }

    String name = parts[parts.length - 1];
    String typePart = String.join(" ", Arrays.copyOf(parts, parts.length - 1)).trim();
    String typeName = normalizeTypeName(typePart);
    return new ParamSpec(name, typeName, defaultExpr);
  }

  private static List<String> splitParams(String params) {
    List<String> out = new ArrayList<>();
    if (params == null) return out;
    StringBuilder current = new StringBuilder();
    int angleDepth = 0;
    for (int i = 0; i < params.length(); i++) {
      char c = params.charAt(i);
      if (c == '<') {
        angleDepth++;
        current.append(c);
        continue;
      }
      if (c == '>' && angleDepth > 0) {
        angleDepth--;
        current.append(c);
        continue;
      }
      if (c == ',' && angleDepth == 0) {
        out.add(current.toString());
        current.setLength(0);
        continue;
      }
      current.append(c);
    }
    if (current.length() > 0) {
      out.add(current.toString());
    }
    return out;
  }

  private static String stripAnnotations(String param) {
    String p = param;
    while (p.startsWith("@")) {
      String stripped = p.replaceFirst("^@\\w+(?:\\([^)]*\\))?\\s*", "");
      if (stripped.equals(p)) break;
      p = stripped.trim();
    }
    return p;
  }

  private static String normalizeTypeName(String type) {
    if (type == null) return null;
    String t = type.trim();
    if (t.isEmpty()) return null;

    t = t.replaceAll("\\b(final|def|var)\\b", "").trim();
    if (t.isEmpty()) return null;

    t = stripGenerics(t);
    t = t.replace("...", "").trim();
    while (t.endsWith("[]")) {
      t = t.substring(0, t.length() - 2).trim();
    }
    return t.isEmpty() ? null : t;
  }

  private static String stripGenerics(String type) {
    StringBuilder out = new StringBuilder();
    int depth = 0;
    for (int i = 0; i < type.length(); i++) {
      char c = type.charAt(i);
      if (c == '<') {
        depth++;
        continue;
      }
      if (c == '>' && depth > 0) {
        depth--;
        continue;
      }
      if (depth == 0) {
        out.append(c);
      }
    }
    return out.toString().trim().replaceAll("\\s+", " ");
  }

  private static Class<?> resolveAssignmentType(String varName, String textBefore, CompletionContext context) {
    if (varName == null || varName.isEmpty() || textBefore == null || textBefore.isEmpty()) {
      return null;
    }

    Pattern assignment = Pattern.compile(
        "(?m)\\b" + Pattern.quote(varName) + "\\b\\s*(?<![=!<>])=(?![=<>])\\s*(.+)$");
    Matcher m = assignment.matcher(textBefore);
    String rhs = null;
    while (m.find()) {
      rhs = m.group(1);
    }
    if (rhs == null) {
      return null;
    }
    rhs = stripTrailingComment(rhs);
    if (rhs.isEmpty() || rhs.contains(varName)) {
      return null;
    }
    return resolveType(rhs, context);
  }

  private static Class<?> inferTypeFromUsage(String varName, String textBefore) {
    if (varName == null || varName.isEmpty() || textBefore == null || textBefore.isEmpty()) {
      return null;
    }

    Matcher m = MATH_CALL.matcher(textBefore);
    while (m.find()) {
      if (containsWord(m.group(1), varName)) {
        return Number.class;
      }
    }
    m = MATH_CALL_INCOMPLETE.matcher(textBefore);
    if (m.find() && containsWord(m.group(1), varName)) {
      return Number.class;
    }

    Pattern numericOp = Pattern.compile(
        "\\b" + Pattern.quote(varName) + "\\b\\s*[+\\-*/%]|[+\\-*/%]\\s*\\b" + Pattern.quote(varName) + "\\b");
    if (numericOp.matcher(textBefore).find()) {
      return Number.class;
    }

    Pattern numericCmp = Pattern.compile(
        "\\b" + Pattern.quote(varName) + "\\b\\s*(?:<=|>=|<|>)\\s*\\d|\\d\\s*(?:<=|>=|<|>)\\s*\\b"
            + Pattern.quote(varName) + "\\b");
    if (numericCmp.matcher(textBefore).find()) {
      return Number.class;
    }

    Pattern stringConcat = Pattern.compile(
        "\\b" + Pattern.quote(varName) + "\\b\\s*\\+\\s*\"|\"\\s*\\+\\s*\\b" + Pattern.quote(varName) + "\\b");
    if (stringConcat.matcher(textBefore).find()) {
      return String.class;
    }

    return null;
  }

  private static boolean containsWord(String text, String word) {
    if (text == null || word == null || word.isEmpty()) {
      return false;
    }
    return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
  }

  private static String stripTrailingComment(String expr) {
    String trimmed = expr == null ? "" : expr.trim();
    int idx = trimmed.indexOf("//");
    if (idx >= 0) {
      trimmed = trimmed.substring(0, idx).trim();
    }
    if (trimmed.endsWith(";")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
    }
    return trimmed;
  }

  private static final class ParamSpec {
    private final String name;
    private final String typeName;
    private final String defaultExpr;

    private ParamSpec(String name, String typeName, String defaultExpr) {
      this.name = name;
      this.typeName = typeName;
      this.defaultExpr = defaultExpr;
    }
  }

  /**
   * Resolves a class name to a Class object.
   */
  public static Class<?> resolveClassName(String name, ClassLoader classLoader) {
    if (name == null || name.isEmpty()) {
      return null;
    }

    // Check common types first
    Class<?> common = COMMON_TYPES.get(name);
    if (common != null) {
      return common;
    }

    if (classLoader == null) {
      classLoader = GroovyTypeResolver.class.getClassLoader();
    }

    // Try as fully qualified name
    Class<?> cls = tryLoadClass(name, classLoader);
    if (cls != null) {
      return cls;
    }

    // Try common package prefixes
    String[] prefixes = {"java.lang.", "java.util.", "java.time.", "java.io.", "java.math.",
                         "groovy.lang.", "groovy.util."};
    for (String prefix : prefixes) {
      cls = tryLoadClass(prefix + name, classLoader);
      if (cls != null) {
        return cls;
      }
    }

    // Try classpath scanner
    List<String> fqcns = ClasspathScanner.getInstance().resolve(name, classLoader);
    for (String fqcn : fqcns) {
      cls = tryLoadClass(fqcn, classLoader);
      if (cls != null) {
        return cls;
      }
    }

    return null;
  }

  /**
   * Finds the best matching method for the given name and argument text.
   */
  private static Method findBestMethod(Class<?> clazz, String methodName, String args) {
    Method fallback = null;
    boolean wantNoArgs = args.isEmpty();

    for (Method m : clazz.getMethods()) {
      if (!m.getName().equals(methodName)) continue;

      if (wantNoArgs && m.getParameterCount() == 0) {
        return m; // Perfect match for no-arg call
      }

      if (fallback == null) {
        fallback = m;
      }
    }

    return fallback;
  }

  private static Class<?> tryLoadClass(String name, ClassLoader cl) {
    try {
      return Class.forName(name, false, cl);
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      return null;
    }
  }
}
