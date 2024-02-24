@Grab('org.jfree:jfreechart:1.5.3')
@Grab(group='org.jfree', module='jfreechart-fx', version='1.0.1')
//io.addDependency('org.jfree:jfreechart:1.5.3')
//io.addDependency('org.jfree:jfreechart-fx:1.0.1')
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RadialGradientPaint
import java.awt.geom.Point2D
import org.jfree.chart.ChartFactory
import org.jfree.chart.fx.ChartViewer;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.HorizontalAlignment;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.general.DefaultPieDataset


def createGradientPaint(Color c1, Color c2) {
    def center = new Point2D.Float(0, 0)
    def radius = 200f
    def dist = [0.0f, 1.0f] as float[]
    return new RadialGradientPaint(center, radius, dist, new Color[] {c1, c2})
}

dataset = new DefaultPieDataset()
dataset.setValue("Samsung", 27.8)
dataset.setValue("Others", 55.3)
dataset.setValue("Nokia", 16.8)
dataset.setValue("Apple", 17.1)

chart = ChartFactory.createPieChart("Smart Phones Manufactured / Q3 2011", dataset)
chart.setBackgroundPaint(Color.BLACK)
// customise the title position and font
title = chart.getTitle()
title.setHorizontalAlignment(HorizontalAlignment.LEFT)
title.setPaint(new Color(240, 240, 240))
title.setFont(new Font("Arial", Font.BOLD, 26))

plot = chart.getPlot()
plot.setBackgroundPaint(Color.BLACK)
plot.setInteriorGap(0.04)
plot.setOutlineVisible(false)

// use gradients and white borders for the section colours
plot.setSectionPaint("Others",createGradientPaint(new Color(200, 200, 255), Color.BLUE))
plot.setSectionPaint("Samsung", createGradientPaint(new Color(255, 200, 200), Color.RED))
plot.setSectionPaint("Apple", createGradientPaint(new Color(200, 255, 200), Color.GREEN))
plot.setSectionPaint("Nokia", createGradientPaint(new Color(200, 255, 200), Color.YELLOW))
plot.setDefaultSectionOutlinePaint(Color.WHITE)
plot.setSectionOutlinesVisible(true)
plot.setDefaultSectionOutlineStroke(new BasicStroke(2.0f))

// customise the section label appearance
plot.setLabelFont(new Font("Courier New", Font.BOLD, 20))
plot.setLabelLinkPaint(Color.WHITE)
plot.setLabelLinkStroke(new BasicStroke(2.0f))
plot.setLabelOutlineStroke(null)
plot.setLabelPaint(Color.WHITE)
plot.setLabelBackgroundPaint(null)

// add a subtitle giving the data source
source = new TextTitle("Source: http://www.bbc.co.uk/news/business-15489523",
        new Font("Courier New", Font.PLAIN, 12))
source.setPaint(Color.WHITE)
source.setPosition(RectangleEdge.BOTTOM)
source.setHorizontalAlignment(HorizontalAlignment.RIGHT)
chart.addSubtitle(source)
viewer = new ChartViewer(chart)
io.display(viewer, "Smart Phones")

file = io.projectFile("SmartPhones.png")
io.save(viewer, file, 1024, 768, false)
io.display(file)
