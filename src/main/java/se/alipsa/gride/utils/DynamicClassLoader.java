package se.alipsa.gride.utils;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;
import java.util.Arrays;

public class DynamicClassLoader extends URLClassLoader {


  public DynamicClassLoader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
  }

  public DynamicClassLoader(URL[] urls) {
    super(urls);
  }

  public DynamicClassLoader(URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory) {
    super(urls, parent, factory);
  }

  public DynamicClassLoader(String name, URL[] urls, ClassLoader parent) {
    super(name, urls, parent);
  }

  public DynamicClassLoader(String name, URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory) {
    super(name, urls, parent, factory);
  }

  public void addUrl(URL url) {
    if (Arrays.stream(getURLs()).noneMatch(p -> p.equals(url))) {
      super.addURL(url);
    }
  }
}
