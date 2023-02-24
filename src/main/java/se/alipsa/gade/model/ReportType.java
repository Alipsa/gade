package se.alipsa.gade.model;

public enum ReportType {

  UNMANAGED, GROOVY, GMD, R, MDR;

  public boolean equals(String type) {
    return name().equals(type);
  }

}
