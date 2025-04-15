/* This installation script fetches the artifacts from the nexus repos using Grape.grab
   It requires
   1. Java 21 or later installed and on the path
   2. groovy 4.x or later installed and on the path

   The directory layout and name is due to MacOS requirements but works in Linux and Windows as well
 */
@Grab('org.slf4j:slf4j-simple:2.0.17')
@Grab('se.alipsa:maven-3.9.4-utils:1.0.3')
import se.alipsa.mavenutils.MavenUtils
import groovy.ant.AntBuilder

def gadeVersion="1.0.0-SNAPSHOT"
def appDir = new File("gade.app")
if (!appDir.exists()) {
  appDir.mkdirs()
}
def libDir = new File(appDir, "lib")
if (!libDir.exists()) {
  libDir.mkdirs()
}
def jfxDir = new File(libDir, "jfx")
if (!jfxDir.exists()) {
  jfxDir.mkdirs()
}
AntBuilder ant = new AntBuilder()
def mvnUtil = new MavenUtils()

println "Creating installation of Gade version ${gadeVersion} in ${appDir.absolutePath}"
File pomFile = mvnUtil.resolveArtifact("se.alipsa", "gade-runner", null, 'pom', gadeVersion)
println "Fetching dependencies declared in pom file: ${pomFile}"

Set<File> dependencies = mvnUtil.resolveDependencies(pomFile)

dependencies.each { file ->
  if (!file.name.endsWith('.jar')) {
    return
  }
  if (file.name.contains('javafx-')) {
    ant.copy(
        file:file.canonicalPath,
        todir:jfxDir.canonicalPath,
    )
  } else {
    ant.copy(
        file:file.canonicalPath,
        todir:libDir.canonicalPath,
    )
  }
}
def jarName= "gade-${gadeVersion}.jar"
ant.unzip(
    src: new File(libDir, jarName).canonicalPath,
    dest: appDir.canonicalPath,
    overwrite: true,
) {
  patternset {
    include(name: "mac/**")
    include(name: "script/*")
  }
}
def contentsDir = new File(appDir, "Contents")
if (!contentsDir.exists()) {
  contentsDir.mkdirs()
}
ant.move(todir: contentsDir.canonicalPath) {
  fileset(dir: new File(appDir, "mac/Contents/").canonicalPath) {
    include(name: "**/*")
  }
}
ant.delete(dir: new File(appDir, "mac").canonicalPath)
ant.chmod(dir: new File(contentsDir, "MacOS").canonicalPath, perm: "ugo+rx", includes: "*")

ant.move(todir: appDir.canonicalPath, flatten: true) {
  fileset(dir: appDir.canonicalPath) {
    include(name: "script/**")
  }
}
ant.delete(dir: new File(appDir, "script").canonicalPath)
ant.chmod(dir: appDir.canonicalPath, perm: "ugo+rx", includes: "*.sh")

def javaHome = System.getProperty("java.home")
new File(appDir, "env.sh")
    .write("JAVA_CMD=$javaHome/bin/java\n")

println()
println "Gade installed in $appDir finished, you can run it with $appDir/gade.sh"
println "You can create a shortcut to the app by running $appDir/createLauncher.sh (or createShortcut.ps1 on windows)"