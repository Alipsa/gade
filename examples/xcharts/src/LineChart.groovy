import se.alipsa.matrix.core.*

  
salesData = new Matrix('Salesdata',
  [
    yearMonth: [202301, 202302, 202303, 202304, 202305, 202306, 202307, 202308, 202309, 202310, 202311, 202312],
    sales: [23,14,15,24,34,36,22,45,43,17,29,25]
  ],
  [int, int]
)

chart = LineChart.create(salesData, 'yearMonth', 'sales')
io.display(chart)
io.display(SwingPlot.swing(chart))