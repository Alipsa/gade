package se.alipsa.gade.runtime;

/**
 * Defines the JSON-RPC protocol version for Gade ↔ Runtime subprocess communication.
 * <p>
 * The protocol governs message exchange between {@link RuntimeProcessRunner} (Gade main process)
 * and {@link se.alipsa.gade.runner.GadeRunnerMain} (external runtime subprocess).
 *
 * <h3>Version History:</h3>
 * <ul>
 *   <li><b>1.0</b> - Initial versioned protocol with handshake support
 *     <ul>
 *       <li>Messages: {@code hello}, {@code eval}, {@code result}, {@code error}, {@code out}, {@code err}</li>
 *       <li>Features: Script evaluation, stdout/stderr forwarding, basic error reporting</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h3>Protocol Messages:</h3>
 * <pre>
 * // Runner → Gade (handshake initiation)
 * {"type":"hello","port":12345,"protocolVersion":"1.0"}
 *
 * // Gade → Runner (eval request)
 * {"cmd":"eval","id":"uuid","script":"code","bindings":{}}
 *
 * // Runner → Gade (eval response)
 * {"type":"result","id":"uuid","result":"value"}
 * {"type":"error","id":"uuid","error":"message","stacktrace":"..."}
 *
 * // Runner → Gade (output forwarding)
 * {"type":"out","text":"stdout line"}
 * {"type":"err","text":"stderr line"}
 * </pre>
 *
 * <h3>Version Compatibility:</h3>
 * <ul>
 *   <li>Major version mismatch (e.g., 1.x vs 2.x) is incompatible - connection refused</li>
 *   <li>Minor version mismatch (e.g., 1.0 vs 1.1) is backward-compatible - features negotiated</li>
 *   <li>Missing {@code protocolVersion} field defaults to "1.0" for backward compatibility</li>
 * </ul>
 *
 * @see RuntimeProcessRunner
 * @see se.alipsa.gade.runner.GadeRunnerMain
 */
public final class ProtocolVersion {

  /** Current protocol version (MAJOR.MINOR format) */
  public static final String CURRENT = "1.0";

  /** Protocol major version (breaking changes increment this) */
  public static final int MAJOR = 1;

  /** Protocol minor version (backward-compatible features increment this) */
  public static final int MINOR = 0;

  private ProtocolVersion() {
    throw new AssertionError("No instances");
  }

  /**
   * Checks if the given protocol version is compatible with the current version.
   * <p>
   * Compatibility rules:
   * <ul>
   *   <li>Major version must match exactly</li>
   *   <li>Minor version can differ (backward compatible)</li>
   *   <li>Null/missing version defaults to "1.0" (backward compatibility)</li>
   * </ul>
   *
   * @param version the version to check (e.g., "1.0", "1.1"), or null for default
   * @return true if compatible, false if incompatible
   */
  public static boolean isCompatible(String version) {
    if (version == null || version.isBlank()) {
      // Backward compatibility: missing version implies 1.0
      return true;
    }

    String[] parts = version.split("\\.");
    if (parts.length != 2) {
      return false; // Invalid format
    }

    try {
      int theirMajor = Integer.parseInt(parts[0]);
      // Minor version doesn't need to match for compatibility
      return theirMajor == MAJOR;
    } catch (NumberFormatException e) {
      return false; // Invalid number format
    }
  }

  /**
   * Returns a human-readable description of the compatibility status.
   *
   * @param version the version to check
   * @return compatibility description
   */
  public static String getCompatibilityMessage(String version) {
    if (version == null || version.isBlank()) {
      return "Protocol version not specified (defaulting to 1.0)";
    }
    if (isCompatible(version)) {
      if (CURRENT.equals(version)) {
        return "Protocol version " + version + " matches (exact)";
      } else {
        return "Protocol version " + version + " compatible with " + CURRENT;
      }
    } else {
      return "Protocol version " + version + " incompatible with " + CURRENT + " (major version mismatch)";
    }
  }
}
