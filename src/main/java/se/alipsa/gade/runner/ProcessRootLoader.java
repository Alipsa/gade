package se.alipsa.gade.runner;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Root classloader for the runner subprocess.
 * <p>
 * Uses standard parent-first delegation. With the boot/engine JAR split,
 * engine classes (which depend on Groovy) exist only on this loader — not
 * on the App classloader — so parent-first works correctly:
 * <ul>
 *   <li>{@code loadClass("GadeRunnerEngine")} → App CL (not found) → this loader finds it in engine JAR</li>
 *   <li>Groovy deps → App CL (not found) → this loader finds them in Groovy jars</li>
 *   <li>{@code ProtocolXml} → App CL → found in boot JAR</li>
 * </ul>
 * <p>
 * This class must be public, JDK-only, and provide a public single-argument
 * constructor taking a {@link ClassLoader} to satisfy the
 * {@code -Djava.system.class.loader} contract.
 */
public class ProcessRootLoader extends URLClassLoader {

  public ProcessRootLoader(ClassLoader parent) {
    super(new URL[0], parent);
  }

  @Override
  public void addURL(URL url) {
    super.addURL(url);
  }
}
