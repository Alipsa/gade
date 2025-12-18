package se.alipsa.gade.runner;

import groovy.lang.GroovyObjectSupport;

final class UnsupportedGuiInteraction extends GroovyObjectSupport {

  private final String name;

  UnsupportedGuiInteraction(String name) {
    this.name = name == null ? "" : name;
  }

  @Override
  public Object invokeMethod(String method, Object args) {
    throw new UnsupportedOperationException(message());
  }

  @Override
  public Object getProperty(String property) {
    throw new UnsupportedOperationException(message());
  }

  @Override
  public void setProperty(String property, Object newValue) {
    throw new UnsupportedOperationException(message());
  }

  @Override
  public String toString() {
    return message();
  }

  private String message() {
    return "'" + name + "' is not available in external runtimes; select the GADE runtime for GUI interactions";
  }
}

