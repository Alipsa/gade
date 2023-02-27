package se.alipsa.gade.model;

public enum ReportType {

  UNMANAGED, GROOVY, GMD, R, MDR;


  public boolean equals(String type) {
    return name().equals(type);
  }
  public boolean equals(ReportType type) {
    if (type == null) {
      return false;
    }
    return this.name().equals(type.name());
  }

  @Override
  public String toString() {
    return name();
  }

  public static ReportType fromString(String type) {
    return type == null ? null : ReportType.valueOf(type);
  }
}
