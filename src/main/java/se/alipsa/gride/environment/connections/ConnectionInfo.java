package se.alipsa.gride.environment.connections;

import javafx.beans.property.SimpleStringProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConnectionInfo implements Comparable<ConnectionInfo> {

  private static final Logger log = LogManager.getLogger(ConnectionInfo.class);

  private final SimpleStringProperty name;

  private final SimpleStringProperty dependency;
  private final SimpleStringProperty driver;
  private final SimpleStringProperty url;
  private final SimpleStringProperty user;
  private final SimpleStringProperty password;

  public ConnectionInfo() {
    this.name = new SimpleStringProperty();
    this.dependency = new SimpleStringProperty();
    this.driver = new SimpleStringProperty();
    this.url = new SimpleStringProperty();
    this.user = new SimpleStringProperty();
    this.password = new SimpleStringProperty();
  }

  public ConnectionInfo(String name, String dependency, String driver, String url, String user, String password) {
    this.name = new SimpleStringProperty(name);
    this.dependency = new SimpleStringProperty(dependency);
    this.driver = new SimpleStringProperty(driver);
    this.url = new SimpleStringProperty(url);
    this.user = new SimpleStringProperty(user);
    this.password = new SimpleStringProperty(password);
  }

  public ConnectionInfo(ConnectionInfo ci) {
    this.name = new SimpleStringProperty(ci.getName());
    this.dependency = new SimpleStringProperty(ci.getDependency());
    this.driver = new SimpleStringProperty(ci.getDriver());
    this.url = new SimpleStringProperty(ci.getUrl());
    this.user = new SimpleStringProperty(ci.getUser());
    this.password = new SimpleStringProperty(ci.getPassword());
  }

  public String getName() {
    return name.getValue();
  }

  public void setName(String name) {
    this.name.setValue(name);
  }

  public String getDependency() {
    return dependency.getValue();
  }

  public void setDependency(String dependency) {
    this.dependency.setValue(dependency);
  }

  public String getDriver() {
    return driver.getValue();
  }

  public void setDriver(String driver) {
    this.driver.setValue(driver);
  }

  public String getUrl() {
    return url.getValue();
  }

  public String getUrlSafe() {
    return url.getValueSafe();
  }

  public void setUrl(String url) {
    this.url.setValue(url);
  }

  @Override
  public String toString() {
    return name.getValue();
  }

  public String getUser() {
    return user.getValue();
  }

  public void setUser(String user) {
    this.user.setValue(user);
  }

  public String getPassword() {
    return password.getValue();
  }

  public void setPassword(String password) {
    this.password.setValue(password);
  }

  @Override
  public int compareTo(ConnectionInfo obj) {
      return this.toString().compareTo(obj.toString());
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ConnectionInfo) {
      return toString().equals(obj.toString());
    } else {
      return false;
    }
  }

  public String asJson() {
    return "{" +
       "\"name\"=\"" + name.getValue() +
       "\", \"driver\"=\"" + driver.getValue() +
       "\", \"url\"=\"" + url.getValue() +
       "\", \"user\"=" + user.getValue() +
       "\", \"password\"=\"" + password.getValue().replaceAll(".", "*") +
       "\"}";
  }
}
