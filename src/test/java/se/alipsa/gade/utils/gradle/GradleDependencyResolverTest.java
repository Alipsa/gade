package se.alipsa.gade.utils.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

class GradleDependencyResolverTest {

  @Test
  void normalizeResolvedDependenciesKeepsHighestVersionPerModule() {
    List<File> deps = List.of(
        file("/home/per/.gradle/caches/modules-2/files-2.1/org.apache.logging.log4j/log4j-api/2.24.3/a/log4j-api-2.24.3.jar"),
        file("/home/per/.gradle/caches/modules-2/files-2.1/org.apache.logging.log4j/log4j-api/2.25.3/b/log4j-api-2.25.3.jar"),
        file("/home/per/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.16/c/slf4j-api-2.0.16.jar"),
        file("/home/per/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.17/d/slf4j-api-2.0.17.jar")
    );

    List<File> normalized = GradleDependencyResolver.normalizeResolvedDependencies(deps);
    List<String> names = normalized.stream().map(File::getName).toList();

    assertEquals(2, normalized.size(), "Expected one selected version per module");
    assertTrue(names.contains("log4j-api-2.25.3.jar"), "Expected latest log4j-api version");
    assertTrue(names.contains("slf4j-api-2.0.17.jar"), "Expected latest slf4j-api version");
  }

  @Test
  void normalizeResolvedDependenciesPreservesClassifierVariants() {
    List<File> deps = List.of(
        file("/home/per/.m2/repository/org/openjfx/javafx-graphics/23.0.2/javafx-graphics-23.0.2-linux.jar"),
        file("/home/per/.m2/repository/org/openjfx/javafx-graphics/23.0.2/javafx-graphics-23.0.2-win.jar")
    );

    List<File> normalized = GradleDependencyResolver.normalizeResolvedDependencies(deps);
    List<String> names = normalized.stream().map(File::getName).toList();

    assertEquals(2, normalized.size(), "Classifier variants must not be collapsed");
    assertTrue(names.contains("javafx-graphics-23.0.2-linux.jar"));
    assertTrue(names.contains("javafx-graphics-23.0.2-win.jar"));
  }

  @Test
  void normalizeResolvedDependenciesRetainsUnknownPaths() {
    File unknown = file("/opt/libs/custom-lib.jar");
    List<File> deps = List.of(
        unknown,
        file("/home/per/.gade/cache/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar"),
        file("/home/per/.gade/cache/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar")
    );

    List<File> normalized = GradleDependencyResolver.normalizeResolvedDependencies(deps);
    List<String> names = normalized.stream().map(File::getName).toList();

    assertEquals(2, normalized.size());
    assertTrue(normalized.contains(unknown), "Unknown file path should pass through");
    assertTrue(names.contains("slf4j-api-2.0.17.jar"), "Expected latest version from cache path");
  }

  private File file(String path) {
    return new File(path);
  }
}

