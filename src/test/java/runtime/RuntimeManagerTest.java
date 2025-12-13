package runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.alipsa.gade.runtime.RuntimeConfig;
import se.alipsa.gade.runtime.RuntimeManager;
import se.alipsa.gade.runtime.RuntimePreferences;
import se.alipsa.gade.runtime.RuntimeType;

class RuntimeManagerTest {

  @TempDir
  File tempDir;

  private Preferences testPrefs;
  private RuntimePreferences runtimePreferences;
  private RuntimeManager runtimeManager;

  @BeforeEach
  void setUp() {
    testPrefs = Preferences.userRoot().node("gade-runtime-test-" + System.nanoTime());
    runtimePreferences = new RuntimePreferences(testPrefs);
    runtimeManager = new RuntimeManager(runtimePreferences);
  }

  @AfterEach
  void tearDown() throws BackingStoreException {
    if (testPrefs != null) {
      testPrefs.removeNode();
    }
  }

  @Test
  void defaultRuntimePrefersGradleThenMaven() throws IOException {
    assertEquals(RuntimeType.GADE, runtimeManager.defaultRuntime(tempDir).getType());

    Files.createFile(new File(tempDir, "build.gradle").toPath());
    assertEquals(RuntimeType.GRADLE, runtimeManager.defaultRuntime(tempDir).getType());

    Files.deleteIfExists(new File(tempDir, "build.gradle").toPath());
    Files.createFile(new File(tempDir, "pom.xml").toPath());
    assertEquals(RuntimeType.MAVEN, runtimeManager.defaultRuntime(tempDir).getType());
  }

  @Test
  void selectsAndPersistsRuntimePerProject() {
    RuntimeConfig custom = new RuntimeConfig("CustomA", RuntimeType.CUSTOM);
    runtimeManager.addOrUpdateCustomRuntime(custom);
    runtimeManager.setSelectedRuntime(tempDir, custom);

    RuntimeManager reloaded = new RuntimeManager(runtimePreferences);
    RuntimeConfig selected = reloaded.getSelectedRuntime(tempDir);
    assertEquals("CustomA", selected.getName());
    assertEquals(RuntimeType.CUSTOM, selected.getType());
  }

  @Test
  void availabilityFollowsProjectFiles() throws IOException {
    RuntimeConfig gradle = new RuntimeConfig(RuntimeManager.RUNTIME_GRADLE, RuntimeType.GRADLE);
    RuntimeConfig maven = new RuntimeConfig(RuntimeManager.RUNTIME_MAVEN, RuntimeType.MAVEN);

    assertFalse(runtimeManager.isAvailable(gradle, tempDir));
    assertFalse(runtimeManager.isAvailable(maven, tempDir));

    Files.createFile(new File(tempDir, "build.gradle").toPath());
    assertTrue(runtimeManager.isAvailable(gradle, tempDir));
    assertFalse(runtimeManager.isAvailable(maven, tempDir));

    Files.deleteIfExists(new File(tempDir, "build.gradle").toPath());
    Files.createFile(new File(tempDir, "pom.xml").toPath());
    assertFalse(runtimeManager.isAvailable(gradle, tempDir));
    assertTrue(runtimeManager.isAvailable(maven, tempDir));
  }

  @Test
  void addUpdateAndDeleteCustomRuntimes() {
    RuntimeConfig customA = new RuntimeConfig("CustomA", RuntimeType.CUSTOM, null, null, List.of("/tmp/a.jar"), List.of());
    runtimeManager.addOrUpdateCustomRuntime(customA);
    assertEquals(1, runtimeManager.getCustomRuntimes().size());

    RuntimeConfig updated = new RuntimeConfig("CustomA", RuntimeType.CUSTOM, "/jdk", "/groovy", List.of(), List.of("g:a:v"));
    runtimeManager.addOrUpdateCustomRuntime(updated);
    assertEquals("/jdk", runtimeManager.getCustomRuntimes().getFirst().getJavaHome());

    runtimeManager.deleteCustomRuntime("CustomA");
    assertTrue(runtimeManager.getCustomRuntimes().isEmpty());
  }

  @Test
  void customRuntimesPersistThroughPreferences() {
    RuntimeConfig customA = new RuntimeConfig("Persisted", RuntimeType.CUSTOM, "/jdk", "/groovy", List.of(), List.of("g:a:v"));
    runtimePreferences.storeCustomRuntimes(List.of(customA));
    String raw = testPrefs.node("runtimes").get("customRuntimes", null);
    assertNotNull(raw);
    try {
      var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      List<RuntimeConfig> parsed = mapper.readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<List<RuntimeConfig>>() {});
      assertFalse(parsed.isEmpty());
    } catch (Exception e) {
      fail(e);
    }
    List<RuntimeConfig> loaded = runtimePreferences.loadCustomRuntimes();
    assertEquals(1, loaded.size());
    assertEquals("Persisted", loaded.getFirst().getName());
    assertEquals(RuntimeType.CUSTOM, loaded.getFirst().getType());
  }
}
