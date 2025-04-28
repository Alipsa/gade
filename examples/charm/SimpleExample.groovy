import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.paint.Color
import javafx.stage.Modality
import se.alipsa.groovy.charts.charm.*
import javafx.scene.shape.Rectangle


CharmChartFx chart = new CharmChartFx()
chart.addTitle("Hello world top", Position.TOP_RIGHT)
chart.addTitle("Hello world left", Position.LEFT_CENTER)
chart.addLegend(['Orange': Color.ORANGE, 'Blue': Color.BLUE], Position.BOTTOM_CENTER)
        .setBackground(Color.LIGHTBLUE)
        .setBorder(Color.RED)


PlotPane plotPane = new PlotPane(300, 200)
Rectangle rect = new Rectangle(10, 10, 200, 200);
rect.setFill(Color.BLUE);
def gc = plotPane.getGraphicsContext2D()
gc.setFill(rect.getFill())
gc.fillRect(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight())

chart.add(plotPane)
io.display(chart, "Charm chart")