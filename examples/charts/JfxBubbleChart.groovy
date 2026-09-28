@Grab('se.alipsa.matrix:matrix-core:3.9.0')
@Grab('se.alipsa.matrix:matrix-pict:0.6.0')
import se.alipsa.groovy.svg.io.SvgWriter
import se.alipsa.matrix.core.Matrix
import se.alipsa.matrix.pict.BubbleChart
import se.alipsa.matrix.pict.Plot

def product1Weeks = [3, 12, 15, 22, 28, 35, 42, 49]
def product1Budgets = [35, 60, 15, 30, 20, 41, 17, 30]
def product2Weeks = [8, 13, 15, 24, 38, 40, 45, 47]
def product2Budgets = [15, 23, 45, 30, 78, 41, 57, 23]

def data = Matrix.builder().data(
    week: product1Weeks + product2Weeks,
    budget: product1Budgets + product2Budgets,
    size: [5] * 16,
    product: (['Product 1'] * 8) + (['Product 2'] * 8)
).types(Integer, Integer, Integer, String).build()

def chart = BubbleChart.builder(data)
    .title('Budget Monitoring')
    .x('week')
    .y('budget')
    .size('size')
    .group('product')
    .xAxisTitle('Week')
    .yAxisTitle('Product Budget')
    .build()

io.displaySvg(SvgWriter.toXml(Plot.svg(chart)), 'bubble chart')

def file = io.projectFile('bubbleChart.png')
Plot.png(chart, file, 800, 600)
io.display(file)
