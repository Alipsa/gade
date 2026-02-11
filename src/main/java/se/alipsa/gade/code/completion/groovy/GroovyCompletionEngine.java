package se.alipsa.gade.code.completion.groovy;

import groovy.lang.Script;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import se.alipsa.gade.code.completion.ClasspathScanner;
import se.alipsa.gade.code.completion.CompletionContext;
import se.alipsa.gade.code.completion.CompletionEngine;
import se.alipsa.gade.code.completion.CompletionItem;

/**
 * Groovy code completion engine providing:
 * - Keyword completion
 * - Class completion (from classpath including @Grab dependencies)
 * - Member access completion (methods, fields)
 * - Groovy extension method completion (DefaultGroovyMethods, etc.)
 * - Type inference for literals and method chains
 */
public final class GroovyCompletionEngine implements CompletionEngine {

  private static final Set<String> SUPPORTED_LANGUAGES = Set.of("groovy");

  private static final Set<String> GROOVY_KEYWORDS = new HashSet<>(Arrays.asList(
      "abstract", "as", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
      "def", "default", "do", "double", "else", "enum", "extends", "false", "final", "finally", "float", "for",
      "goto", "if", "implements", "import", "in", "instanceof", "int", "interface", "long", "native", "new",
      "null", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super",
      "switch", "synchronized", "this", "throw", "throws", "trait", "true", "try", "void", "volatile", "while", "var"
  ));

  private static final Map<String, Boolean> IMPLICIT_METHODS = Map.ofEntries(
      Map.entry("println", true),
      Map.entry("print", true),
      Map.entry("printf", true),
      Map.entry("sprintf", true)
  );

  // Methods to exclude from completion
  private static final Set<String> EXCLUDED_METHODS = Set.of(
      "wait", "notify", "notifyAll", "getClass"
  );

  private static final Pattern MEMBER_ACCESS =
      Pattern.compile("([\\p{L}_][\\p{L}\\p{N}_]*(?:\\([^)]*\\))?(?:\\.[\\p{L}_][\\p{L}\\p{N}_]*(?:\\([^)]*\\))?)*)\\.(\\w*)\\s*$");

  // Pattern to detect import statement: import [static] package.path
  private static final Pattern IMPORT_STATEMENT =
      Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]*?)$", Pattern.MULTILINE);

  // Singleton instance for registry
  private static volatile GroovyCompletionEngine instance;

  public GroovyCompletionEngine() {}

  /**
   * Returns the singleton instance for registration.
   */
  public static GroovyCompletionEngine getInstance() {
    if (instance == null) {
      synchronized (GroovyCompletionEngine.class) {
        if (instance == null) {
          instance = new GroovyCompletionEngine();
        }
      }
    }
    return instance;
  }

  @Override
  public Set<String> supportedLanguages() {
    return SUPPORTED_LANGUAGES;
  }

  @Override
  public String name() {
    return "GroovyCompletionEngine";
  }

  @Override
  public void invalidateCache() {
    ClasspathScanner.getInstance().invalidateAll();
    GroovyExtensionMethods.invalidateCache();
  }

  @Override
  public List<CompletionItem> complete(CompletionContext context) {
    List<CompletionItem> out = new ArrayList<>();

    // Don't complete inside strings or comments
    if (context.isInsideString() || context.isInsideComment()) {
      return out;
    }

    String textBefore = context.textBeforeCaret();

    // Check for import statement context first
    String currentLine = context.lineText();
    if (currentLine != null && currentLine.trim().startsWith("import")) {
      completeImport(context, out);
      if (!out.isEmpty()) {
        return out;
      }
    }

    // Check for member access context
    Matcher m = MEMBER_ACCESS.matcher(textBefore);
    if (m.find()) {
      String target = m.group(1);       // e.g., "LocalDateTime" or "LocalDateTime.now()"
      String memberPrefix = m.group(2); // may be "" right after the '.'

      // Resolve the type of the target expression
      Class<?> cls = GroovyTypeResolver.resolveType(target, context);

      if (cls != null) {
        completeMemberAccess(cls, target, memberPrefix, out);

        // Also add Groovy extension methods
        List<GroovyExtensionMethods.ExtensionMethod> extensions =
            GroovyExtensionMethods.getExtensionMethods(cls, memberPrefix);
        out.addAll(GroovyExtensionMethods.toCompletionItems(extensions));

        return out;
      } else {
        // Couldn't resolve target; don't fall back to keywords
        return out;
      }
    }

    // Not in member context: keywords + classes + implicit receiver methods
    String prefix = context.tokenPrefix();
    String low = (prefix == null ? "" : prefix).toLowerCase(Locale.ROOT);

    if (!low.isEmpty()) {
      completeMemberAccess(Script.class, "this", prefix, out);
      addImplicitMethodCompletions(low, out);
    }

    // Add keywords
    for (String kw : GROOVY_KEYWORDS) {
      if (kw.startsWith(low)) {
        out.add(CompletionItem.builder()
            .completion(kw)
            .kind(CompletionItem.Kind.KEYWORD)
            .sortPriority(50)
            .build());
      }
    }

    // Add classes from classpath scanner (includes @Grab, Maven/Gradle dependencies)
    Map<String, List<String>> classIndex = ClasspathScanner.getInstance().scan(context.classLoader());
    int count = 0;
    int cap = 200;

    for (Map.Entry<String, List<String>> e : classIndex.entrySet()) {
      String simpleName = e.getKey();
      if (simpleName != null && simpleName.toLowerCase(Locale.ROOT).startsWith(low)) {
        for (String fqcn : e.getValue()) {
          out.add(CompletionItem.builder()
              .completion(simpleName)
              .display(simpleName)
              .kind(CompletionItem.Kind.CLASS)
              .detail(fqcn)
              .sortPriority(100)
              .build());
          if (++count >= cap) break;
        }
      }
      if (count >= cap) break;
    }

    return out;
  }

  /**
   * Backward-compatible static method for existing code.
   * Prefer using complete(CompletionContext) for new code.
   */
  public static List<CompletionItem> complete(String lastWord, String fullText, int caret) {
    CompletionContext context = CompletionContext.builder()
        .fullText(fullText)
        .caretPosition(caret)
        .tokenPrefix(lastWord)
        .build();
    return getInstance().complete(context);
  }

  /**
   * Returns the simple name to FQCN index for external use (e.g., import suggestions).
   */
  public static Map<String, List<String>> simpleNameIndex() {
    return simpleNameIndex(null);
  }

  /**
   * Returns the simple name to FQCN index using the given classloader.
   *
   * @param cl the classloader to scan; if null, falls back to the context classloader
   */
  public static Map<String, List<String>> simpleNameIndex(ClassLoader cl) {
    if (cl == null) {
      cl = Thread.currentThread().getContextClassLoader();
    }
    return ClasspathScanner.getInstance().scan(cl);
  }

  private void completeMemberAccess(Class<?> cls, String target, String memberPrefix,
                                    List<CompletionItem> out) {
    String memberLow = memberPrefix.toLowerCase(Locale.ROOT);
    Method[] methods = cls.getMethods();
    Arrays.sort(methods, Comparator.comparing(Method::getName));

    boolean isStaticContext = Character.isUpperCase(target.charAt(0)) || target.contains(".");
    Set<String> seen = new LinkedHashSet<>();
    Map<String, Boolean> hasParamsByName = new HashMap<>();

    for (Method method : methods) {
      boolean hasParams = method.getParameterCount() > 0;
      hasParamsByName.merge(method.getName(), hasParams, (a, b) -> a || b);
    }

    // Static fields (if static context)
    if (isStaticContext) {
      for (Field f : cls.getFields()) {
        if (!Modifier.isStatic(f.getModifiers())) continue;
        addFieldCompletion(f, memberLow, seen, out, 60);
      }
    }

    // Static methods (if static context)
    if (isStaticContext) {
      for (Method method : methods) {
        if (!Modifier.isStatic(method.getModifiers())) continue;
        addMethodCompletion(method, memberLow, seen, out, 70, hasParamsByName);
      }
    }

    // Instance fields
    for (Field f : cls.getFields()) {
      if (isStaticContext && Modifier.isStatic(f.getModifiers())) continue;
      addFieldCompletion(f, memberLow, seen, out, 80);
    }

    // Instance methods (or all if not static context)
    for (Method method : methods) {
      if (isStaticContext && Modifier.isStatic(method.getModifiers())) continue;
      addMethodCompletion(method, memberLow, seen, out, 90, hasParamsByName);
    }
  }

  private void addImplicitMethodCompletions(String lowPrefix, List<CompletionItem> out) {
    if (lowPrefix == null || lowPrefix.isEmpty()) return;
    Set<String> existing = new HashSet<>();
    for (CompletionItem item : out) {
      if (item.completion() != null) {
        existing.add(item.completion());
      }
    }

    for (Map.Entry<String, Boolean> entry : IMPLICIT_METHODS.entrySet()) {
      String name = entry.getKey();
      if (!name.startsWith(lowPrefix)) continue;
      if (!existing.add(name)) continue;
      boolean hasParams = entry.getValue();
      int cursorOffset = hasParams ? -1 : 0;
      out.add(CompletionItem.builder()
          .completion(name)
          .display(name + "()")
          .kind(CompletionItem.Kind.METHOD)
          .detail("implicit")
          .insertText(name + "()")
          .sortPriority(80)
          .cursorOffset(cursorOffset)
          .build());
    }
  }

  /**
   * Completes import statements by suggesting packages and classes.
   * For "import groovy.tr" suggests "groovy.transform", etc.
   */
  private void completeImport(CompletionContext context, List<CompletionItem> out) {
    String line = context.lineText().trim();

    // Extract the partial import path
    String importPath = "";
    if (line.startsWith("import static ")) {
      importPath = line.substring("import static ".length()).trim();
    } else if (line.startsWith("import ")) {
      importPath = line.substring("import ".length()).trim();
    }

    if (importPath.isEmpty()) {
      return; // Need at least some input
    }

    // Split into complete package parts and partial last segment
    // e.g., "groovy.tr" -> basePath="groovy", partialSegment="tr"
    // e.g., "groovy.transform." -> basePath="groovy.transform", partialSegment=""
    int lastDot = importPath.lastIndexOf('.');
    String basePath;
    String partialSegment;

    if (lastDot >= 0) {
      basePath = importPath.substring(0, lastDot);
      partialSegment = importPath.substring(lastDot + 1);
    } else {
      basePath = "";
      partialSegment = importPath;
    }

    String basePathLower = basePath.toLowerCase(Locale.ROOT);
    String partialLower = partialSegment.toLowerCase(Locale.ROOT);

    Map<String, List<String>> classIndex = ClasspathScanner.getInstance().scan(context.classLoader());

    Set<String> addedPackages = new HashSet<>();
    Set<String> addedClasses = new HashSet<>();
    int count = 0;
    int cap = 100;

    for (List<String> fqcns : classIndex.values()) {
      for (String fqcn : fqcns) {
        String fqcnLower = fqcn.toLowerCase(Locale.ROOT);

        // Check if FQCN is under the base path (or at root if basePath is empty)
        if (!basePath.isEmpty()) {
          if (!fqcnLower.startsWith(basePathLower + ".")) continue;
        }

        // Get the part after basePath
        String afterBase = basePath.isEmpty() ? fqcn : fqcn.substring(basePath.length() + 1);
        String afterBaseLower = afterBase.toLowerCase(Locale.ROOT);

        // Check if the next segment starts with our partial
        if (!afterBaseLower.startsWith(partialLower)) continue;

        // Find the next dot to determine if this is a package or class
        int nextDot = afterBase.indexOf('.');

        if (nextDot > 0) {
          // There's more path - extract the next package segment
          String nextSegment = afterBase.substring(0, nextDot);
          String fullPackage = basePath.isEmpty() ? nextSegment : basePath + "." + nextSegment;

          if (addedPackages.add(fullPackage)) {
            // Use nextSegment for both filtering and insertion
            // (we're only replacing the partial segment the user typed)
            out.add(CompletionItem.builder()
                .completion(nextSegment)  // For filtering: matches "tr" -> "transform"
                .display(fullPackage)      // Show full path in popup
                .kind(CompletionItem.Kind.MODULE)
                .detail("package")
                .insertText(nextSegment)   // Insert just the segment to replace partial
                .sortPriority(50)
                .build());
            if (++count >= cap) return;
          }
        } else {
          // This is the class name directly under basePath
          String className = afterBase;  // The simple class name
          if (addedClasses.add(fqcn)) {
            out.add(CompletionItem.builder()
                .completion(className)     // For filtering: matches partial class name
                .display(fqcn)             // Show full FQCN in popup
                .kind(CompletionItem.Kind.CLASS)
                .insertText(className)     // Insert just class name to replace partial
                .sortPriority(100)
                .build());
            if (++count >= cap) return;
          }
        }
      }
    }

    // Sort: packages first, then classes
    out.sort(Comparator.comparingInt(CompletionItem::sortPriority)
        .thenComparing(CompletionItem::completion));
  }

  private void addFieldCompletion(Field f, String prefix, Set<String> seen,
                                  List<CompletionItem> out, int priority) {
    String name = f.getName();
    if (!name.toLowerCase(Locale.ROOT).startsWith(prefix)) return;
    if (!seen.add("F:" + name)) return;

    out.add(CompletionItem.builder()
        .completion(name)
        .display(name + " : " + simpleName(f.getType()))
        .kind(CompletionItem.Kind.FIELD)
        .sortPriority(priority)
        .build());
  }

  private void addMethodCompletion(Method method, String prefix, Set<String> seen,
                                   List<CompletionItem> out, int priority,
                                   Map<String, Boolean> hasParamsByName) {
    String name = method.getName();
    if (!name.toLowerCase(Locale.ROOT).startsWith(prefix)) return;
    if (EXCLUDED_METHODS.contains(name)) return;
    if (!seen.add("M:" + name)) return;

    // Insert just methodName() - user fills in parameters themselves
    String insertText = name + "()";
    // Display shows full signature so user knows what parameters are expected
    String display = name + "(" + paramSig(method) + ") : " + simpleName(method.getReturnType());

    // For methods with parameters, cursor inside (); otherwise after ()
    boolean hasParams = hasParamsByName.getOrDefault(name, method.getParameterCount() > 0);
    int cursorOffset = hasParams ? -1 : 0;

    out.add(CompletionItem.builder()
        .completion(name)
        .display(display)
        .kind(CompletionItem.Kind.METHOD)
        .insertText(insertText)
        .sortPriority(priority)
        .cursorOffset(cursorOffset)
        .build());
  }

  private static String paramSig(Method m) {
    Class<?>[] p = m.getParameterTypes();
    if (p.length == 0) return "";
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < p.length; i++) {
      if (i > 0) b.append(", ");
      b.append(simpleName(p[i]));
    }
    return b.toString();
  }

  private static String simpleName(Class<?> c) {
    if (c.isArray()) {
      return simpleName(c.getComponentType()) + "[]";
    }
    return c.getSimpleName();
  }
}
