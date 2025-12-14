package se.alipsa.gade.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.model.Library;
import se.alipsa.gade.runtime.RuntimeConfig;
import se.alipsa.gade.runtime.RuntimeType;

class LibraryUtilsInternalTest {

  @Test
  void addClassLoaderJarsParsesJar() throws Exception {
    File jar = new File(System.getProperty("java.io.tmpdir"), "demo-1.0.jar");
    jar.delete();
    jar.createNewFile();
    jar.deleteOnExit();
    URLClassLoader cl = new URLClassLoader(new java.net.URL[]{jar.toURI().toURL()});
    Map<String, Library> libs = new HashMap<>();
    LibraryUtils.addClassLoaderJars(cl, libs, new HashSet<>());
    assertTrue(libs.values().stream().anyMatch(l -> "demo".equals(l.getPackageName()) && "1.0".equals(l.getVersion())));
  }

  @Test
  void addCustomDependenciesAddsCoordinatesAndJars() throws Exception {
    File jar = new File(System.getProperty("java.io.tmpdir"), "custom-2.0.jar");
    jar.delete();
    jar.createNewFile();
    jar.deleteOnExit();
    RuntimeConfig runtime = new RuntimeConfig("Custom", RuntimeType.CUSTOM, null, null,
        java.util.List.of(jar.getAbsolutePath()),
        java.util.List.of("org.example:demo:1.0"));
    Map<String, Library> libs = new HashMap<>();
    LibraryUtils.addCustomDependencies(runtime, libs);
    assertTrue(libs.values().stream().anyMatch(l -> "demo".equals(l.getPackageName()) && "1.0".equals(l.getVersion())));
    assertTrue(libs.values().stream().anyMatch(l -> "custom".equals(l.getPackageName()) && "2.0".equals(l.getVersion())));
  }

  @Test
  void addMavenDependenciesReadsPom() throws Exception {
    File dir = new File(System.getProperty("java.io.tmpdir"), "pomtest-" + System.nanoTime());
    dir.mkdirs();
    dir.deleteOnExit();
    File pom = new File(dir, "pom.xml");
    pom.deleteOnExit();
    try (FileWriter fw = new FileWriter(pom)) {
      fw.write("""
          <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>
            <groupId>org.example</groupId>
            <artifactId>demo</artifactId>
            <version>1.0</version>
            <dependencies>
              <dependency>
                <groupId>org.apache.commons</groupId>
                <artifactId>commons-lang3</artifactId>
                <version>3.12.0</version>
              </dependency>
              <dependency>
                <groupId>junit</groupId>
                <artifactId>junit</artifactId>
                <version>4.13.2</version>
                <scope>test</scope>
              </dependency>
            </dependencies>
          </project>
          """);
    }
    Map<String, Library> libs = new HashMap<>();
    LibraryUtils.addMavenDependencies(dir, libs);
    assertTrue(libs.values().stream().anyMatch(l -> "commons-lang3".equals(l.getPackageName())));
    assertTrue(libs.values().stream().noneMatch(l -> "junit".equals(l.getPackageName())));
  }
}
