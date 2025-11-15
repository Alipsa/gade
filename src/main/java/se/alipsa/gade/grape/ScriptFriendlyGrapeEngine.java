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
    Object candidate = args.get("classLoader");
    if (!(candidate instanceof ClassLoader)) {
      return args;
    }
    ClassLoader originalLoader = (ClassLoader) candidate;
    if (originalLoader != ClassLoader.getSystemClassLoader()) {
      return args;
    }
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    if (!(contextLoader instanceof GroovyClassLoader) || contextLoader == originalLoader) {
      return args;
    }
    Map<Object, Object> adjusted = new LinkedHashMap<>(args);
    adjusted.put("classLoader", contextLoader);
    return adjusted;
  }
}
