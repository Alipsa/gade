package se.alipsa.gride.chart;

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
}
