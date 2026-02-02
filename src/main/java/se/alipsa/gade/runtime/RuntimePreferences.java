package se.alipsa.gade.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Handles persistence of runtime settings and selections.
 */
public class RuntimePreferences {

  private static final String PREF_NODE = "runtimes";
  private static final String CUSTOM_RUNTIMES_KEY = "customRuntimes";
  private static final String PROJECT_SELECTION_NODE = "projectSelections";

  private final Preferences root;
  private final ObjectMapper mapper = new ObjectMapper();

  public RuntimePreferences(Preferences basePrefs) {
    root = basePrefs.node(PREF_NODE);
  }

  public List<RuntimeConfig> loadCustomRuntimes() {
    String raw = root.get(CUSTOM_RUNTIMES_KEY, null);
    if (raw == null || raw.isBlank()) {
      return Collections.emptyList();
    }
    try {
      return mapper.readValue(raw, new TypeReference<List<RuntimeConfig>>() {});
    } catch (JsonProcessingException e) {
      // If parsing fails, reset to empty so the UI can recover.
      root.remove(CUSTOM_RUNTIMES_KEY);
      return Collections.emptyList();
    }
  }

  public void storeCustomRuntimes(List<RuntimeConfig> runtimes) {
    try {
      root.put(CUSTOM_RUNTIMES_KEY, mapper.writeValueAsString(runtimes));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to store custom runtimes", e);
    }
  }

  public Optional<String> getSelectedRuntimeName(File projectDir) {
    String key = projectKey(projectDir);
    Preferences selectionNode = root.node(PROJECT_SELECTION_NODE);
    return Optional.ofNullable(selectionNode.get(key, null));
  }

  public void storeSelectedRuntimeName(File projectDir, String runtimeName) {
    String key = projectKey(projectDir);
    Preferences selectionNode = root.node(PROJECT_SELECTION_NODE);
    selectionNode.put(key, runtimeName);
  }

  private String projectKey(File projectDir) {
    if (projectDir == null) {
      return "default";
    }
    // Use SHA-256 hash to create a short, consistent key that fits within
    // Java Preferences 80-character limit (hash in hex is 64 chars).
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(projectDir.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
