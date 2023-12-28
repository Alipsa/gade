import org.knowm.xchart.*

PieChart chart = new PieChartBuilder()
        .title("Pie Chart Example")
        .build();

chart.addSeries("A", 30);
chart.addSeries("B", 20);
chart.addSeries("C", 40);
chart.addSeries("D", 10);

io.display(chart)
