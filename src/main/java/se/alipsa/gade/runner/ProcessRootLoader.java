package se.alipsa.gade.runner;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Root classloader for the runner subprocess.
 * <p>
 * This class must be public and provide a public single-argument constructor
 * to satisfy the {@code -Djava.system.class.loader} contract.
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
