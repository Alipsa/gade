package se.alipsa.gade.code.completion;

/**
 * Represents a single code completion suggestion.
 */
public final class CompletionItem {

  public enum Kind {
    KEYWORD, CLASS, METHOD, FIELD, FUNCTION, TABLE, COLUMN, SNIPPET,
    VARIABLE, PARAMETER, PROPERTY, CONSTANT, INTERFACE, ENUM, MODULE
  }

  private final String completion;   // Text to insert when selected
  private final String display;      // Text shown in the completion popup
  private final Kind kind;
  private final String detail;       // Additional info (e.g., type, package name)
  private final String insertText;   // Alternative text to insert (if different from completion)
  private final int sortPriority;    // Lower values appear first (0 = highest priority)
  private final int cursorOffset;    // Cursor offset from end of insertText (e.g., -1 to place inside parens)

  /**
   * Creates a completion item with just the completion text and kind.
   */
  public CompletionItem(String completion, Kind kind) {
    this(completion, completion, kind);
  }

  /**
   * Creates a completion item with completion text, display text, and kind.
   */
  public CompletionItem(String completion, String display, Kind kind) {
    this(completion, display, kind, null, null, 100, 0);
  }

  /**
   * Creates a completion item with all fields.
   *
   * @param completion   text to insert (and filter against)
   * @param display      text shown in the popup
   * @param kind         the type of completion
   * @param detail       additional detail text (type, package, etc.)
   * @param insertText   alternative text to insert (null to use completion)
   * @param sortPriority lower values appear first
   * @param cursorOffset offset from end of insertText for cursor placement (e.g., -1 for inside parens)
   */
  public CompletionItem(String completion, String display, Kind kind,
                        String detail, String insertText, int sortPriority, int cursorOffset) {
    this.completion = completion;
    this.display = display;
    this.kind = kind;
    this.detail = detail;
    this.insertText = insertText;
    this.sortPriority = sortPriority;
    this.cursorOffset = cursorOffset;
  }

  /**
   * Returns the text used for filtering and basic insertion.
   */
  public String completion() { return completion; }

  /**
   * Returns the text shown in the completion popup.
   */
  public String display() { return display; }

  /**
   * Returns the kind of completion item.
   */
  public Kind kind() { return kind; }

  /**
   * Returns additional detail text (e.g., type info, package name).
   * May be null.
   */
  public String detail() { return detail; }

  /**
   * Returns the text to actually insert when this item is selected.
   * Falls back to completion() if not explicitly set.
   */
  public String insertText() {
    return insertText != null ? insertText : completion;
  }

  /**
   * Returns the sort priority (lower = higher priority).
   */
  public int sortPriority() { return sortPriority; }

  /**
   * Returns the cursor offset from end of insertText.
   * 0 means cursor at end, -1 means cursor 1 char before end (inside parens), etc.
   */
  public int cursorOffset() { return cursorOffset; }

  /**
   * Renders the label for display in the completion popup.
   */
  public String renderLabel() {
    String kindLabel = kindLabel();
    String base = kind == Kind.METHOD || kind == Kind.FIELD || kind == Kind.COLUMN || kind == Kind.SNIPPET
        ? display
        : completion;

    if (detail != null && !detail.isEmpty()) {
      return base + "  " + detail + "  - " + kindLabel;
    }
    return base + "  - " + kindLabel;
  }

  private String kindLabel() {
    return switch (kind) {
      case KEYWORD -> "keyword";
      case CLASS -> "class";
      case METHOD -> "method";
      case FIELD -> "field";
      case FUNCTION -> "function";
      case TABLE -> "table";
      case COLUMN -> "column";
      case SNIPPET -> "snippet";
      case VARIABLE -> "variable";
      case PARAMETER -> "parameter";
      case PROPERTY -> "property";
      case CONSTANT -> "constant";
      case INTERFACE -> "interface";
      case ENUM -> "enum";
      case MODULE -> "module";
    };
  }

  /**
   * Creates a builder for constructing CompletionItems with many options.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for CompletionItem.
   */
  public static final class Builder {
    private String completion;
    private String display;
    private Kind kind = Kind.KEYWORD;
    private String detail;
    private String insertText;
    private int sortPriority = 100;
    private int cursorOffset = 0;

    private Builder() {}

    public Builder completion(String completion) {
      this.completion = completion;
      return this;
    }

    public Builder display(String display) {
      this.display = display;
      return this;
    }

    public Builder kind(Kind kind) {
      this.kind = kind;
      return this;
    }

    public Builder detail(String detail) {
      this.detail = detail;
      return this;
    }

    public Builder insertText(String insertText) {
      this.insertText = insertText;
      return this;
    }

    public Builder sortPriority(int sortPriority) {
      this.sortPriority = sortPriority;
      return this;
    }

    public Builder cursorOffset(int cursorOffset) {
      this.cursorOffset = cursorOffset;
      return this;
    }

    public CompletionItem build() {
      if (display == null) display = completion;
      return new CompletionItem(completion, display, kind, detail, insertText, sortPriority, cursorOffset);
    }
  }
}
