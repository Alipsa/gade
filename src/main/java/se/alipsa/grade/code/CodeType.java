package se.alipsa.grade.code;

public enum CodeType {
  TXT("Text file"),
  XML("Xml file"),
  SQL("SQL script"),
  MD("Markdown file"),
  JAVA("Java code"),
  GROOVY("Groovy code"),
  JAVA_SCRIPT("Javascript code");

  CodeType(String displayValue) {
    this.displayValue = displayValue;
  }

  private final String displayValue;

  public String getDisplayValue() {
    return displayValue;
  }
}
