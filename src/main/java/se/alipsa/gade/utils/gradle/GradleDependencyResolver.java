package se.alipsa.gade.utils.gradle;

import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import se.alipsa.gade.utils.SemanticVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Handles dependency resolution via the Gradle Tooling API.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Resolving project dependencies using the IdeaProject model</li>
 *   <li>Filtering dependencies by scope (compile, runtime, test, etc.)</li>
 *   <li>Extracting output directories for main and test sources</li>
 * </ul>
 *
 * @see GradleUtils
 */
final class GradleDependencyResolver {

  private final File projectDir;

  /**
   * Creates a new dependency resolver.
   *
   * @param projectDir the Gradle project directory
   */
  GradleDependencyResolver(File projectDir) {
    this.projectDir = projectDir;
  }

  /**
   * Result of dependency resolution containing dependencies and output directories.
   *
   * @param dependencies list of dependency jar files
   * @param outputDirs list of output directories (compiled classes)
   * @param fromCache whether this result came from cache
   */
  record ClasspathModel(List<File> dependencies, List<File> outputDirs, boolean fromCache) {}

  /**
   * Resolves dependencies from an IdeaProject model.
   *
   * @param project the IdeaProject model
   * @param testContext whether to include test dependencies
   * @return the resolved classpath model
   */
  ClasspathModel resolveFromIdeaProject(IdeaProject project, boolean testContext) {
    LinkedHashSet<File> dependencyFiles = new LinkedHashSet<>();
    LinkedHashSet<File> outputDirs = new LinkedHashSet<>();

    for (IdeaModule module : project.getModules()) {
      for (IdeaDependency dependency : module.getDependencies()) {
        if (dependency instanceof IdeaSingleEntryLibraryDependency ideaDependency) {
          if (shouldIncludeDependency(dependency, testContext)) {
            dependencyFiles.add(ideaDependency.getFile());
          }
        }
      }
      outputDirs.add(getOutputDir(module));
      if (testContext) {
        File testOut = getTestOutputDir(module);
        if (testOut != null) {
          outputDirs.add(testOut);
        }
      }
    }
    return new ClasspathModel(normalizeResolvedDependencies(dependencyFiles), List.copyOf(outputDirs), false);
  }

  /**
   * Normalizes resolved dependency files by collapsing multiple versions of the same module.
   * <p>
   * IdeaProject can surface multiple versions of the same module. At runtime this can cause
   * classpath pollution (e.g. mixed Log4j/SLF4J versions). Keep one file per module key and
   * prefer the highest version.
   */
  static List<File> normalizeResolvedDependencies(Iterable<File> dependencies) {
    LinkedHashMap<ModuleKey, Candidate> byModule = new LinkedHashMap<>();
    List<File> passthrough = new ArrayList<>();
    for (File file : dependencies) {
      if (file == null) {
        continue;
      }
      Candidate candidate = parseCandidate(file);
      if (candidate == null) {
        passthrough.add(file);
        continue;
      }
      Candidate current = byModule.get(candidate.key());
      if (current == null || compareVersion(candidate.version(), current.version()) > 0) {
        byModule.put(candidate.key(), candidate);
      }
    }
    List<File> normalized = new ArrayList<>(passthrough.size() + byModule.size());
    normalized.addAll(passthrough);
    byModule.values().forEach(candidate -> normalized.add(candidate.file()));
    return List.copyOf(normalized);
  }

  private static int compareVersion(String left, String right) {
    if (Objects.equals(left, right)) {
      return 0;
    }
    if (left == null || left.isBlank()) {
      return -1;
    }
    if (right == null || right.isBlank()) {
      return 1;
    }
    return SemanticVersion.compare(left, right);
  }

  private static Candidate parseCandidate(File file) {
    ParsedCoordinate coordinate = parseFromGradleCache(file);
    if (coordinate == null) {
      coordinate = parseFromMavenRepo(file);
    }
    if (coordinate == null) {
      coordinate = parseFromGadeCache(file);
    }
    if (coordinate == null) {
      return null;
    }
    return new Candidate(file, coordinate.version(), coordinate.key());
  }

  private static ParsedCoordinate parseFromGradleCache(File file) {
    String path = file.getAbsolutePath().replace('\\', '/');
    String marker = "/modules-2/files-2.1/";
    int idx = path.indexOf(marker);
    if (idx < 0) {
      return null;
    }
    String remainder = path.substring(idx + marker.length());
    String[] parts = remainder.split("/");
    if (parts.length < 5) {
      return null;
    }
    String group = parts[0];
    String artifact = parts[1];
    String version = parts[2];
    return parseCoordinate(file.getName(), group, artifact, version);
  }

  private static ParsedCoordinate parseFromMavenRepo(File file) {
    String path = file.getAbsolutePath().replace('\\', '/');
    String marker = "/.m2/repository/";
    int idx = path.indexOf(marker);
    if (idx < 0) {
      return null;
    }
    String remainder = path.substring(idx + marker.length());
    String[] parts = remainder.split("/");
    if (parts.length < 4) {
      return null;
    }
    String artifact = parts[parts.length - 3];
    String version = parts[parts.length - 2];
    StringBuilder groupBuilder = new StringBuilder();
    for (int i = 0; i < parts.length - 3; i++) {
      if (i > 0) {
        groupBuilder.append('.');
      }
      groupBuilder.append(parts[i]);
    }
    return parseCoordinate(file.getName(), groupBuilder.toString(), artifact, version);
  }

  private static ParsedCoordinate parseFromGadeCache(File file) {
    String path = file.getAbsolutePath().replace('\\', '/');
    String marker = "/.gade/cache/";
    int idx = path.indexOf(marker);
    if (idx < 0) {
      return null;
    }
    String remainder = path.substring(idx + marker.length());
    String[] parts = remainder.split("/");
    if (parts.length < 4) {
      return null;
    }
    String artifact = parts[parts.length - 3];
    String version = parts[parts.length - 2];
    StringBuilder groupBuilder = new StringBuilder();
    for (int i = 0; i < parts.length - 3; i++) {
      if (i > 0) {
        groupBuilder.append('.');
      }
      groupBuilder.append(parts[i]);
    }
    return parseCoordinate(file.getName(), groupBuilder.toString(), artifact, version);
  }

  private static ParsedCoordinate parseCoordinate(String fileName, String group, String artifact, String version) {
    if (group == null || group.isBlank() || artifact == null || artifact.isBlank()
        || version == null || version.isBlank() || fileName == null || fileName.isBlank()) {
      return null;
    }
    int extIdx = fileName.lastIndexOf('.');
    if (extIdx <= 0 || extIdx >= fileName.length() - 1) {
      return null;
    }
    String ext = fileName.substring(extIdx + 1).toLowerCase(Locale.ROOT);
    String baseName = fileName.substring(0, extIdx);

    String prefix = artifact + "-" + version;
    String classifier = "";
    if (baseName.equals(prefix)) {
      classifier = "";
    } else if (baseName.startsWith(prefix + "-")) {
      classifier = baseName.substring(prefix.length() + 1);
    } else {
      return null;
    }
    return new ParsedCoordinate(version, new ModuleKey(group, artifact, classifier, ext));
  }

  private record Candidate(File file, String version, ModuleKey key) {}

  private record ParsedCoordinate(String version, ModuleKey key) {}

  private record ModuleKey(String group, String artifact, String classifier, String extension) {}

  /**
   * Determines if a dependency should be included based on its scope.
   *
   * @param dependency the dependency to check
   * @param testContext whether test dependencies should be included
   * @return true if the dependency should be included
   */
  static boolean shouldIncludeDependency(IdeaDependency dependency, boolean testContext) {
    if (dependency == null || dependency.getScope() == null) {
      return true;
    }
    String scope = dependency.getScope().getScope();
    if (scope == null || scope.isBlank()) {
      return true;
    }
    scope = scope.toUpperCase();
    if ("COMPILE".equals(scope) || "RUNTIME".equals(scope) || "PROVIDED".equals(scope)) {
      return true;
    }
    return testContext && "TEST".equals(scope);
  }

  /**
   * Gets the main output directory for a module.
   *
   * @param module the IdeaModule
   * @return the output directory
   */
  File getOutputDir(IdeaModule module) {
    File outPutDir = module.getCompilerOutput().getOutputDir();
    if (outPutDir == null || !outPutDir.exists()) {
      File moduleDir = module.getGradleProject().getProjectDirectory();
      moduleDir = moduleDir != null && moduleDir.exists() ? moduleDir : projectDir;
      outPutDir = new File(moduleDir, "build/classes/groovy/main/");
    }
    return outPutDir;
  }

  /**
   * Gets the test output directory for a module.
   *
   * @param module the IdeaModule
   * @return the test output directory, or null if not found
   */
  File getTestOutputDir(IdeaModule module) {
    if (module == null || module.getCompilerOutput() == null) {
      return null;
    }
    File out = module.getCompilerOutput().getTestOutputDir();
    if (out != null && out.exists()) {
      return out;
    }
    File moduleDir = module.getGradleProject().getProjectDirectory();
    moduleDir = moduleDir != null && moduleDir.exists() ? moduleDir : projectDir;
    File fallback = new File(moduleDir, "build/classes/groovy/test/");
    return fallback.exists() ? fallback : null;
  }
}
