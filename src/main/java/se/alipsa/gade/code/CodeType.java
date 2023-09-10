package se.alipsa.gade.code;

public enum CodeType {
  /** Plain text content*/
  TXT("Text file"),

  /** Extensible markup language content */
  XML("Xml file"),

  /** Structured query language content */
  SQL("SQL script"),

  /** Groovy markdown content */
  GMD("Groovy Markdown"),

  /** Plain markdown content */
  MD("Markdown file"),

  /** Java code content */
  JAVA("Java code"),
  /** Groovycode content */
  GROOVY("Groovy code"),

  /** Gradle build script content */
  GRADLE("Gradle build script"),

  /** Javascript content */
  JAVA_SCRIPT("Javascript code"),

  /** R code content */
  R("R code"),

  /** SAS code content */
  SAS("SAS code");

  CodeType(String displayValue) {
    this.displayValue = displayValue;
  }

  private final String displayValue;

  /** The display value is a "user-friendly" version of the enum */
  public String getDisplayValue() {
    return displayValue;
  }
}
