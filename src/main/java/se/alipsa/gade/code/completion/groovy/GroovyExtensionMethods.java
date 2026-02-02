package se.alipsa.gade.code.completion.groovy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.code.completion.CompletionItem;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovers and provides Groovy extension methods.
 * Extension methods are static methods in classes like DefaultGroovyMethods
 * that appear as instance methods on the first parameter type.
 * <p>
 * For example: DefaultGroovyMethods.collect(Iterable, Closure) appears as
 * iterable.collect(closure) in Groovy code.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe. The extension method registry uses a
 * {@code volatile} field with lazy initialization (thread-safe via happens-before guarantees).
 * Method caching by receiver type uses {@link ConcurrentHashMap} for lock-free concurrent access.
 * Multiple threads can safely query extension methods concurrently; first access triggers
 * registry initialization which is safely published via volatile semantics.
 *
 * @see ConcurrentHashMap
 * @threadsafe
 */
public final class GroovyExtensionMethods {

  private static final Logger LOG = LogManager.getLogger(GroovyExtensionMethods.class);

  // Groovy extension method classes
  private static final String[] EXTENSION_CLASSES = {
      "org.codehaus.groovy.runtime.DefaultGroovyMethods",
      "org.codehaus.groovy.runtime.StringGroovyMethods",
      "org.codehaus.groovy.runtime.DateGroovyMethods",
      "org.codehaus.groovy.runtime.SqlGroovyMethods",
      "org.codehaus.groovy.runtime.ProcessGroovyMethods",
      "org.codehaus.groovy.runtime.EncodingGroovyMethods",
      "org.codehaus.groovy.runtime.IOGroovyMethods",
      "org.codehaus.groovy.runtime.ResourceGroovyMethods",
      "org.codehaus.groovy.runtime.NioGroovyMethods",
      "org.apache.groovy.datetime.extensions.DateTimeExtensions"
  };

  // Methods to exclude (Object methods that are redundant)
  private static final Set<String> EXCLUDED_METHODS = Set.of(
      "wait", "notify", "notifyAll", "getClass", "getMetaClass", "setMetaClass",
      "invokeMethod", "getProperty", "setProperty"
  );

  // Cache: receiver type -> list of extension methods
  private static final Map<Class<?>, List<ExtensionMethod>> CACHE = new ConcurrentHashMap<>();

  // Singleton lazy-loaded extension method registry
  private static volatile Map<String, List<ExtensionMethod>> methodsByReceiverName;

  private GroovyExtensionMethods() {}

  /**
   * Returns extension methods applicable to the given receiver type.
   *
   * @param receiverType the type of the object the methods would be called on
   * @param prefix       filter methods starting with this prefix (case-insensitive)
   * @return list of applicable extension methods
   */
  public static List<ExtensionMethod> getExtensionMethods(Class<?> receiverType, String prefix) {
    if (receiverType == null) {
      return Collections.emptyList();
    }

    List<ExtensionMethod> cached = CACHE.get(receiverType);
    if (cached == null) {
      cached = findExtensionMethods(receiverType);
      CACHE.put(receiverType, cached);
    }

    if (prefix == null || prefix.isEmpty()) {
      return cached;
    }

    String lowPrefix = prefix.toLowerCase(Locale.ROOT);
    List<ExtensionMethod> filtered = new ArrayList<>();
    for (ExtensionMethod em : cached) {
      if (em.name.toLowerCase(Locale.ROOT).startsWith(lowPrefix)) {
        filtered.add(em);
      }
    }
    return filtered;
  }

  /**
   * Converts extension methods to CompletionItems.
   */
  public static List<CompletionItem> toCompletionItems(List<ExtensionMethod> methods) {
    List<CompletionItem> items = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Map<String, Boolean> hasParamsByName = new HashMap<>();

    for (ExtensionMethod em : methods) {
      boolean hasParams = em.parameterTypes.length > 0;
      hasParamsByName.merge(em.name, hasParams, (a, b) -> a || b);
    }

    for (ExtensionMethod em : methods) {
      // Deduplicate by method name (keep first overload)
      if (!seen.add(em.name)) {
        continue;
      }

      // Insert just methodName() - user fills in parameters themselves
      String insertText = em.name + "()";
      // Display shows full signature so user knows what parameters are expected
      String display = em.name + "(" + em.parameterSignature() + ") : " + em.returnTypeName();

      boolean hasParams = hasParamsByName.getOrDefault(em.name, em.parameterTypes.length > 0);
      int cursorOffset = hasParams ? -1 : 0;

      CompletionItem item = CompletionItem.builder()
          .completion(em.name)
          .display(display)
          .kind(CompletionItem.Kind.METHOD)
          .detail("extension")
          .insertText(insertText)
          .sortPriority(150) // Slightly lower priority than regular methods
          .cursorOffset(cursorOffset)
          .build();

      items.add(item);
    }

    return items;
  }

  private static List<ExtensionMethod> findExtensionMethods(Class<?> receiverType) {
    ensureMethodsLoaded();

    List<ExtensionMethod> result = new ArrayList<>();

    // Check all extension methods to see which apply to this receiver type
    for (Map.Entry<String, List<ExtensionMethod>> entry : methodsByReceiverName.entrySet()) {
      String receiverClassName = entry.getKey();
      Class<?> declaredReceiver = tryLoadClass(receiverClassName);

      if (declaredReceiver != null && declaredReceiver.isAssignableFrom(receiverType)) {
        result.addAll(entry.getValue());
      }
    }

    // Also check for Object extensions (apply to everything)
    List<ExtensionMethod> objectExtensions = methodsByReceiverName.get("java.lang.Object");
    if (objectExtensions != null) {
      result.addAll(objectExtensions);
    }

    // Sort by method name
    result.sort(Comparator.comparing(em -> em.name));

    return result;
  }

  private static synchronized void ensureMethodsLoaded() {
    if (methodsByReceiverName != null) {
      return;
    }

    Map<String, List<ExtensionMethod>> methods = new HashMap<>();
    long start = System.currentTimeMillis();

    for (String className : EXTENSION_CLASSES) {
      try {
        Class<?> extClass = Class.forName(className);
        loadExtensionMethodsFromClass(extClass, methods);
      } catch (ClassNotFoundException e) {
        LOG.debug("Extension class not found: {}", className);
      } catch (Exception e) {
        LOG.warn("Failed to load extension methods from {}", className, e);
      }
    }

    long elapsed = System.currentTimeMillis() - start;
    int count = methods.values().stream().mapToInt(List::size).sum();
    LOG.debug("Loaded {} extension methods from {} receiver types in {} ms",
        count, methods.size(), elapsed);

    methodsByReceiverName = methods;
  }

  private static void loadExtensionMethodsFromClass(Class<?> extClass,
                                                    Map<String, List<ExtensionMethod>> methods) {
    for (Method m : extClass.getMethods()) {
      // Extension methods are public static with at least one parameter
      if (!Modifier.isStatic(m.getModifiers())) continue;
      if (!Modifier.isPublic(m.getModifiers())) continue;
      if (m.getParameterCount() == 0) continue;

      String name = m.getName();
      if (EXCLUDED_METHODS.contains(name)) continue;

      // First parameter is the receiver type
      Class<?> receiverType = m.getParameterTypes()[0];
      String receiverName = receiverType.getName();

      ExtensionMethod em = new ExtensionMethod(
          name,
          m.getReturnType(),
          Arrays.copyOfRange(m.getParameterTypes(), 1, m.getParameterCount()),
          m.getParameterCount() > 1 ? Arrays.copyOfRange(m.getParameters(), 1, m.getParameterCount()) : new java.lang.reflect.Parameter[0],
          receiverType
      );

      methods.computeIfAbsent(receiverName, k -> new ArrayList<>()).add(em);
    }
  }

  private static Class<?> tryLoadClass(String name) {
    try {
      return Class.forName(name, false, GroovyExtensionMethods.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  /**
   * Represents a single extension method.
   */
  public static final class ExtensionMethod {
    public final String name;
    public final Class<?> returnType;
    public final Class<?>[] parameterTypes;
    public final java.lang.reflect.Parameter[] parameters;
    public final Class<?> receiverType;

    ExtensionMethod(String name, Class<?> returnType, Class<?>[] parameterTypes,
                    java.lang.reflect.Parameter[] parameters, Class<?> receiverType) {
      this.name = name;
      this.returnType = returnType;
      this.parameterTypes = parameterTypes;
      this.parameters = parameters;
      this.receiverType = receiverType;
    }

    public String returnTypeName() {
      return simpleName(returnType);
    }

    public String parameterSignature() {
      if (parameterTypes.length == 0) return "";
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < parameterTypes.length; i++) {
        if (i > 0) sb.append(", ");
        sb.append(simpleName(parameterTypes[i]));
      }
      return sb.toString();
    }

    public String buildParameterList() {
      if (parameterTypes.length == 0) return "";
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < parameterTypes.length; i++) {
        if (i > 0) sb.append(", ");
        sb.append(simpleName(parameterTypes[i])).append(" ");
        if (i < parameters.length && parameters[i].isNamePresent()) {
          sb.append(parameters[i].getName());
        } else {
          sb.append("arg").append(i + 1);
        }
      }
      return sb.toString();
    }

    private static String simpleName(Class<?> c) {
      if (c.isArray()) {
        return simpleName(c.getComponentType()) + "[]";
      }
      return c.getSimpleName();
    }
  }

  /**
   * Clears the extension method cache.
   * Useful if Groovy runtime changes (unlikely during normal operation).
   */
  public static void invalidateCache() {
    CACHE.clear();
  }
}
