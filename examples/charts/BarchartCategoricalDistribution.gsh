// Show a diagram of a categorical variable
@Grab('se.alipsa.matrix:matrix-core:3.9.0')
@Grab('se.alipsa.matrix:matrix-pict:0.6.0')
import se.alipsa.groovy.svg.io.SvgWriter
import se.alipsa.matrix.core.Stat
import se.alipsa.matrix.pict.*

// Fair, Good, Very Good, Premium, Ideal
column = ['Fair', 'Good', 'Very Good', 'Premium', 'Ideal', 'Ideal', 'Premium']
freq = Stat.frequency(column)
chart = BarChart.createVertical('Diamonds cut distribution', freq, 'Value', 'Frequency')

io.displaySvg(SvgWriter.toXml(Plot.svg(chart)), 'Diamonds cut distribution')
