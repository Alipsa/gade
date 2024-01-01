import java.time.LocalDate
import se.alipsa.groovy.matrix.*
import se.alipsa.groovy.charts.*
import org.knowm.xchart.CategoryChartBuilder

import static se.alipsa.groovy.matrix.ListConverter.*


empData = new Matrix(
    emp_id: 1..5,
    emp_name: ["Rick","Dan","Michelle","Ryan","Gary"],
    salary: [623.3, 515.2, 611.0, 729.0, 843.25],
    bonus: [12.2, 10.4, 75.2, 19.1, 55.1],
    start_date: toLocalDates("2012-01-01", "2013-09-23", "2014-11-15", "2014-05-11", "2015-03-27"),
    [int, String, BigDecimal, Number, LocalDate]
)

chart = BarChart.createVertical("Salaries", empData, "emp_name", ChartType.STACKED, "salary", "bonus")
io.display(chart, "charts barchart as native jfx")

categoryChart =
    new CategoryChartBuilder()
        .title(chart.getTitle())
        .build()

categoryChart.getStyler().setLegendVisible(false)
categoryChart.getStyler().setStacked(chart.isStacked())

xVals = chart.getCategorySeries()

int i = 0
for (String serie in chart.valueSeriesNames) {
  categoryChart.addSeries(serie, xVals, toBigDecimals(chart.getValueSeries()[i++]))
}
io.display(categoryChart, "Direct display of XChart")

swingChart = SwingPlot.swing(chart)
io.display(swingChart, "Matrix chart to XChart")