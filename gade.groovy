@GrabResolver(name='central', root='https://repo.maven.apache.org/maven2/')
@GrabResolver(name='gradle', root='https://repo.gradle.org/gradle/libs-releases')
@GrabResolver(name='jitpack.io', root='https://jitpack.io')
@GrabResolver(name='oss.sonatype.org-snapshot', root='https://oss.sonatype.org/content/repositories/snapshots')
@Grab('org.openjfx:javafx-controls:21.0.6')
@Grab('org.openjfx:javafx-web:21.0.6')
@Grab('org.openjfx:javafx-media:21.0.6')
@Grab('org.openjfx:javafx-swing:21.0.6')
@Grab('se.alipsa:gade:1.0.0-SNAPSHOT')
// We are running this from groovy so makes no sense to have duplicate dependencies for groovy
@GrabExclude('org.apache.groovy:groovy-all')
@GrabExclude('org.apache.groovy:groovy-ginq')
@GrabExclude('org.apache.groovy:groovy-macro-library')
import se.alipsa.gade.Gade

Gade.main()