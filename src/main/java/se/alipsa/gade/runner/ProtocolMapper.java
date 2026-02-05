package se.alipsa.gade.runner;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Factory for creating ObjectMapper instances configured for socket-based JSON protocol.
 * <p>
 * The mappers are configured to NOT auto-close streams, since the socket connection
 * is reused across multiple JSON messages (one per line). Auto-closing would terminate
 * the connection after the first message.
 */
public final class ProtocolMapper {

  private ProtocolMapper() {
    throw new AssertionError("No instances");
  }

  /**
   * Creates an ObjectMapper configured for the Gade runner socket protocol.
   * <p>
   * Configuration:
   * <ul>
   *   <li>AUTO_CLOSE_TARGET = false - prevents closing the writer after each write</li>
   *   <li>AUTO_CLOSE_SOURCE = false - prevents closing the reader after each read</li>
   * </ul>
   *
   * @return configured ObjectMapper instance
   */
  public static ObjectMapper create() {
    return new ObjectMapper()
        .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
        .configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
  }
}
