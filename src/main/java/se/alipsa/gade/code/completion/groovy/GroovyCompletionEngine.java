package se.alipsa.gade.code.completion.groovy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import  java.util.regex.Matcher;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
      "java.lang", "java.time", "java.util",
      "groovy.lang", "groovy.util", "groovy.json",
      "groovy.xml", "groovy.transform", "groovy.sql",
      "se.alipsa", "org.apache.groovy"
  };

  private static final Pattern MEMBER_ACCESS =
      Pattern.compile("([\\p{L}_][\\p{L}\\p{N}_]*(?:\\.[\\p{L}_][\\p{L}\\p{N}_]*)*)\\.(\\w*)\\s*$");



  public static List<CompletionItem> complete(String lastWord, String fullText, int caret) {
    final List<CompletionItem> out = new ArrayList<>();
    final String beforeCaret = fullText == null ? "" : fullText.substring(0, Math.max(0, caret));

    // --- Member access (handles empty prefix, e.g. "LocalDateTime." and chained calls) ---
    boolean inMemberContext = false;
    Matcher m = MEMBER_ACCESS.matcher(beforeCaret);
    if (m.find()) {
      inMemberContext = true;
      String target = m.group(1);       // e.g. "LocalDateTime" or "foo()" or "LocalDateTime.now()"
      String memberPrefix = m.group(2); // may be "" right after the '.'

      // Try to infer type from chained calls, otherwise resolve simple class
      Class<?> cls = inferType(target);
      if (cls == null) cls = resolveClass(target);

      if (cls != null) {
        final String memberLow = memberPrefix.toLowerCase(Locale.ROOT);
        var methods = cls.getMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));

        boolean classLike = Character.isUpperCase(target.charAt(0)) || target.contains(".");
        var seen = new java.util.LinkedHashSet<String>();

        // Static fields first if class-like
        if (classLike) {
          for (var f : cls.getFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            String name = f.getName();
            if (!name.toLowerCase(Locale.ROOT).startsWith(memberLow)) continue;
            if (seen.add("F:" + name)) {
              out.add(new CompletionItem(
                  name,                                // completion
                  name + " : " + simple(f.getType()),  // label
                  CompletionItem.Kind.FIELD));
            }
          }
        }

        // Static methods (class-like)
        if (classLike) {
          for (var method : methods) {
            if (!Modifier.isStatic(method.getModifiers())) continue;
            String name = method.getName();
            if (!name.toLowerCase(Locale.ROOT).startsWith(memberLow)) continue;
            if (name.equals("wait") || name.equals("notify") || name.equals("notifyAll") || name.equals("getClass")) continue;
            if (seen.add("M:" + name)) {
              String params = buildParamList(method); // NOTE: returns a string that already ENDS with ')'
              String insert = name + "(" + params;    // so do NOT add another ')'
              String label  = name + "(" + paramSig(method) + ") : " + simple(method.getReturnType());

              out.add(new CompletionItem(insert, label, CompletionItem.Kind.METHOD));
            }
          }
        }

        // Instance fields
        for (var f : cls.getFields()) {
          if (classLike && Modifier.isStatic(f.getModifiers())) continue;
          String name = f.getName();
          if (!name.toLowerCase(Locale.ROOT).startsWith(memberLow)) continue;
          if (seen.add("F:" + name)) {
            out.add(new CompletionItem(
                name + " : " + simple(f.getType()), // label (shows type)
                name,                                // what to insert
                CompletionItem.Kind.FIELD));
          }
        }

        // Instance methods (or all if not class-like)
        for (var method : methods) {
          if (classLike && Modifier.isStatic(method.getModifiers())) continue;
          String name = method.getName();
          if (!name.toLowerCase(Locale.ROOT).startsWith(memberLow)) continue;
          if (name.equals("wait") || name.equals("notify") || name.equals("notifyAll") || name.equals("getClass")) continue;
          if (seen.add("M:" + name)) {
            String insert = name + "(" + buildParamList(method);           // e.g. now()
            String label  = name + "(" + paramSig(method) + ") : "          // e.g. now() : LocalDateTime
                + simple(method.getReturnType());

            out.add(new CompletionItem(insert, label, CompletionItem.Kind.METHOD));
          }
        }
        return out; // IMPORTANT: in member context, return immediately
      } else {
        // Couldn’t resolve target; still in member context -> do not fall back to keywords
        return out;
      }
    }

    // --- Not in member context: keywords + classes as before ---
    final String prefix = (lastWord == null ? "" : lastWord);
    final String low = prefix.toLowerCase(Locale.ROOT);

    for (String kw : GROOVY_KEYWORDS) {
      if (kw.startsWith(low)) {
        out.add(new CompletionItem(kw, CompletionItem.Kind.KEYWORD));
      }
    }

    int cap = 200, count = 0;
    for (var e : simpleIndex().entrySet()) {
      String simple = e.getKey();
      if (simple != null && simple.toLowerCase(Locale.ROOT).startsWith(low)) {
        for (String fqn : e.getValue()) {
          out.add(new CompletionItem(simple, fqn, CompletionItem.Kind.CLASS));
          if (++count >= cap) break;
        }
      }
      if (count >= cap) break;
    }
    return out;
  }

  public static Map<String, List<String>> simpleNameIndex() {
    return simpleIndex(); // returns the cached simpleName -> [fqcn, ...]
  }

  private static String paramSig(Method m) {
    Class<?>[] p = m.getParameterTypes();
    if (p.length == 0) return "";
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < p.length; i++) {
      b.append(simple(p[i]));
      if (i < p.length - 1) b.append(", ");
    }
    return b.toString();
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
      java.util.Map<String, java.util.List<String>> m = new java.util.HashMap<>();
      try (io.github.classgraph.ScanResult sr = new io.github.classgraph.ClassGraph()
          .enableClassInfo()
          .enableSystemJarsAndModules()
          .acceptPackages(SCAN_PACKAGES)
          .scan()) {
        for (io.github.classgraph.ClassInfo ci : sr.getAllClasses()) {
          String simple = ci.getSimpleName();
          if (simple == null || simple.isEmpty()) continue;
          m.computeIfAbsent(simple, k -> new java.util.ArrayList<>()).add(ci.getName());
        }
        java.util.Comparator<String> byPref = java.util.Comparator.comparingInt(fqn -> {
          for (int i = 0; i < SCAN_PACKAGES.length; i++) {
            if (fqn.startsWith(SCAN_PACKAGES[i] + ".")) return i;
          }
          return SCAN_PACKAGES.length;
        });
        for (java.util.List<String> list : m.values()) list.sort(byPref);
      } catch (Throwable ignore) {}
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

  // Infer the Class<?> of an expression like "ClassName.staticMethod(...)" or "...).method(...)"
  private static Class<?> inferType(String expr) {
    if (expr == null) return null;
    String s = expr.trim();
    if (!s.endsWith(")")) {
      // not a call, try to resolve as a class
      return resolveClass(s);
    }
    // We have a call expression ...(...) — find the last '('
    int open = s.lastIndexOf('(');
    if (open < 0) return null;

    String argsText = s.substring(open + 1, s.length() - 1);
    String call = s.substring(0, open);           // e.g. "LocalDate.now" or "foo().bar"
    int lastDot = call.lastIndexOf('.');
    if (lastDot < 0) return null;

    String baseExpr = call.substring(0, lastDot); // e.g. "LocalDate" or "foo()"
    String method   = call.substring(lastDot + 1);// e.g. "now" or "bar"

    // Resolve base type (recurse if base is itself a call)
    Class<?> baseType = baseExpr.endsWith(")") ? inferType(baseExpr) : resolveClass(baseExpr);
    if (baseType == null) return null;

    java.lang.reflect.Method best = pickBestMethod(baseType, method, argsText);
    if (best == null) return null;

    Class<?> rt = best.getReturnType();
    return (rt == Void.TYPE) ? null : rt;
  }

  private static java.lang.reflect.Method pickBestMethod(Class<?> baseType, String name, String argsText) {
    java.lang.reflect.Method fallback = null;
    int desiredParams = argsText.trim().isEmpty() ? 0 : -1; // prefer 0-arg if no args typed
    for (java.lang.reflect.Method m : baseType.getMethods()) {
      if (!m.getName().equals(name)) continue;
      if (desiredParams == 0 && m.getParameterCount() == 0) return m; // exact cheap match
      if (fallback == null) fallback = m;
    }
    return fallback;
  }

  private static Class<?> resolveClass(String token) {
    if (token == null || token.isEmpty()) return null;

    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) cl = GroovyCompletionEngine.class.getClassLoader();

    // 1) FQCN
    Class<?> c = tryLoad(token, cl);
    if (c != null) return c;

    // 2) fast common packages
    for (String pkg : new String[]{"java.lang", "groovy.lang", "java.time"}) {
      c = tryLoad(pkg + "." + token, cl);
      if (c != null) return c;
    }

    // 3) fallback: cached index (must include java.time in SCAN_PACKAGES)
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
