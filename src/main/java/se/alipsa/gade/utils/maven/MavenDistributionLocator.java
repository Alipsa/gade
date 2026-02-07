package se.alipsa.gade.utils.maven;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

final class MavenDistributionLocator {

  static final String EMBEDDED_MAVEN_HOME_PROP = "gade.maven.embedded.home";
  private static final Logger log = LogManager.getLogger(MavenDistributionLocator.class);

  private MavenDistributionLocator() {
  }

  static File resolveBundledMavenHome() {
    String override = System.getProperty(EMBEDDED_MAVEN_HOME_PROP);
    if (override != null && !override.isBlank()) {
      File overrideDir = new File(override);
      if (isMavenHome(overrideDir)) {
        return overrideDir;
      }
      log.warn("Ignoring invalid {} path: {}", EMBEDDED_MAVEN_HOME_PROP, overrideDir);
    }
    try {
      URI location = MavenDistributionLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI();
      File anchor = new File(location);
      File cursor = anchor.isFile() ? anchor.getParentFile() : anchor;
      for (int i = 0; i < 10 && cursor != null; i++) {
        File candidate = new File(cursor, "lib/maven");
        if (isMavenHome(candidate)) {
          return candidate;
        }
        cursor = cursor.getParentFile();
      }
    } catch (URISyntaxException e) {
      log.debug("Failed to resolve bundled Maven home from code source", e);
    }
    return null;
  }

  static boolean isMavenHome(File candidate) {
    if (candidate == null || !candidate.isDirectory()) {
      return false;
    }
    return new File(candidate, "bin/mvn").isFile()
        || new File(candidate, "bin/mvn.cmd").isFile();
  }
}
