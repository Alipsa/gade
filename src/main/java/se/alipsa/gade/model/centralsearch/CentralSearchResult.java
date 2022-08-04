package se.alipsa.gade.model.centralsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The Highlight is not relevant for us, so we don't model it and set ignore unknown to skip it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CentralSearchResult {
  public ResponseHeader responseHeader;
  public Response response;
}
