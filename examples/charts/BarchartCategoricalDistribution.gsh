// Show a diagram of a categorical variable
import se.alipsa.matrix.core.Stat
import se.alipsa.matrix.pict.*

// Fair, Good, Very Good, Premium, Ideal
column = ['Fair', 'Good', 'Very Good', 'Premium', 'Ideal', 'Ideal', 'Premium']
freq = Stat.frequency(column)
chart = BarChart.createVertical('Diamonds cut distribution', freq, 'Value', 'Frequency')

io.display(chart)