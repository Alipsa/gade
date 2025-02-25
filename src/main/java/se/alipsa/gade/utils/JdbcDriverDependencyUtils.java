package se.alipsa.gade.utils;

import static se.alipsa.gade.Constants.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Gade;
import se.alipsa.groovy.resolver.Dependency;

import java.io.File;

public class JdbcDriverDependencyUtils {

  private static final Logger log = LogManager.getLogger();

  // TODO, look up the latest version dynamically instead of hard coding it
  public static Dependency driverDependency(String driverClass) {
    Dependency dependency = new Dependency();
    var driver = Driver.fromClass(driverClass);
    var depExp = driver.getDependency().split(":");
    dependency.setGroupId(depExp[0]);
    dependency.setArtifactId(depExp[1]);
    dependency.setVersion(depExp[2]);
    return dependency;
  }

  /*
    If there is a ddl file for integrated security we should use the corresponding jdbc version
   */
  private static String checkForDllVersion(String defaultVersion) {
    File libDir = new File(Gade.instance().getGadeBaseDir(), "lib");
    String version = defaultVersion;
    String latestVersion = "0.0.0";
    String tmpVersion;
    if (libDir.exists() && libDir.isDirectory()) {
      for (String file : libDir.list()) {
        //"mssql-jdbc_auth-9.4.1.x64.dll"
        // there might be several ones so grab the latest one
        if (file.endsWith(".dll") && file.startsWith("mssql-jdbc_auth-") && file.contains(".x64.dll")) {
          tmpVersion = file.substring("mssql-jdbc_auth-".length(), file.indexOf(".x64.dll"))
              + defaultVersion.substring(defaultVersion.lastIndexOf('.'));
          if (SemanticVersion.compare(latestVersion, tmpVersion) < 0) {
            latestVersion = tmpVersion;
          }
        }
      }
      if (!"0.0.0".equals(latestVersion)) {
        log.info("Using the driver corresponding to the latest dll version in lib dir i.e. {}", latestVersion);
        version = latestVersion;
      }
    }
    return version;
  }
}
