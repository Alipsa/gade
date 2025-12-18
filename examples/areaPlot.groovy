@Grab('tech.tablesaw:tablesaw-core:0.44.4')
@Grab('tech.tablesaw:tablesaw-jsplot:0.44.4')
import tech.tablesaw.api.Table
import tech.tablesaw.plotly.api.AreaPlot
import tech.tablesaw.plotly.components.Page

robberies = Table.read().csv(new File(io.scriptDir(), "/data/boston-robberies.csv"));
robberies.setName("Boston Robberies by month: Jan 1966-Oct 1975")

def plot = AreaPlot.create(
  "Boston Robberies by month: Jan 1966-Oct 1975", 
  robberies, 
  "Record", 
  "Robberies"
)
//println plot.asJavaScript()
Page page = Page.pageBuilder(plot, "target").build();
String output = page.asJavascript();
    
io.view(output, "Boston Robberies")

tech.tablesaw.plotly.Plot.show(
    AreaPlot.create(
        "Boston Robberies by month: Jan 1966-Oct 1975", robberies, "Record", "Robberies"));
