package se.alipsa.gade.code.completion;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rich context for completion requests, providing all information
 * that completion engines need to generate relevant suggestions.
 */
public final class CompletionContext {

  // Pattern to detect if we're in member access context (after a dot)
  private static final Pattern MEMBER_ACCESS_PATTERN =
      Pattern.compile("([\\p{L}_][\\p{L}\\p{N}_]*(?:\\([^)]*\\))?(?:\\.[\\p{L}_][\\p{L}\\p{N}_]*(?:\\([^)]*\\))?)*)\\.(\\w*)$");

  // Pattern to detect string literals (simplistic - doesn't handle all edge cases)
  private static final Pattern STRING_PATTERN = Pattern.compile(
      "\"\"\"[\\s\\S]*?\"\"\"" + "|" +
      "'''[\\s\\S]*?'''" + "|" +
      "\"(?:\\\\.|[^\"])*\"?" + "|" +
      "'(?:\\\\.|[^'])*'?"
  );

  // Pattern to detect comments
  private static final Pattern COMMENT_PATTERN = Pattern.compile(
      "//[^\n]*" + "|" + "/\\*[\\s\\S]*?\\*/"
  );

  private final String fullText;
  private final int caretPosition;
  private final String lineText;
  private final int lineOffset;
  private final String tokenPrefix;
  private final String expressionBefore;
  private final ClassLoader classLoader;
  private final Map<String, Object> metadata;

  // Cached computed values
  private Boolean memberAccess;
  private Boolean insideString;
  private Boolean insideComment;

  private CompletionContext(Builder builder) {
    this.fullText = builder.fullText != null ? builder.fullText : "";
    this.caretPosition = Math.max(0, Math.min(builder.caretPosition, this.fullText.length()));
    this.lineText = builder.lineText != null ? builder.lineText : "";
    this.lineOffset = builder.lineOffset;
    this.tokenPrefix = builder.tokenPrefix != null ? builder.tokenPrefix : "";
    this.expressionBefore = builder.expressionBefore;
    this.classLoader = builder.classLoader;
    this.metadata = builder.metadata != null
        ? Collections.unmodifiableMap(new HashMap<>(builder.metadata))
        : Collections.emptyMap();
  }

  /**
   * Returns the full document text.
   */
  public String fullText() {
    return fullText;
  }

  /**
   * Returns the caret position within the full text.
   */
  public int caretPosition() {
    return caretPosition;
  }

  /**
   * Returns the text before the caret position.
   */
  public String textBeforeCaret() {
    return fullText.substring(0, caretPosition);
  }

  /**
   * Returns the current line text.
   */
  public String lineText() {
    return lineText;
  }

  /**
   * Returns the caret position within the current line.
   */
  public int lineOffset() {
    return lineOffset;
  }

  /**
   * Returns the partial token being typed (the completion prefix).
   */
  public String tokenPrefix() {
    return tokenPrefix;
  }

  /**
   * Returns the expression before the dot for member access completion.
   * May be null if not in member access context.
   */
  public String expressionBefore() {
    return expressionBefore;
  }

  /**
   * Returns the classloader to use for class resolution.
   * This includes project dependencies, @Grab jars, etc.
   */
  public ClassLoader classLoader() {
    return classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
  }

  /**
   * Returns language-specific metadata.
   */
  public Map<String, Object> metadata() {
    return metadata;
  }

  /**
   * Returns a specific metadata value.
   */
  @SuppressWarnings("unchecked")
  public <T> T metadata(String key, Class<T> type) {
    Object value = metadata.get(key);
    if (type.isInstance(value)) {
      return (T) value;
    }
    return null;
  }

  /**
   * Returns true if the caret is after a '.' (member access context).
   */
  public boolean isMemberAccess() {
    if (memberAccess == null) {
      String before = textBeforeCaret();
      Matcher m = MEMBER_ACCESS_PATTERN.matcher(before);
      memberAccess = m.find();
    }
    return memberAccess;
  }

  /**
   * Returns true if the expression before the dot looks like a class name
   * (starts with uppercase or contains dots suggesting FQCN).
   */
  public boolean isStaticContext() {
    if (expressionBefore == null || expressionBefore.isEmpty()) {
      return false;
    }
    char first = expressionBefore.charAt(0);
    return Character.isUpperCase(first) || expressionBefore.contains(".");
  }

  /**
   * Returns true if the caret is inside a string literal.
   */
  public boolean isInsideString() {
    if (insideString == null) {
      insideString = isInsidePattern(STRING_PATTERN);
    }
    return insideString;
  }

  /**
   * Returns true if the caret is inside a comment.
   */
  public boolean isInsideComment() {
    if (insideComment == null) {
      insideComment = isInsidePattern(COMMENT_PATTERN);
    }
    return insideComment;
  }

  private boolean isInsidePattern(Pattern pattern) {
    Matcher m = pattern.matcher(fullText);
    while (m.find()) {
      if (m.start() <= caretPosition && caretPosition <= m.end()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Creates a new builder for CompletionContext.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for CompletionContext.
   */
  public static final class Builder {
    private String fullText;
    private int caretPosition;
    private String lineText;
    private int lineOffset;
    private String tokenPrefix;
    private String expressionBefore;
    private ClassLoader classLoader;
    private Map<String, Object> metadata;

    private Builder() {}

    public Builder fullText(String fullText) {
      this.fullText = fullText;
      return this;
    }

    public Builder caretPosition(int caretPosition) {
      this.caretPosition = caretPosition;
      return this;
    }

    public Builder lineText(String lineText) {
      this.lineText = lineText;
      return this;
    }

    public Builder lineOffset(int lineOffset) {
      this.lineOffset = lineOffset;
      return this;
    }

    public Builder tokenPrefix(String tokenPrefix) {
      this.tokenPrefix = tokenPrefix;
      return this;
    }

    public Builder expressionBefore(String expressionBefore) {
      this.expressionBefore = expressionBefore;
      return this;
    }

    public Builder classLoader(ClassLoader classLoader) {
      this.classLoader = classLoader;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = metadata;
      return this;
    }

    public Builder metadata(String key, Object value) {
      if (this.metadata == null) {
        this.metadata = new HashMap<>();
      }
      this.metadata.put(key, value);
      return this;
    }

    /**
     * Builds the CompletionContext, automatically extracting member access
     * information from the text if not explicitly set.
     */
    public CompletionContext build() {
      // Auto-extract expression before dot if in member access context
      if (expressionBefore == null && fullText != null && caretPosition > 0) {
        String before = fullText.substring(0, caretPosition);
        Matcher m = MEMBER_ACCESS_PATTERN.matcher(before);
        if (m.find()) {
          expressionBefore = m.group(1);
          if (tokenPrefix == null) {
            tokenPrefix = m.group(2);
          }
        }
      }
      return new CompletionContext(this);
    }
  }
}
