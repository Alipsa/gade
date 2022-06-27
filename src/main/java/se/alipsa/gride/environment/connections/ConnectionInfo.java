package se.alipsa.gride.environment.connections;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Alert;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import se.alipsa.gride.Gride;
import se.alipsa.gride.utils.Alerts;
import se.alipsa.maven.MavenUtils;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

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

  @SuppressWarnings("unchecked")
  public Connection connect() throws SQLException {
    log.info("Connecting to {} using {}", getUrl(), getDependency());
    var gui = Gride.instance();
    Driver driver;
    ClassLoader cl;
    if (gui != null) {
      cl = gui.getConsoleComponent().getClassLoader();
    } else {
      cl = Thread.currentThread().getContextClassLoader();
    }

    try {
      MavenUtils mavenUtils = new MavenUtils();
      String[] dep = getDependency().split(":");
      log.info("Resolving dependency {}", getDependency());
      File jar = mavenUtils.resolveArtifact(dep[0], dep[1], null, "jar", dep[2]);
      URL[] urls = new URL[] {jar.toURI().toURL()};
      log.info("Dependency url is {}", urls[0]);
      ClassLoader classLoader = new URLClassLoader(urls, cl);
      log.info("Attempting to load the class {}", getDriver());
      Class<Driver> clazz = (Class<Driver>) classLoader.loadClass(getDriver());
      log.info("Loaded driver from session classloader, instating the driver {}", getDriver());
      try {
        driver = clazz.getDeclaredConstructor().newInstance();
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | NullPointerException e) {
        log.error("Failed to instantiate the driver: {}, clazz is {}", getDriver(), clazz, e);
        Platform.runLater(() ->
            Alerts.showAlert("Failed to instantiate the driver",
                getDriver() + " could not be loaded from dependency " + getDependency(),
                Alert.AlertType.ERROR)
        );
        return null;
      }
    } catch (ClassCastException | ClassNotFoundException | SettingsBuildingException | ArtifactResolutionException | MalformedURLException e) {
      Platform.runLater(() ->
          Alerts.showAlert("Failed to load driver",
              getDriver() + " could not be loaded from dependency " + getDependency(),
              Alert.AlertType.ERROR)
      );
      return null;
    }
    Properties props = new Properties();
    if ( urlContainsLogin() ) {
      log.info("Skipping specified user/password since it is part of the url");
    } else {
      if (getUser() != null) {
        props.put("user", getUser());
        if (getPassword() != null) {
          props.put("password", getPassword());
        }
      }
    }
    if (gui != null) {
      gui.setNormalCursor();
    }
    return driver.connect(getUrl(), props);
  }

  public boolean urlContainsLogin() {
    String safeLcUrl = url.getValueSafe().toLowerCase();
    return ( safeLcUrl.contains("user") && safeLcUrl.contains("pass") ) || safeLcUrl.contains("@");
  }

  public String asJson() {
    return "{" +
       "\"name\"=\"" + name.getValue() +
       "\", \"driver\"=\"" + driver.getValue() +
       "\", \"url\"=\"" + url.getValue() +
       "\", \"user\"=" + user.getValue() +
       "\", \"password\"=\"" + password.getValue() +
       "\"}";
  }
}
