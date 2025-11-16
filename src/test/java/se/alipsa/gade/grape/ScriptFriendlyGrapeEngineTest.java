package se.alipsa.gade.grape;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import groovy.grape.GrapeEngine;
import groovy.lang.GroovyClassLoader;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScriptFriendlyGrapeEngineTest {

  @Test
  void redirectsSystemClassLoaderToContextLoader() {
    RecordingGrapeEngine delegate = new RecordingGrapeEngine();
    ScriptFriendlyGrapeEngine engine = new ScriptFriendlyGrapeEngine(delegate);
    GroovyClassLoader context = new GroovyClassLoader();
    ClassLoader original = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(context);
      engine.grab(Collections.singletonMap("classLoader", ClassLoader.getSystemClassLoader()), Map.of());
      assertTrue(delegate.invoked);
      assertSame(context, delegate.lastArgs.get("classLoader"));
    } finally {
      Thread.currentThread().setContextClassLoader(original);
      context.clearCache();
    }
  }

  @Test
  void redirectsSystemClassLoaderFlag() {
    RecordingGrapeEngine delegate = new RecordingGrapeEngine();
    ScriptFriendlyGrapeEngine engine = new ScriptFriendlyGrapeEngine(delegate);
    GroovyClassLoader context = new GroovyClassLoader();
    ClassLoader original = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(context);
      engine.grab(Collections.singletonMap("systemClassLoader", Boolean.TRUE), Map.of());
      assertTrue(delegate.invoked);
      assertSame(context, delegate.lastArgs.get("classLoader"));
      assertSame(Boolean.FALSE, delegate.lastArgs.get("systemClassLoader"));
    } finally {
      Thread.currentThread().setContextClassLoader(original);
      context.clearCache();
    }
  }

  @Test
  void redirectsStringSystemClassLoaderFlag() {
    RecordingGrapeEngine delegate = new RecordingGrapeEngine();
    ScriptFriendlyGrapeEngine engine = new ScriptFriendlyGrapeEngine(delegate);
    GroovyClassLoader context = new GroovyClassLoader();
    ClassLoader original = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(context);
      engine.grab(Collections.singletonMap("systemClassLoader", "true"), Map.of());
      assertTrue(delegate.invoked);
      assertSame(context, delegate.lastArgs.get("classLoader"));
      assertSame(Boolean.FALSE, delegate.lastArgs.get("systemClassLoader"));
    } finally {
      Thread.currentThread().setContextClassLoader(original);
      context.clearCache();
    }
  }

  @Test
  void addsContextLoaderWhenMissing() {
    RecordingGrapeEngine delegate = new RecordingGrapeEngine();
    ScriptFriendlyGrapeEngine engine = new ScriptFriendlyGrapeEngine(delegate);
    GroovyClassLoader context = new GroovyClassLoader();
    ClassLoader original = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(context);
      engine.grab(Collections.singletonMap("group", "example"), Map.of());
      assertTrue(delegate.invoked);
      assertSame(context, delegate.lastArgs.get("classLoader"));
    } finally {
      Thread.currentThread().setContextClassLoader(original);
      context.clearCache();
    }
  }

  @Test
  void injectsContextLoaderWhenArgsNull() {
    RecordingGrapeEngine delegate = new RecordingGrapeEngine();
    ScriptFriendlyGrapeEngine engine = new ScriptFriendlyGrapeEngine(delegate);
    GroovyClassLoader context = new GroovyClassLoader();
    ClassLoader original = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(context);
      engine.grab((Map) null, Map.of());
      assertTrue(delegate.invoked);
      assertSame(context, delegate.lastArgs.get("classLoader"));
    } finally {
      Thread.currentThread().setContextClassLoader(original);
      context.clearCache();
    }
  }

  @Test
  void injectsContextLoaderWhenArgsEmpty() {
    RecordingGrapeEngine delegate = new RecordingGrapeEngine();
    ScriptFriendlyGrapeEngine engine = new ScriptFriendlyGrapeEngine(delegate);
    GroovyClassLoader context = new GroovyClassLoader();
    ClassLoader original = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(context);
      engine.grab(Collections.emptyMap(), Map.of());
      assertTrue(delegate.invoked);
      assertSame(context, delegate.lastArgs.get("classLoader"));
    } finally {
      Thread.currentThread().setContextClassLoader(original);
      context.clearCache();
    }
  }

  private static final class RecordingGrapeEngine implements GrapeEngine {

    private Map<?, ?> lastArgs;
    private boolean invoked;

    @Override
    public Object grab(String endorsedModule) {
      invoked = true;
      return null;
    }

    @Override
    public Object grab(Map args) {
      invoked = true;
      lastArgs = args;
      return null;
    }

    @Override
    public Object grab(Map args, Map... dependencies) {
      invoked = true;
      lastArgs = args;
      return null;
    }

    @Override
    public Map<String, Map<String, List<String>>> enumerateGrapes() {
      invoked = true;
      return Collections.emptyMap();
    }

    @Override
    public URI[] resolve(Map args, Map... dependencies) {
      invoked = true;
      lastArgs = args;
      return new URI[0];
    }

    @Override
    public URI[] resolve(Map args, List depsInfo, Map... dependencies) {
      invoked = true;
      lastArgs = args;
      return new URI[0];
    }

    @Override
    public Map[] listDependencies(ClassLoader classLoader) {
      invoked = true;
      return new Map[0];
    }

    @Override
    public void addResolver(Map<String, Object> args) {
      invoked = true;
      lastArgs = args;
    }
  }
}
