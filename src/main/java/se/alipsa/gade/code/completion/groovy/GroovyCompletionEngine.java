package se.alipsa.gade.code.completion.groovy;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import se.alipsa.gade.code.completion.CompletionItem;

public final class GroovyCompletionEngine {

  private static volatile java.util.Map<String, java.util.List<String>> SIMPLE_TO_FQCN;

  private GroovyCompletionEngine() {}

  private static final Set<String> GROOVY_KEYWORDS = new HashSet<>(Arrays.asList(
      "abstract","as","assert","boolean","break","byte","case","catch","char","class","const","continue",
      "def","default","do","double","else","enum","extends","false","final","finally","float","for",
      "goto","if","implements","import","in","instanceof","int","interface","long","native","new",
      "null","package","private","protected","public","return","short","static","strictfp","super",
      "switch","synchronized","this","throw","throws","trait","true","try","void","volatile","while","var"
  ));

  private static final String[] SCAN_PACKAGES = {
      "java.lang", "java.time", "Java.util",
      "groovy.lang", "groovy.util", "groovy.json",
      "groovy.xml", "groovy.transform", "groovy.sql",
      "se.alipsa", "org.apache.groovy"
  };

  private static final Pattern MEMBER_ACCESS = Pattern.compile("([\\p{L}_][\\p{L}\\p{N}_.]*)\\.(\\w*)$");

  public static List<CompletionItem> complete(String lastWord, String fullText, int caret) {
    final List<CompletionItem> out = new ArrayList<>();
    final String prefix = lastWord == null ? "" : lastWord;

    final String beforeCaret = fullText == null ? "" : fullText.substring(0, Math.max(0, caret));

    // --- Member access with optional prefix: e.g. "LocalDate.n" or "LocalDate." ---
    java.util.regex.Matcher m = MEMBER_ACCESS.matcher(beforeCaret);
    if (m.find()) {
      String target = m.group(1);                         // "LocalDate"
      String memberPrefix = m.group(2);                   // "n" ('' if just after the dot)
      String memberLow = memberPrefix.toLowerCase(java.util.Locale.ROOT);

      Class<?> cls = resolveClass(target);
      if (cls != null) {
        java.lang.reflect.Method[] methods = cls.getMethods();
        // stable-ish sort by name
        java.util.Arrays.sort(methods, java.util.Comparator.comparing(java.lang.reflect.Method::getName));

        // If it looks like a class reference, prefer static methods first
        boolean likelyClassRef = Character.isUpperCase(target.charAt(0)) || target.contains(".");

        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();

        // First pass: (optional) static-only when likely class ref
        if (likelyClassRef) {
          for (java.lang.reflect.Method method : methods) {
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
            String name = method.getName();
            if (!name.toLowerCase(java.util.Locale.ROOT).startsWith(memberLow)) continue;
            if (name.equals("wait") || name.equals("notify") || name.equals("notifyAll") || name.equals("getClass")) continue;
            if (seen.add(name)) {
              String params = buildParamList(method);
              out.add(new CompletionItem(name + "(", name + "(" + params + ")", CompletionItem.Kind.METHOD));
            }
          }
        }

        // Second pass: include instance methods (or all methods if not likely class ref)
        for (Method method : methods) {
          if (likelyClassRef && Modifier.isStatic(method.getModifiers())) continue; // already added
          String name = method.getName();
          if (!name.toLowerCase(java.util.Locale.ROOT).startsWith(memberLow)) continue;
          if (name.equals("wait") || name.equals("notify") || name.equals("notifyAll") || name.equals("getClass")) continue;
          if (seen.add(name)) {
            String params = buildParamList(method);
            out.add(new CompletionItem(name + "(", name + "(" + params + ")", CompletionItem.Kind.METHOD));
          }
        }

        return out; // member suggestions take precedence
      }
    }

    // --- Otherwise: keywords + classes from cached index ---
    final String low = prefix.toLowerCase(java.util.Locale.ROOT);

    // 1) Groovy keywords
    for (String kw : GROOVY_KEYWORDS) {
      if (kw.startsWith(low)) {
        out.add(new CompletionItem(kw, CompletionItem.Kind.KEYWORD));
      }
    }

    // 2) Classes via cached index (simpleName -> [fqcn...], preferred packages first)
    final int cap = 200; // keep menu lean
    int count = 0;
    Map<String, java.util.List<String>> idx = simpleIndex(); // builds once, caches

    outer:
    for (Map.Entry<String, java.util.List<String>> e : idx.entrySet()) {
      String simple = e.getKey();
      if (simple != null && simple.toLowerCase(java.util.Locale.ROOT).startsWith(low)) {
        for (String fqn : e.getValue()) {
          out.add(new CompletionItem(simple, fqn, CompletionItem.Kind.CLASS));
          if (++count >= cap) break outer;
        }
      }
    }

    return out;
  }


  private static String buildParamList(Method m) {
    Class<?>[] p = m.getParameterTypes();
    if (p.length == 0) return ")";
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < p.length; i++) {
      b.append(simple(p[i])).append(" arg").append(i + 1);
      if (i < p.length - 1) b.append(", ");
    }
    b.append(")");
    return b.toString();
  }

  private static String simple(Class<?> c) {
    if (c.isArray()) {
      return simple(c.getComponentType()) + "[]";
    }
    return c.getSimpleName();
  }

  // build a simpleName -> [fqcn, ...] index once, ordered by preferred packages
  private static Map<String, List<String>> simpleIndex() {
    Map<String, List<String>> idx = SIMPLE_TO_FQCN;
    if (idx != null) return idx;
    synchronized (GroovyCompletionEngine.class) {
      if (SIMPLE_TO_FQCN != null) return SIMPLE_TO_FQCN;

      Map<String, List<String>> m = new HashMap<>();
      try (io.github.classgraph.ScanResult sr = new io.github.classgraph.ClassGraph()
          .enableClassInfo()
          .acceptPackages(SCAN_PACKAGES)  // <--- your proposal fits perfectly here
          .scan()) {
        for (io.github.classgraph.ClassInfo ci : sr.getAllClasses()) {
          String simple = ci.getSimpleName();
          if (simple == null || simple.isEmpty()) continue;
          m.computeIfAbsent(simple, k -> new ArrayList<>()).add(ci.getName());
        }
        // Preferred packages first
        Comparator<String> byPreference = Comparator.comparingInt(fqn -> {
          for (int i = 0; i < SCAN_PACKAGES.length; i++) {
            if (fqn.startsWith(SCAN_PACKAGES[i] + ".")) return i;
          }
          return SCAN_PACKAGES.length;
        });
        for (List<String> list : m.values()) list.sort(byPreference);
      } catch (Throwable ignore) {
        // keep index empty on failure; keywords-only completion still works
      }
      SIMPLE_TO_FQCN = m;
      return m;
    }
  }

  private static Class<?> tryLoad(String name, ClassLoader cl) {
    try {
      // false => don't run static initializers; keeps it cheap
      return Class.forName(name, false, cl);
    } catch (Throwable ignore) {
      return null;
    }
  }

  // REPLACE your existing resolveClass(...) with this:
  private static Class<?> resolveClass(String token) {
    if (token == null || token.isEmpty()) return null;

    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) cl = GroovyCompletionEngine.class.getClassLoader();

    // 1) Fully qualified
    Class<?> c = tryLoad(token, cl);
    if (c != null) return c;

    // 2) Fast common packages (no scan)
    for (String pkg : new String[] {"java.lang", "groovy.lang"}) {
      c = tryLoad(pkg + "." + token, cl);
      if (c != null) return c;
    }

    // 3) Fallback: cached ClassGraph index for simple names
    java.util.List<String> candidates = simpleIndex().get(token);
    if (candidates != null) {
      for (String fqn : candidates) {
        c = tryLoad(fqn, cl);
        if (c != null) return c;
      }
    }
    return null;
  }
}
