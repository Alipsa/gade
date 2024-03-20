// Show a diagram of a categorical variable
import se.alipsa.groovy.datasets.*
import se.alipsa.groovy.matrix.*
import se.alipsa.groovy.charts.*

// Fair, Good, Very Good, Premium, Ideal
column = Dataset.diamonds()["cut"]
freq = Stat.frequency(column)
chart = BarChart.createVertical('Diamonds cut distribution', freq, 'Value', 'Frequency')

io.display(chart)