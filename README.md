# Gade
## A Groovy based Analytics Development Environment for Analysts and Data Scientists 

Gade started off as a fork of [Ride](/Alipsa/ride), but I wanted a simpler, more integrated set-up for Groovy based Data Science work.
Since there is no equivalent to a data.frame OOTB in Java or Groovy, I created the [matrix](https://github.com/Alipsa/matrix) library for that.

The purpose of this IDE is to provide a groovy development environment for data analysis, visualization, model building, and machine learning. 
It runs Groovy code in the Groovy ScriptEngine thus allowing you to be conveniently used for ad-hoc work as well as for creating
packages that can easily be integrated into a larger JVM based context (e.g. Spring Boot, Grails, Play etc.).

It is fully functional i.e. it is possible to create, open, save and execute Groovy scripts,
run selected text, ctrl + enter execution of current line or selected text,
see variables created, syntax highlighting for XML, SQL, Java, Groovy, Javascript etc.
It also has wizards to create Groovy applications or Libraries and of course, integrated support 
for git and gradle making it a very convenient development environment for analytics and data science.

![Screenshot](https://raw.githubusercontent.com/perNyfelt/gade/master/docs/Screenshot.png "Screenshot")

### Installing and Running Gade
#### Run Gade via maven
If you have maven and java 21 installed, the easiest way to run Gade is to
copy the gadeMavenRunner.sh script from the releases tab to a directory of choice and just run the script to start Gade.

#### Run Gade via groovy
If you have groovy installed, you can instead start gade using the gade.groovy script as follows
`groovy gade.groovy`

#### To install a zipped distribution
The Zip distribution is a self-contained package of Gade which does not need any other pre-requisites.
Go to the releases tab and expand the assets section at the bottom of the release.
Unzip the zip file gade-platform-version.zip e.g. gade-linux-1.0.0.zip into a directory of choice.

There are icons for windows and Linux in the base folder of the unpacked zip that can be used to create a desktop shortcut/launcher.

Use gade.cmd or gade.sh to start Gade.

If you want to run the bash script from windows git bash (msys), create a shortcut with the command
`D:\whatever\Git\bin\bash.exe ./gade.sh`
Where `D:\whatever` is the path to your git for windows installation
Also set the working dir in the shortcut to wherever you installed Gade.

If you want to override or customize startup options you can create a file called env.sh (or env.cmd) in the base directory
where you installed (unzipped) Gade, e.g:
```shell script
#!/usr/bin/env bash

# Add some additional memory
# Scale the application 200% as we have a Hi-DPI screen, see https://wiki.archlinux.org/index.php/HiDPI#Java_applications
# use the Marlin java2d rendering engine
JAVA_OPTS="-Xmx16G -Dglass.gtk.uiScale=200% -Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine"
```
JAVA_OPTS is a special variable to add system properties (-D values) to java starting up.

Equivalent in windows is (note the placement of quotation marks):
```shell
set "JAVA_OPTS=-Xmx16G -Dglass.gtk.uiScale=200% -Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine"
```

On Windows, you can also run Gade in java instead of javaw (so you can see the console) by setting the JAVA_CMD variable 
in the env.cmd i.e:
```shell
SET JAVA_CMD=path\to\java
```
JAVA_CMD can also be used to set another java version than the "default".

Other parameters in the env script are
- SPLASH_TIME the number of seconds to show the splash screen e.g `SPLASH_TIME=5`

### A SQL script screenshot
Showing the result of a select query in the viewer tab and the connection view that is shown when you right-click
a connection and choose "view connection".

![SQL Screenshot](https://raw.githubusercontent.com/perNyfelt/gade/master/docs/SQLScreenshot.png "SQL Screenshot")

# Issues and Feature requests
For any issues or feature request etc. on Gade please use the [github issue feature](https://github.com/perNyfelt/gade/issues)

# Documentation
 - [Cookbook](docs/cookbook/cookbook.md) - an introduction to use Gade to solve common Data Scientist tasks.
 - [Freecharts](docs/FreeCharts.md) - A short description on how to use the Freecharts library with Gade
 - User Manual - can be found in Gade (Help -> User Manual)
 - [Wiki](https://github.com/perNyfelt/gade/wiki) - Wiki pages on various topics related to Gade

# Examples
In the [examples](https://github.com/perNyfelt/gade/tree/main/examples) dir you can find a lot of examples of using Gade.
Many of them are taken from Paul Kings [groovy-data-science](https://github.com/paulk-asert/groovy-data-science) project at Github
and modified slightly to work interactively in Gade.


# Building and compiling

To build Gade, simply do `gradle build`

A quick development run is simply `gradle run`

To create multi-platform distributions do `gradle clean build runtimeZip`
You will find the zip distros (gradle-*.zip) in the build dir, and the source jar and javadoc jar in build/libs

The install.sh script is an example of how to automate installation of the platform specific build on your machine.

## 3:rd party software used
Note: only direct dependencies are listed below.

### org.apache.groovy:groovy-all
Makes it possible to run Groovy scripts. 
Apache Software License, Version 2.0.

### io.github.classgraph:classgraph
Very fast class and module loader scanning library. Used for autocompletion among other things.
MIT License

### org.fxmisc.richtext:richtextfx
Base for all code editors. Used to color (syntax highlight) code etc.
Copyright (c) 2013-2017, Tomas Mikula and contributors under BSD 2-Clause "Simplified" License

### org.girod:fxsvgimage
Allows conversion of svg files to javafx Image nodes.
MIT license.

### org.apache:log4j
The logging framework used.
Apache 2.0 license

### com.fasterxml.jackson.core:jackson-core and jackson-databind
Used for JSON handling in various places.
Copyright Fasterxml under Apache 2.0 license.

### org.apache.tika:tika-core
Used to detect file types as Files.probeContentType is inconsistent over different OS's;
Apache 2.0 license.

### org.apache.tika:tika-parsers
Used to detect file encoding.
Apache 2.0 license.

### org.apache.commons-lang3
Used for time and string formatting
Apache 2.0 license.

### org.apache.commons-io
Used for reading files content
Apache 2.0 license.

###  com.github.jsqlparser:jsqlparser
Used to validate and analyse SQL code. 
Apache Software License, Version 2.0.

### org.apache.maven:maven-artifact
Used to do semantic version comparisons. 
Apache Software License, Version 2.0.

### org.eclipse.jgit:org.eclipse.jgit
Used to provide git support. 
Eclipse Distribution License v1.0

### se.alipsa.matrix + all sub projects
A data container, statistics and visualization library for Groovy, similar to Joinery and Tablesaw for Java.
MIT license.

### se.alipsa.groovy:data-utils
Makes it possible to load jdbc drivers without having to resort to the system classloader.
MIT license.

### se.alipsa:simple-rest
Provides support for REST interaction with Munin.
MIT license.

### se.alipsa.groovy:gmd
Provides support for processing Groovy Markdown (gmd).
MIT license.

# Contributing
If you are interested in helping out, reporting issues, creating tests or implementing new features
are all warmly welcome. See also [todo](todo.md) for roadmap.

