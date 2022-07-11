
@Grab('org.postgresql:postgresql:42.4.0')
@Grab('se.alipsa.groovy:data-utils:1.0-SNAPSHOT')
import se.alipsa.groovy.datautil.SqlUtil
import tech.tablesaw.api.Table

def sql = SqlUtil.newInstance('jdbc:postgresql://localhost:5432/rsg', 'rsg', 'Tian12Pan', 'org.postgresql.Driver')

sql.query("""select user_name, count(1) as sessions from dbsession group by user_name""") { rs -> {
  table = Table.read().db(rs)
  }
}
sql.close()
table.setName("Full")


chart = se.alipsa.gride.chart.AreaChart.create(
  "dbsessions", 
  table.column("user_name"),
  table.column("sessions"), 
  table.column("sessions").multiply(0.5))
inout.display(chart)

figure = se.alipsa.gride.chart.Plot.jsPlot(chart)
inout.display(figure, "plotly")
//tech.tablesaw.plotly.Plot.show(figure)
//inout.viewer("/home/per/programs/gride/testoutput/output15b71368-3e8b-42c5-9b1a-1f894c5dda56.html")
