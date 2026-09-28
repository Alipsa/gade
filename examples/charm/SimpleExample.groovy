@Grab('se.alipsa.matrix:matrix-core:3.9.0')
@Grab('se.alipsa.matrix:matrix-charts:0.6.0')
import se.alipsa.groovy.svg.io.SvgWriter
import se.alipsa.matrix.charm.Scale

import static se.alipsa.matrix.charm.Charts.plot

def chart = plot(color: ['Orange', 'Blue'], value: [3, 5]) {
  mapping {
    x = 'color'
    y = 'value'
    fill = 'color'
  }
  layers {
    geomCol()
  }
  scale {
    fill = Scale.manual(Orange: '#FFA500', Blue: '#0000FF')
  }
  labels {
    title = 'Hello world'
    x = 'Color'
    y = 'Value'
  }
}.build()

io.displaySvg(SvgWriter.toXml(chart.render()), 'Charm chart')
