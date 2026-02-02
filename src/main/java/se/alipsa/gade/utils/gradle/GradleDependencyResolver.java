package se.alipsa.gade.utils.gradle;

import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;

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
    return new ClasspathModel(List.copyOf(dependencyFiles), List.copyOf(outputDirs), false);
  }

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
