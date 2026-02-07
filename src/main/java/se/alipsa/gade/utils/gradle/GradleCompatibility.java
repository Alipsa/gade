package se.alipsa.gade.utils.gradle;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GradleCompatibility {

  private static final Logger log = LogManager.getLogger(GradleCompatibility.class);

  public static final String MIN_SUPPORTED_GRADLE = "5.0";
  public static final String MAX_SUPPORTED_GRADLE = "9.99";

  private static final Pattern CORE_JAR_PATTERN = Pattern.compile("gradle-core-([0-9][0-9A-Za-z._+-]*)\\.jar");
  private static final Pattern VERSION_LINE_PATTERN = Pattern.compile("^Gradle\\s+(.+)$");
  private static final Pattern WRAPPER_URL_PATTERN = Pattern.compile("gradle-([0-9A-Za-z._+-]+)-(?:bin|all)\\.zip");

  private GradleCompatibility() {
  }

  public static boolean isSupported(String version) {
    if (version == null || version.isBlank()) {
      return false;
    }
    ComparableVersion current = new ComparableVersion(version.trim());
    ComparableVersion min = new ComparableVersion(MIN_SUPPORTED_GRADLE);
    ComparableVersion max = new ComparableVersion(MAX_SUPPORTED_GRADLE);
    return current.compareTo(min) >= 0 && current.compareTo(max) <= 0;
  }

  public static CompletableFuture<String> extractVersion(File gradleHome) {
    if (gradleHome == null || !gradleHome.isDirectory()) {
      return CompletableFuture.completedFuture(null);
    }
    String fromCoreJar = extractVersionFromCoreJar(gradleHome);
    if (fromCoreJar != null) {
      return CompletableFuture.completedFuture(fromCoreJar);
    }
    return CompletableFuture.supplyAsync(() -> extractVersionFromCommand(gradleHome))
        .completeOnTimeout(null, 5, TimeUnit.SECONDS)
        .exceptionally(e -> {
          log.debug("Failed to extract Gradle version from {}", gradleHome, e);
          return null;
        });
  }

  public static String extractVersionFromWrapper(File wrapperProperties) {
    if (wrapperProperties == null || !wrapperProperties.isFile()) {
      return null;
    }
    Properties props = new Properties();
    try (FileInputStream fis = new FileInputStream(wrapperProperties)) {
      props.load(fis);
    } catch (IOException e) {
      log.debug("Failed to read Gradle wrapper properties {}", wrapperProperties, e);
      return null;
    }
    return extractVersionFromDistributionUrl(props.getProperty("distributionUrl"));
  }

  static String extractVersionFromCoreJar(File gradleHome) {
    File libDir = new File(gradleHome, "lib");
    if (!libDir.isDirectory()) {
      return null;
    }
    File[] matches = libDir.listFiles(file -> file.isFile() && file.getName().startsWith("gradle-core-")
        && file.getName().endsWith(".jar"));
    if (matches == null || matches.length == 0) {
      return null;
    }
    return Arrays.stream(matches)
        .sorted(Comparator.comparing(File::getName))
        .map(File::getName)
        .map(name -> {
          Matcher m = CORE_JAR_PATTERN.matcher(name);
          if (m.matches()) {
            return m.group(1);
          }
          return null;
        })
        .filter(v -> v != null && !v.isBlank())
        .findFirst()
        .orElse(null);
  }

  static String extractVersionFromCommand(File gradleHome) {
    File gradleExec = gradleExecutable(gradleHome);
    if (gradleExec == null || !gradleExec.isFile()) {
      return null;
    }
    Process process = null;
    try {
      process = new ProcessBuilder(gradleExec.getAbsolutePath(), "--version")
          .directory(gradleHome)
          .redirectErrorStream(true)
          .start();
      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return null;
      }
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          Matcher m = VERSION_LINE_PATTERN.matcher(line.trim());
          if (m.matches()) {
            return m.group(1).trim();
          }
        }
      }
    } catch (Exception e) {
      log.debug("Failed to run {} --version", gradleExec, e);
    } finally {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
    }
    return null;
  }

  private static File gradleExecutable(File gradleHome) {
    String os = System.getProperty("os.name", "").toLowerCase();
    if (os.contains("win")) {
      return new File(gradleHome, "bin/gradle.bat");
    }
    return new File(gradleHome, "bin/gradle");
  }

  private static String extractVersionFromDistributionUrl(String distributionUrl) {
    if (distributionUrl == null || distributionUrl.isBlank()) {
      return null;
    }
    String normalized = distributionUrl.trim().replace("\\:", ":");
    int idx = normalized.lastIndexOf('/');
    String fileName = idx >= 0 ? normalized.substring(idx + 1) : normalized;
    Matcher matcher = WRAPPER_URL_PATTERN.matcher(fileName);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }
}
