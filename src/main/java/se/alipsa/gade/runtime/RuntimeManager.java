package se.alipsa.gade.runtime;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Manages runtime definitions and selections per project.
 */
public class RuntimeManager {

  public static final String RUNTIME_GADE = "Gade";
  public static final String RUNTIME_GRADLE = "Gradle";
  public static final String RUNTIME_MAVEN = "Maven";

  private final RuntimePreferences preferences;
  private List<RuntimeConfig> customRuntimes;

  public RuntimeManager(RuntimePreferences preferences) {
    this.preferences = preferences;
    customRuntimes = new ArrayList<>(preferences.loadCustomRuntimes());
  }

  public List<RuntimeConfig> getBuiltInRuntimes() {
    return List.of(
        new RuntimeConfig(RUNTIME_GRADLE, RuntimeType.GRADLE),
        new RuntimeConfig(RUNTIME_MAVEN, RuntimeType.MAVEN),
        new RuntimeConfig(RUNTIME_GADE, RuntimeType.GADE)
    );
  }

  public List<RuntimeConfig> getCustomRuntimes() {
    return Collections.unmodifiableList(customRuntimes);
  }

  public List<RuntimeConfig> getAllRuntimes() {
    List<RuntimeConfig> runtimes = new ArrayList<>(customRuntimes);
    for (RuntimeConfig builtIn : getBuiltInRuntimes()) {
      boolean exists = runtimes.stream().anyMatch(r -> r.getName().equalsIgnoreCase(builtIn.getName()));
      if (!exists) {
        runtimes.add(builtIn);
      }
    }
    return runtimes;
  }

  public Optional<RuntimeConfig> findRuntime(String name) {
    return getAllRuntimes().stream()
        .filter(r -> r.getName().equalsIgnoreCase(name))
        .findFirst();
  }

  public void setSelectedRuntime(File projectDir, RuntimeConfig runtime) {
    preferences.storeSelectedRuntimeName(projectDir, runtime.getName());
  }

  public RuntimeConfig getSelectedRuntime(File projectDir) {
    Optional<String> selection = preferences.getSelectedRuntimeName(projectDir);
    if (selection.isPresent()) {
      Optional<RuntimeConfig> runtime = findRuntime(selection.get());
      if (runtime.isPresent()) {
        return runtime.get();
      }
    }
    return defaultRuntime(projectDir);
  }

  public RuntimeConfig defaultRuntime(File projectDir) {
    // Prefer Maven if a pom.xml exists, then Gradle, otherwise fallback to Gade.
    if (projectDir != null) {
      if (new File(projectDir, "pom.xml").exists()) {
        return findRuntime(RUNTIME_MAVEN).orElseGet(() -> new RuntimeConfig(RUNTIME_MAVEN, RuntimeType.MAVEN));
      }
      if (new File(projectDir, "build.gradle").exists()) {
        return findRuntime(RUNTIME_GRADLE).orElseGet(() -> new RuntimeConfig(RUNTIME_GRADLE, RuntimeType.GRADLE));
      }
    }
    return findRuntime(RUNTIME_GADE).orElseGet(() -> new RuntimeConfig(RUNTIME_GADE, RuntimeType.GADE));
  }

  public void addOrUpdateCustomRuntime(RuntimeConfig runtime) {
    if (runtime == null || runtime.getType() == null) {
      return;
    }
    if (runtime.getType() == RuntimeType.GADE) {
      return;
    }
    customRuntimes = customRuntimes.stream()
        .filter(r -> !r.getName().equalsIgnoreCase(runtime.getName()))
        .collect(Collectors.toCollection(ArrayList::new));
    customRuntimes.add(runtime);
    preferences.storeCustomRuntimes(customRuntimes);
  }

  public void deleteCustomRuntime(String runtimeName) {
    customRuntimes = customRuntimes.stream()
        .filter(r -> !r.getName().equalsIgnoreCase(runtimeName))
        .collect(Collectors.toCollection(ArrayList::new));
    preferences.storeCustomRuntimes(customRuntimes);
  }

  public boolean isAvailable(RuntimeConfig runtime, File projectDir) {
    if (runtime == null) {
      return false;
    }
    return switch (runtime.getType()) {
      case GRADLE -> projectDir != null && new File(projectDir, "build.gradle").exists();
      case MAVEN -> projectDir != null && new File(projectDir, "pom.xml").exists();
      default -> true;
    };
  }
}
