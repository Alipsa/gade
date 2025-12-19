package se.alipsa.gade.utils.maven;

import groovy.lang.GroovyClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.console.ConsoleTextArea;
import se.alipsa.gade.runtime.MavenResolver;
import se.alipsa.gade.utils.gradle.GradleUtils;
import se.alipsa.mavenutils.DependenciesResolveException;
import se.alipsa.mavenutils.MavenUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

public final class MavenClasspathUtils {

  private static final Logger log = LogManager.getLogger(MavenClasspathUtils.class);
  private static final String CACHE_SCHEMA = "schema";
  private static final String CACHE_SCHEMA_VERSION = "1";
  private static final String CACHE_FINGERPRINT = "fingerprint";
  private static final String CACHE_DEP_PREFIX = "dep.";
  private static final String CACHE_CREATED_AT = "createdAtEpochMs";

  private MavenClasspathUtils() {}

  public static void addPomDependenciesTo(GroovyClassLoader loader, File projectDir, boolean testContext, ConsoleTextArea console) {
    if (projectDir == null) {
      return;
    }
    File pom = new File(projectDir, "pom.xml");
    if (!pom.exists()) {
      return;
    }
    long start = System.currentTimeMillis();
    try {
      List<File> deps = resolveDependencies(projectDir, pom, testContext, console);
      for (File f : deps) {
        if (!f.exists()) {
          console.appendWarningFx("Dependency file does not exist: " + f);
          continue;
        }
        loader.addURL(f.toURI().toURL());
      }
      long ms = System.currentTimeMillis() - start;
      console.appendFx("  Maven classpath resolved in " + (ms / 1000) + "s", true);
    } catch (Exception e) {
      log.warn("Failed to resolve Maven dependencies for {}", pom, e);
      console.appendWarningFx("Failed to resolve Maven dependencies: " + e.getMessage());
      tryFallback(loader, pom, testContext, console);
    }
  }

  private static void tryFallback(GroovyClassLoader loader, File pom, boolean testContext, ConsoleTextArea console) {
    try {
      if (testContext) {
        console.appendWarningFx("Falling back to minimal Maven dependency resolver (test scope not fully supported).");
      } else {
        console.appendWarningFx("Falling back to minimal Maven dependency resolver.");
      }
      MavenResolver.addPomDependenciesTo(loader, pom);
    } catch (Exception ex) {
      log.warn("Fallback Maven dependency resolver failed for {}", pom, ex);
      console.appendWarningFx("Fallback Maven dependency resolver failed: " + ex.getMessage());
    }
  }

  private static List<File> resolveDependencies(File projectDir, File pom, boolean testContext, ConsoleTextArea console)
      throws IOException, DependenciesResolveException {
    String fingerprint = fingerprintProject(projectDir, testContext);
    File cacheFile = cacheFile(projectDir, testContext);
    List<File> cached = load(cacheFile, fingerprint);
    if (cached != null) {
      console.appendFx("  Using cached Maven classpath", true);
      return cached;
    }
    MavenUtils maven = new MavenUtils();
    Set<File> resolved;
    try {
      resolved = maven.resolveDependencies(pom, testContext);
    } catch (Exception e) {
      if (e instanceof DependenciesResolveException dre) {
        throw dre;
      }
      throw new DependenciesResolveException("Failed to resolve dependencies from " + pom, e);
    }
    List<File> deps = resolved.stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(File::getAbsolutePath))
        .toList();
    store(cacheFile, fingerprint, deps);
    return deps;
  }

  private static List<File> load(File cacheFile, String expectedFingerprint) {
    if (cacheFile == null || !cacheFile.exists()) {
      return null;
    }
    Properties props = new Properties();
    try (FileInputStream in = new FileInputStream(cacheFile)) {
      props.load(in);
    } catch (IOException e) {
      log.debug("Failed to read Maven classpath cache {}", cacheFile, e);
      return null;
    }
    if (!CACHE_SCHEMA_VERSION.equals(props.getProperty(CACHE_SCHEMA))) {
      return null;
    }
    if (!Objects.equals(expectedFingerprint, props.getProperty(CACHE_FINGERPRINT))) {
      return null;
    }
    List<File> deps = new ArrayList<>();
    for (String key : props.stringPropertyNames()) {
      if (key.startsWith(CACHE_DEP_PREFIX)) {
        deps.add(new File(props.getProperty(key)));
      }
    }
    deps.sort(Comparator.comparing(File::getAbsolutePath));
    return deps;
  }

  private static void store(File cacheFile, String fingerprint, List<File> deps) {
    if (cacheFile == null) {
      return;
    }
    Properties props = new Properties();
    props.setProperty(CACHE_SCHEMA, CACHE_SCHEMA_VERSION);
    props.setProperty(CACHE_FINGERPRINT, fingerprint);
    props.setProperty(CACHE_CREATED_AT, String.valueOf(System.currentTimeMillis()));
    int i = 0;
    for (File dep : deps) {
      props.setProperty(CACHE_DEP_PREFIX + (++i), dep.getAbsolutePath());
    }
    File parent = cacheFile.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      log.warn("Failed to create Maven classpath cache dir {}", parent);
      return;
    }
    try (FileOutputStream out = new FileOutputStream(cacheFile)) {
      props.store(out, "Gade Maven classpath cache");
    } catch (IOException e) {
      log.debug("Failed to write Maven classpath cache {}", cacheFile, e);
    }
  }

  private static File cacheFile(File projectDir, boolean testContext) {
    String projectKey = MavenBuildUtils.projectKey(projectDir);
    File dir = new File(GradleUtils.getCacheDir(), "maven-classpath/" + projectKey);
    return new File(dir, testContext ? "classpath-test.properties" : "classpath-main.properties");
  }

  private static String fingerprintProject(File projectDir, boolean testContext) throws IOException {
    List<Path> tracked = new ArrayList<>();
    tracked.add(projectDir.toPath().resolve("pom.xml"));
    tracked.add(projectDir.toPath().resolve(".mvn").resolve("maven.config"));
    tracked.add(projectDir.toPath().resolve(".mvn").resolve("extensions.xml"));
    tracked.add(projectDir.toPath().resolve(".mvn").resolve("wrapper").resolve("maven-wrapper.properties"));

    long dotMvnLatest = latestModified(projectDir.toPath().resolve(".mvn"));
    StringBuilder sb = new StringBuilder();
    sb.append("project=").append(projectDir.getAbsolutePath()).append('\n');
    sb.append("testContext=").append(testContext).append('\n');
    for (Path p : tracked) {
      sb.append(p).append('|');
      try {
        if (Files.exists(p)) {
          sb.append(Files.size(p)).append('|').append(Files.getLastModifiedTime(p).toMillis());
        } else {
          sb.append("missing");
        }
      } catch (IOException e) {
        sb.append("error:").append(e.getClass().getSimpleName());
      }
      sb.append('\n');
    }
    sb.append("dotMvnLatest=").append(dotMvnLatest).append('\n');
    return sha256Hex(sb.toString());
  }

  private static long latestModified(Path dir) {
    if (dir == null || !Files.exists(dir)) {
      return 0L;
    }
    try (Stream<Path> stream = Files.walk(dir)) {
      return stream
          .filter(Files::isRegularFile)
          .limit(2000)
          .mapToLong(path -> {
            try {
              return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
              return 0L;
            }
          })
          .max()
          .orElse(0L);
    } catch (IOException e) {
      return 0L;
    }
  }

  private static String sha256Hex(String str) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(str.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
