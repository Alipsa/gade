@Grab('se.alipsa.matrix:matrix-core:3.9.0')
@Grab('se.alipsa.matrix:matrix-stats:2.5.3')
@Grab('se.alipsa.matrix:matrix-pict:0.6.0')
import se.alipsa.groovy.svg.io.SvgWriter
import se.alipsa.matrix.core.Matrix
import se.alipsa.matrix.pict.Histogram as PictHistogram
import se.alipsa.matrix.pict.Plot

def airquality = Matrix.builder().data(new File(io.scriptDir(), "../data/airquality.csv")).build()
def data = Matrix.builder()
    .data(Temp: airquality.column('Temp').collect { Integer.parseInt(it.toString()) })
    .types(Integer)
    .build()
chart = PictHistogram.builder(data).title('Airquality.Temp').x('Temp').bins(5).build()
io.displaySvg(SvgWriter.toXml(Plot.svg(chart)), "Histogram")
