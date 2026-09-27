import se.alipsa.matrix.datasets.Dataset
import se.alipsa.matrix.core.Stat
import se.alipsa.matrix.xchart.BarChart

frequency = Stat.frequency(Dataset.mtcars(), 'mpg')
chart = BarChart.builder(frequency).title('Mtcars mpg').x('Value').y('Frequency').build()
io.display(chart.exportSwing(), 'Mtcars mpg distribution')
