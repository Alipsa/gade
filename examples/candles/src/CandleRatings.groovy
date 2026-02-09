/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Original code is here: https://github.com/paulk-asert/groovy-data-science/blob/master/subprojects/Candles/src/main/groovy/CandleRatings.groovy
 * Slightly modified to behave nicely in Gade
 */
/*
@Grab("tech.tablesaw:tablesaw-core:0.44.1")
@Grab("tech.tablesaw:tablesaw-excel:0.44.1")
@Grab("tech.tablesaw:tablesaw-html:0.44.1")
@Grab("tech.tablesaw:tablesaw-aggregate:0.44.1")
@GrabConfig(systemClassLoader=true)
@Grab(group='org.apache.logging.log4j', module='log4j-slf4j2-impl', version='2.25.3')
@Grab(group='org.apache.logging.log4j', module='log4j-core', version='2.25.3')
*/
import tech.tablesaw.api.*
import tech.tablesaw.io.xlsx.XlsxReader
import tech.tablesaw.plotly.components.*
import tech.tablesaw.plotly.traces.ScatterTrace
import tech.tablesaw.plotly.traces.Trace
import tech.tablesaw.selection.Selection

import java.time.*
import java.util.function.Function

import static java.time.Month.JANUARY
import static tech.tablesaw.aggregate.AggregateFunctions.mean
import static tech.tablesaw.api.QuerySupport.and
import static tech.tablesaw.io.xlsx.XlsxReadOptions.builder
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.Level
import org.slf4j.LoggerFactory

Configurator.setRootLevel(Level.INFO)

def log = LoggerFactory.getLogger('CandleRatings')

log.info("hello")

javaFXPlatform = {
  def os = System.getProperty('os.name').toLowerCase()
  if (os.contains('mac')) {
    def arch = System.getProperty('os.arch').toLowerCase()
    return arch.contains('aarch64') || arch.contains('arm') ? 'mac-aarch64' : 'mac'
  } else if (os.contains('linux')) {
    return 'linux'
  } else if (os.contains('win')) {
    return 'win'
  }
  throw new Exception("Unsupported OS: ${os}")
}.call()

if (! binding.hasVariable('io')) {
  groovy.grape.Grape.grab(group:"se.alipsa.gi", module: "gi-fx", version:"0.3.0")
  groovy.grape.Grape.grab(group:"org.openjfx", module: "javafx-web", version:"23.0.2", classifier: javaFXPlatform)
  groovy.grape.Grape.grab(group:"org.openjfx", module: "javafx-base", version:"23.0.2", classifier: javaFXPlatform)
  groovy.grape.Grape.grab(group:"org.openjfx", module: "javafx-controls", version:"23.0.2", classifier: javaFXPlatform)
  groovy.grape.Grape.grab(group:"org.openjfx", module: "javafx-graphics", version:"23.0.2", classifier: javaFXPlatform)
  groovy.grape.Grape.grab(group:"org.openjfx", module: "javafx-media", version:"23.0.2", classifier: javaFXPlatform)
  groovy.grape.Grape.grab(group:"org.openjfx", module: "javafx-swing", version:"23.0.2", classifier: javaFXPlatform)
  binding.setVariable('io', this.class.classLoader.loadClass("se.alipsa.gi.fx.InOut").getDeclaredConstructor().newInstance())
}
// helper function
List<Trace> traces(URL url, String lineColor, String markerColor) {
    def table = new XlsxReader().read(builder(url).build())

    table.addColumns(
        DateColumn.create('YearMonth', table.column('Date').collect { LocalDate.of(it.year, it.month, 15) })
    )
    def janFirst2017 = LocalDateTime.of(2017, JANUARY, 1, 0, 0)
    Function<Table, Selection> from2017 = r -> r.dateTimeColumn('Date').isAfter(janFirst2017)
    Function<Table, Selection> top3 = r -> r.intColumn('CandleID').isLessThanOrEqualTo(3)

    def byMonth = table.sortAscendingOn('Date')
            .where(and(from2017, top3))
            .summarize('Rating', mean).by('YearMonth')
    def byDate = table.sortAscendingOn('Date')
            .where(and(from2017, top3))
            .summarize('Rating', mean).by('Date')

    def averaged = ScatterTrace.builder(byMonth.dateColumn('YearMonth'), byMonth.nCol('Mean [Rating]'))
            .mode(ScatterTrace.Mode.LINE)
            .line(Line.builder().width(5).color(lineColor).shape(Line.Shape.SPLINE).smoothing(1.3).build())
            .build()
    def scatter = ScatterTrace.builder(byDate.dateTimeColumn('Date'), byDate.nCol('Mean [Rating]'))
            .marker(Marker.builder().opacity(0.3).color(markerColor).build())
            .build()
    [averaged, scatter]
}

void display(Figure figure, String... titleOpt) {
  String title = titleOpt.length > 0 ? titleOpt[0] : ''
  Page page = Page.pageBuilder(figure, "target").build()
  String output = page.asJavascript()
  io.viewHtml(output, title)
}
  
Layout layout(String variant) {
    Layout.builder("Top 3 $variant candles Amazon reviews 2017-2020", 'Date', 'Average daily rating (1-5)')
            .showLegend(false).width(1000).height(500).build()
}

// create the start of COVID line
covidReported = LocalDateTime.of(2020, JANUARY, 20, 0, 0)
reported = Table.create(DateTimeColumn.create('Date'), IntColumn.create('Val'))
reported.appendRow().with {setDateTime('Date', covidReported); setInt('Val', 1) }
reported.appendRow().with {setDateTime('Date', covidReported); setInt('Val', 5) }
line = ScatterTrace.builder(reported.dateTimeColumn('Date'), reported.nCol('Val'))
        .mode(ScatterTrace.Mode.LINE)
        .line(Line.builder().width(2).dash(Line.Dash.DOT).color('red').build())
        .build()

url = io.projectFile('data/Scented_all.xlsx').toURL()
(sAverage, sScatter) = traces(url, 'seablue', 'lightskyblue')

url = io.projectFile('data/Unscented_all.xlsx').toURL()
(uAverage, uScatter) = traces(url, 'seagreen', 'lightgreen')

display(new Figure(layout(''), sAverage, sScatter, uAverage, uScatter, line), 'Overview')
display(new Figure(layout('scented'), sAverage, sScatter, line), 'ScentedRatings')
display(new Figure(layout('unscented'), uAverage, uScatter, line), 'UnscentedRatings')
