package se.alipsa.gade.code.completion;

public final class CompletionItem {

  public enum Kind { KEYWORD, CLASS, METHOD, FIELD, FUNCTION, TABLE, COLUMN, SNIPPET }

  private final String completion;
  private final String display;
  private final Kind kind;

  public CompletionItem(String completion, Kind kind) {
    this(completion, completion, kind);
  }

  public CompletionItem(String completion, String display, Kind kind) {
    this.completion = completion;
    this.display = display;
    this.kind = kind;
  }

  public String completion() { return completion; }
  public String display() { return display; }
  public Kind kind() { return kind; }

  public String renderLabel() {
    switch (kind) {
      case KEYWORD:  return completion + "  - keyword";
      case CLASS:    return completion + "  - class";
      case METHOD:   return display + "  - method";
      case FIELD:    return display + "  - field";
      case FUNCTION: return completion + "  - function";
      case TABLE:    return completion + "  - table";
      case COLUMN:   return display + "  - column";
      case SNIPPET:  return display + "  - snippet";
      default:       return display;
    }
  }
}
