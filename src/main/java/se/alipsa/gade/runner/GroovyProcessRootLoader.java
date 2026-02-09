package se.alipsa.gade.runner;

import org.codehaus.groovy.tools.RootLoader;
import java.net.URL;

/**
 * RootLoader-based system classloader for all runtimes.
 * <p>
 * Extends {@link RootLoader} so that Groovy's {@code GrapeIvy} accepts it as a
 * valid target for {@code @GrabConfig(systemClassLoader=true)}.
 * {@code GrapeIvy.isValidTargetClassLoaderClass()} recursively checks the
 * superclass chain for {@code groovy.lang.GroovyClassLoader} or
 * {@code org.codehaus.groovy.tools.RootLoader} — this class matches the latter.
 * <p>
 * Using {@code RootLoader} (extends {@code URLClassLoader}) instead of
 * {@code GroovyClassLoader} ensures standard resource discovery via
 * {@code ServiceLoader}, which is critical for {@code @Grab}bed SLF4J
 * providers to be found correctly.
 * <p>
 * Required JVM contract: public single-arg constructor taking {@code ClassLoader}.
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html#getSystemClassLoader()">ClassLoader.getSystemClassLoader()</a>
 */
public class GroovyProcessRootLoader extends RootLoader {

  public GroovyProcessRootLoader(ClassLoader parent) {
    super(parent);
  }

  @Override
  public void addURL(URL url) {
    super.addURL(url);
  }
}
