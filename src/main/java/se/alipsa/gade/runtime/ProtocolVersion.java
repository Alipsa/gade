package se.alipsa.gade.runtime;

/**
 * Defines the XML protocol version for Gade ↔ Runtime subprocess communication.
 * <p>
 * The protocol governs message exchange between {@link RuntimeProcessRunner} (Gade main process)
 * and {@link se.alipsa.gade.runner.GadeRunnerMain} (external runtime subprocess).
 * Messages are serialized as single-line XML via {@link ProtocolXml}.
 *
 * <h2>Version History:</h2>
 * <ul>
 *   <li><b>1.0</b> - Initial versioned protocol with handshake support
 *     <ul>
 *       <li>Messages: {@code hello}, {@code eval}, {@code result}, {@code error}, {@code out}, {@code err}</li>
 *       <li>Features: Script evaluation, stdout/stderr forwarding, basic error reporting</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Protocol Messages (XML format):</h2>
 * <pre>
 * // Runner → Gade (handshake initiation)
 * &lt;msg&gt;&lt;e k="type"&gt;hello&lt;/e&gt;&lt;e k="port" t="int"&gt;12345&lt;/e&gt;&lt;e k="protocolVersion"&gt;1.0&lt;/e&gt;&lt;/msg&gt;
 *
 * // Gade → Runner (eval request)
 * &lt;msg&gt;&lt;e k="cmd"&gt;eval&lt;/e&gt;&lt;e k="id"&gt;uuid&lt;/e&gt;&lt;e k="script"&gt;code&lt;/e&gt;&lt;/msg&gt;
 *
 * // Runner → Gade (eval response)
 * &lt;msg&gt;&lt;e k="type"&gt;result&lt;/e&gt;&lt;e k="id"&gt;uuid&lt;/e&gt;&lt;e k="result"&gt;value&lt;/e&gt;&lt;/msg&gt;
 * &lt;msg&gt;&lt;e k="type"&gt;error&lt;/e&gt;&lt;e k="id"&gt;uuid&lt;/e&gt;&lt;e k="error"&gt;message&lt;/e&gt;&lt;/msg&gt;
 *
 * // Runner → Gade (output forwarding)
 * &lt;msg&gt;&lt;e k="type"&gt;out&lt;/e&gt;&lt;e k="text"&gt;stdout line&lt;/e&gt;&lt;/msg&gt;
 * &lt;msg&gt;&lt;e k="type"&gt;err&lt;/e&gt;&lt;e k="text"&gt;stderr line&lt;/e&gt;&lt;/msg&gt;
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
 * @see ProtocolXml
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
