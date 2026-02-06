package se.alipsa.gade.runtime;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

/**
 * XML-based protocol serialization for Gade ↔ Runner subprocess communication.
 * <p>
 * Uses only JDK built-in XML APIs ({@code javax.xml.parsers}, {@code org.w3c.dom})
 * so that the runner subprocess does not need Groovy or Jackson on its classpath.
 * <p>
 * Wire format (one XML document per line, no XML declaration):
 * <pre>
 * &lt;msg&gt;&lt;e k="type"&gt;eval&lt;/e&gt;&lt;e k="id"&gt;uuid&lt;/e&gt;&lt;e k="script"&gt;code&lt;/e&gt;&lt;/msg&gt;
 * </pre>
 * <p>
 * Nested maps use {@code <map>}, lists use {@code <list>/<i>}.
 * Type hints: {@code t="int|long|double|bool"}, null: {@code nil="1"}.
 */
public final class ProtocolXml {

  private static final DocumentBuilderFactory DBF = DocumentBuilderFactory.newInstance();

  private ProtocolXml() {
    throw new AssertionError("No instances");
  }

  /**
   * Serialize a map to a single-line XML string (no XML declaration).
   *
   * @param map the message map to serialize
   * @return XML string with no newlines (suitable for line-based protocol)
   */
  public static String toXml(Map<String, ?> map) {
    StringBuilder sb = new StringBuilder();
    sb.append("<msg>");
    appendMapEntries(sb, map);
    sb.append("</msg>");
    return sb.toString();
  }

  /**
   * Deserialize a single-line XML string back to a map.
   *
   * @param xml the XML string to parse
   * @return the parsed map
   */
  public static Map<String, Object> fromXml(String xml) {
    try {
      DocumentBuilder db = DBF.newDocumentBuilder();
      Document doc = db.parse(new InputSource(new StringReader(xml)));
      Element root = doc.getDocumentElement(); // <msg>
      return parseMapEntries(root);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse protocol XML: " + e.getMessage(), e);
    }
  }

  // ===== Serialization =====

  private static void appendMapEntries(StringBuilder sb, Map<String, ?> map) {
    for (Map.Entry<String, ?> entry : map.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      appendEntry(sb, key, value);
    }
  }

  private static void appendEntry(StringBuilder sb, String key, Object value) {
    if (value == null) {
      sb.append("<e k=\"").append(escapeAttr(key)).append("\" nil=\"1\"/>");
      return;
    }

    if (value instanceof Map<?, ?> mapVal) {
      sb.append("<e k=\"").append(escapeAttr(key)).append("\"><map>");
      @SuppressWarnings("unchecked")
      Map<String, ?> typed = (Map<String, ?>) mapVal;
      appendMapEntries(sb, typed);
      sb.append("</map></e>");
      return;
    }

    if (value instanceof List<?> listVal) {
      sb.append("<e k=\"").append(escapeAttr(key)).append("\"><list>");
      for (Object item : listVal) {
        appendListItem(sb, item);
      }
      sb.append("</list></e>");
      return;
    }

    if (value instanceof Iterable<?> iterVal) {
      sb.append("<e k=\"").append(escapeAttr(key)).append("\"><list>");
      for (Object item : iterVal) {
        appendListItem(sb, item);
      }
      sb.append("</list></e>");
      return;
    }

    // Scalar value with optional type hint
    String typeHint = typeHintFor(value);
    sb.append("<e k=\"").append(escapeAttr(key)).append("\"");
    if (typeHint != null) {
      sb.append(" t=\"").append(typeHint).append("\"");
    }
    sb.append(">").append(escapeText(String.valueOf(value))).append("</e>");
  }

  private static void appendListItem(StringBuilder sb, Object item) {
    if (item == null) {
      sb.append("<i nil=\"1\"/>");
      return;
    }

    if (item instanceof Map<?, ?> mapVal) {
      sb.append("<i><map>");
      @SuppressWarnings("unchecked")
      Map<String, ?> typed = (Map<String, ?>) mapVal;
      appendMapEntries(sb, typed);
      sb.append("</map></i>");
      return;
    }

    if (item instanceof List<?> listVal) {
      sb.append("<i><list>");
      for (Object sub : listVal) {
        appendListItem(sb, sub);
      }
      sb.append("</list></i>");
      return;
    }

    String typeHint = typeHintFor(item);
    sb.append("<i");
    if (typeHint != null) {
      sb.append(" t=\"").append(typeHint).append("\"");
    }
    sb.append(">").append(escapeText(String.valueOf(item))).append("</i>");
  }

  private static String typeHintFor(Object value) {
    if (value instanceof Integer) return "int";
    if (value instanceof Long) return "long";
    if (value instanceof Double) return "double";
    if (value instanceof Float) return "float";
    if (value instanceof Boolean) return "bool";
    return null;
  }

  // ===== Deserialization =====

  private static Map<String, Object> parseMapEntries(Element parent) {
    Map<String, Object> map = new LinkedHashMap<>();
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element el = (Element) child;
      if (!"e".equals(el.getTagName())) {
        continue;
      }
      String key = el.getAttribute("k");
      Object value = parseValue(el);
      map.put(key, value);
    }
    return map;
  }

  private static Object parseValue(Element el) {
    // Null check
    if ("1".equals(el.getAttribute("nil"))) {
      return null;
    }

    // Check for nested <map> or <list>
    NodeList children = el.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element nested = (Element) child;
      switch (nested.getTagName()) {
        case "map":
          return parseMapEntries(nested);
        case "list":
          return parseList(nested);
      }
    }

    // Scalar text content
    String text = getTextContent(el);
    String type = el.getAttribute("t");
    return convertScalar(text, type);
  }

  private static List<Object> parseList(Element listEl) {
    List<Object> list = new ArrayList<>();
    NodeList children = listEl.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element item = (Element) child;
      if (!"i".equals(item.getTagName())) {
        continue;
      }
      list.add(parseListItem(item));
    }
    return list;
  }

  private static Object parseListItem(Element item) {
    // Null check
    if ("1".equals(item.getAttribute("nil"))) {
      return null;
    }

    // Check for nested <map> or <list>
    NodeList children = item.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element nested = (Element) child;
      switch (nested.getTagName()) {
        case "map":
          return parseMapEntries(nested);
        case "list":
          return parseList(nested);
      }
    }

    // Scalar
    String text = getTextContent(item);
    String type = item.getAttribute("t");
    return convertScalar(text, type);
  }

  private static Object convertScalar(String text, String type) {
    if (type == null || type.isEmpty()) {
      return text;
    }
    return switch (type) {
      case "int" -> Integer.parseInt(text);
      case "long" -> Long.parseLong(text);
      case "double" -> Double.parseDouble(text);
      case "float" -> Float.parseFloat(text);
      case "bool" -> Boolean.parseBoolean(text);
      default -> text;
    };
  }

  /**
   * Get direct text content of an element, excluding child element text.
   */
  private static String getTextContent(Element el) {
    StringBuilder sb = new StringBuilder();
    NodeList children = el.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
        sb.append(child.getNodeValue());
      }
    }
    return sb.toString();
  }

  // ===== XML Escaping =====

  private static String escapeText(String text) {
    StringBuilder sb = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&' -> sb.append("&amp;");
        case '<' -> sb.append("&lt;");
        case '>' -> sb.append("&gt;");
        case '\n' -> sb.append("&#10;");
        case '\r' -> sb.append("&#13;");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }

  private static String escapeAttr(String text) {
    StringBuilder sb = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&' -> sb.append("&amp;");
        case '<' -> sb.append("&lt;");
        case '>' -> sb.append("&gt;");
        case '"' -> sb.append("&quot;");
        case '\n' -> sb.append("&#10;");
        case '\r' -> sb.append("&#13;");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }
}
