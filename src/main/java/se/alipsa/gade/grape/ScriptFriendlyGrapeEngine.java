package se.alipsa.gade.grape;

import groovy.grape.GrapeEngine;
import groovy.lang.GroovyClassLoader;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link GrapeEngine} decorator that redirects {@code systemClassLoader} requests from {@code @Grab}
 * annotations to the current script class loader so downloaded dependencies remain in the isolated
 * script hierarchy.
 */
public final class ScriptFriendlyGrapeEngine implements GrapeEngine {

  private final GrapeEngine delegate;

  /**
   * Create a new wrapper.
   *
   * @param delegate the underlying {@link GrapeEngine} to forward calls to.
   */
  public ScriptFriendlyGrapeEngine(GrapeEngine delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public Object grab(String endorsedModule) {
    return delegate.grab(endorsedModule);
  }

  @Override
  public Object grab(Map args) {
    return delegate.grab(adjustArgs(args));
  }

  @Override
  public Object grab(Map args, Map... dependencies) {
    return delegate.grab(adjustArgs(args), dependencies);
  }

  @Override
  public Map<String, Map<String, List<String>>> enumerateGrapes() {
    return delegate.enumerateGrapes();
  }

  @Override
  public URI[] resolve(Map args, Map... dependencies) {
    return delegate.resolve(adjustArgs(args), dependencies);
  }

  @Override
  public URI[] resolve(Map args, List depsInfo, Map... dependencies) {
    return delegate.resolve(adjustArgs(args), depsInfo, dependencies);
  }

  @Override
  public Map[] listDependencies(ClassLoader classLoader) {
    return delegate.listDependencies(classLoader);
  }

  @Override
  public void addResolver(Map<String, Object> args) {
    delegate.addResolver(args);
  }

  private Map adjustArgs(Map args) {
    if (args == null || args.isEmpty()) {
      return args;
    }
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    if (!(contextLoader instanceof GroovyClassLoader)
        || contextLoader == null
        || contextLoader == ClassLoader.getSystemClassLoader()) {
      return args;
    }

    boolean requiresRedirect = false;
    Map<Object, Object> adjusted = null;

    Object candidate = args.get("classLoader");
    if (candidate instanceof ClassLoader && candidate == ClassLoader.getSystemClassLoader()) {
      requiresRedirect = true;
      adjusted = cloneArgs(args);
      adjusted.put("classLoader", contextLoader);
    }

    if (isTrue(args.get("systemClassLoader"))) {
      if (adjusted == null) {
        adjusted = cloneArgs(args);
      }
      adjusted.put("systemClassLoader", Boolean.FALSE);
      adjusted.put("classLoader", contextLoader);
      requiresRedirect = true;
    }

    return requiresRedirect ? adjusted : args;
  }

  private Map<Object, Object> cloneArgs(Map args) {
    return new LinkedHashMap<>(args);
  }

  private boolean isTrue(Object value) {
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    if (value instanceof CharSequence) {
      return Boolean.parseBoolean(value.toString());
    }
    return false;
  }
}
