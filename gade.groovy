@GrabResolver(name='central', root='https://repo.maven.apache.org/maven2/')
@GrabResolver(name='gradle', root='https://repo.gradle.org/gradle/libs-releases')
@GrabResolver(name='jitpack.io', root='https://jitpack.io')
@GrabResolver(name='oss.sonatype.org-snapshot', root='https://oss.sonatype.org/content/repositories/snapshots')
@Grab('org.openjfx:javafx-controls:23.0.2')
@Grab('org.openjfx:javafx-web:23.0.2')
@Grab('org.openjfx:javafx-media:23.0.2')
@Grab('org.openjfx:javafx-swing:23.0.2')
@Grab('se.alipsa:gade:1.0.0-SNAPSHOT')
import se.alipsa.gade.Gade

Gade.main()