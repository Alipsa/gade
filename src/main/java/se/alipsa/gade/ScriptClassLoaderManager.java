package se.alipsa.gade;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovySystem;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.groovy.control.CompilerConfiguration;
import se.alipsa.gade.utils.ClassUtils;

/**
 * Manages the hierarchy of {@link GroovyClassLoader} instances used for executing scripts.
 * The manager creates a curated root class loader that is isolated from the IDE/application
 * class path while exposing only the Groovy runtime that is required to evaluate scripts and
 * explicitly blocking access to Gade implementation classes.
 *
 * <p>The manager also exposes a single shared "dynamic" loader that acts as the only gateway
 * for intentionally approved dependencies (for example JDBC drivers selected by the user) to be
 * attached to subsequently created script loaders. This ensures that scripts start in a clean
 * environment yet can still opt-in to additional libraries without reintroducing the IDE class
 * path.</p>
 */
public final class ScriptClassLoaderManager {

  private static final Logger LOG = LogManager.getLogger(ScriptClassLoaderManager.class);

  private static final List<String> BLOCKED_PACKAGES = List.of("se.alipsa.gade");

  private final GroovyClassLoader rootLoader;
  private GroovyClassLoader sharedDynamicLoader;

  /**
   * Create a new manager using the supplied Gade home directory.
   *
   * @param gadeHome the directory containing the Gade distribution.
   */
  public ScriptClassLoaderManager(File gadeHome) {
    Objects.requireNonNull(gadeHome, "gadeHome");
    rootLoader = createRootLoader();
  }

  private GroovyClassLoader createRootLoader() {
    GroovyClassLoader loader = new IsolatedGroovyClassLoader(ClassUtils.getBootstrapClassLoader(), BLOCKED_PACKAGES);
    addCodeSource(loader, GroovySystem.class);
    return loader;
  }

  private void addCodeSource(GroovyClassLoader loader, Class<?> clazz) {
    CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
    if (codeSource == null) {
      return;
    }
    addUrl(loader, codeSource.getLocation());
  }

  private void addUrl(GroovyClassLoader loader, URL url) {
    List<URL> existing = Arrays.asList(loader.getURLs());
    if (!existing.contains(url)) {
      loader.addURL(url);
    }
  }

  /**
   * Create a child script {@link GroovyClassLoader} that inherits the curated root loader.
   *
   * @param configuration the compiler configuration to apply.
   * @return a new {@link GroovyClassLoader} for executing scripts.
   */
  public GroovyClassLoader createScriptClassLoader(CompilerConfiguration configuration) {
    GroovyClassLoader parent = getSharedDynamicLoader();
    return configuration == null
        ? new IsolatedGroovyClassLoader(parent, BLOCKED_PACKAGES)
        : new IsolatedGroovyClassLoader(parent, configuration, BLOCKED_PACKAGES);
  }

  /**
   * Obtain the curated root {@link GroovyClassLoader}.
   *
   * @return the root loader managed by this instance.
   */
  public GroovyClassLoader getRootLoader() {
    return rootLoader;
  }

  /**
   * Obtain a shared loader that should be used for long-lived dynamic dependencies such as JDBC drivers.
   * The shared loader is intentionally separated from the IDE/application class path so that callers can
   * curate exactly which jars become visible to future script executions.
   *
   * @return a shared {@link GroovyClassLoader} instance.
   */
  public synchronized GroovyClassLoader getSharedDynamicLoader() {
    if (sharedDynamicLoader == null) {
      sharedDynamicLoader = new IsolatedGroovyClassLoader(rootLoader, BLOCKED_PACKAGES);
    }
    return sharedDynamicLoader;
  }

  /**
   * Append additional dependency URLs to the shared dynamic loader while avoiding duplicates.
   * These URLs represent the curated dependency set that scripts should see in addition to the
   * Groovy runtime.
   *
   * @param urls the URLs to add.
   */
  public synchronized void addDependencyUrls(Collection<URL> urls) {
    if (urls == null || urls.isEmpty()) {
      return;
    }
    GroovyClassLoader loader = getSharedDynamicLoader();
    Set<URL> current = Arrays.stream(loader.getURLs()).collect(Collectors.toCollection(LinkedHashSet::new));
    for (URL url : urls) {
      if (url != null && !current.contains(url)) {
        loader.addURL(url);
        current.add(url);
      }
    }
  }

  /**
   * Append a dependency file to the shared dynamic loader.
   *
   * @param file the jar or directory to add.
   */
  public void addDependencyFile(File file) {
    if (file == null) {
      return;
    }
    try {
      addDependencyUrls(List.of(file.toURI().normalize().toURL()));
    } catch (MalformedURLException e) {
      LOG.warn("Failed to add dependency {}", file, e);
    }
  }
}
