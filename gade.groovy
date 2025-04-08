@GrabResolver(name='central', root='https://repo.maven.apache.org/maven2/')
@GrabResolver(name='gradle', root='https://repo.gradle.org/gradle/libs-releases')
@GrabResolver(name='jitpack.io', root='https://jitpack.io')
@GrabResolver(name='oss.sonatype.org-snapshot', root='https://oss.sonatype.org/content/repositories/snapshots')

@Grab('se.alipsa:gade:1.0.0-SNAPSHOT')
// We are running this script from groovy so makes no sense to duplicate dependencies for groovy
@GrabExclude('org.apache.groovy:groovy-all')
@GrabExclude('org.apache.groovy:groovy-ginq')
@GrabExclude('org.apache.groovy:groovy-macro-library')
import groovy.grape.Grape
import se.alipsa.gade.Gade

def cl = this.class.classLoader

try {
  cl.loadClass('javafx.application.Application')
} catch (ClassNotFoundException ignore) {
  println "JavaFX not found, adding dependencies..."
  addJavaFxDependencies()
}

Gade.main()

/*
 * Add JavaFX dependencies to the classpath based on the OS and architecture.
 * Grab cannot handle OS specific dependencies, so we have to do it manually using Grape.
 */
def addJavaFxDependencies() {
  String os = System.getProperty("os.name")
  String arch = System.getProperty("os.arch")
  String classifier = ""
  if (os == "Linux") {
    if (arch == "aarch64") {
      classifier = "linux-aarch64"
    } else {
      classifier = "linux"
    }
  } else if (os == "Mac OS X") {
    if (arch == "aarch64") {
      classifier = "mac-aarch64"
    } else {
      classifier = "mac"
    }
  } else if (os == "Windows") {
    if (arch == "amd64") {
      classifier = "win"
    } else {
      classifier = "win-x86"
    }
  } else {
    println "Unknown OS: $os, arch: $arch, unable to determine JavaFX classifier"
    System.exit(1)
  }
  def javafxVersion = System.getProperty("java.version") //'21.0.6'
  Grape.grab(group: 'org.openjfx', module: 'javafx-base', version: "$javafxVersion", classifier: "$classifier")
  Grape.grab(group: 'org.openjfx', module: 'javafx-graphics', version: "$javafxVersion", classifier: "$classifier")
  Grape.grab(group: 'org.openjfx', module: 'javafx-controls', version: "$javafxVersion", classifier: "$classifier")
  Grape.grab(group: 'org.openjfx', module: 'javafx-web', version: "$javafxVersion", classifier: "$classifier")
  Grape.grab(group: 'org.openjfx', module: 'javafx-media', version: "$javafxVersion", classifier: "$classifier")
  Grape.grab(group: 'org.openjfx', module: 'javafx-swing', version: "$javafxVersion", classifier: "$classifier")
}