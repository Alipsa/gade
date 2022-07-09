package se.alipsa.gride.chart;

import tech.tablesaw.api.Table;

/**
 * Represents a chart in some form.
 * A chart can be exported into various formats using the Plot class e.g:
 * <code>
 * AreaChart chart = new AreaChart(table);
 * inout.view(Plot.jfx(chart))
 * </code>
 */
public abstract class Chart {

  protected String title;
  protected Table[] series;

  public String getTitle() {
    return title;
  }

  public Table[] getSeries() {
    return series;
  }
}
