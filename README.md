# Grade
## A Groovy based Analytics Development Environment for Analysts and Data Scientists 

Grade started off as a fork of [Ride](/alipsa/ride) but I wanted a simpler, more integrated set-up for Groovy based Data Science work. 
Since there is no equivalent to a data.frame OOTB in Java or Groovy, I decided to use [Tablesaw](/jtablesaw/tablesaw) 
and integrate it as much as possible to get a similar experience as in R.

The purpose of this ODE is to provide a groovy development environment for data analysis, visualization, model building, and machine learning. 
It runs Groovy code in the Groovy ScriptEngine thus allowing you to be conveniently used for ad-hoc work as well as for creating
packages that can easily be integrated into a larger JVM based context (e.g Spring Boot, Grails, Play etc.).

It is fully functional i.e. it is possible to create, open, save and execute Groovy scripts,
run selected text, ctrl + enter execution of current line or selected text,
see variables created, syntax highlighting for XML, SQL, Java, Groovy, Javascript etc.
It also has wizards to create Groovy applications or Libraries and of course, integrated support 
for git and maven making it a very convenient development environment for analytics and data science.

![Screenshot](https://raw.githubusercontent.com/perNyfelt/grade/master/docs/Screenshot.png "Screenshot")

### Installing and Running Grade
Go to the releases tab and expand the assets section at the bottom of the release.
Unzip the zip file grade-<version>-dist.zip e.g. grade-1.0.0-dist.zip into a directory of choice.

There are icons for windows and Linux in the base folder of the unpacked zip that can be used to create a desktop shortcut/launcher.

Use grade.cmd or grade.sh to start Grade. There is also an (experimental) executable for Windows aimed to
provide a slightly more "integrated" experience. To run it, make sure you have JAVA_HOME and path set to
s java 17 version that includes javafx. I have seen some issues running with OpenJDK which seems to be
related to not setting registry values in the same way as the Oracle JDK. The workaround to this would be to
bundle the entire JDK which is not something I want to do so if the exe is not working for you, use the cmd script.

If you want to run the bash script from windows git bash (msys), create a shortcut with the comment
`D:\whatever\Git\bin\bash.exe ./grade.sh`
Where D:\whatever is the path to your git for windows installation
Also set the working dir in the shortcut to wherever you installed Grade.

If you want to override or customize startup options you can create a file called env.sh (or env.cmd) in the base directory
where you installed (unzipped) Grade. An example would be to set the right java environment variables so the launcher works
properly, e.g.
```shell script
#!/usr/bin/env bash

# Java is managed by SDK, load it
source ~/.sdkman/bin/sdkman-init.sh
# Specify the java version to use
sdk use 17.0.3.1.fx-librca
# Scale the application 200% as we have a Hi-DPI screen, see https://wiki.archlinux.org/index.php/HiDPI#Java_applications
JAVA_OPTS="-Dglass.gtk.uiScale=200%"
```
The last setting (JAVA_OPTS) is a special variable to add system properties (-D values) to java starting up.

### A SQL script screenshot
Showing the result of a select query in the viewer tab and the connection view that is shown when you right click
a connection and choose "view connection".

![SQL Screenshot](https://raw.githubusercontent.com/perNyfelt/grade/master/docs/SQLScreenshot.png "SQL Screenshot")

# Issues and Feature requests
For any issues or feature request etc. on Grade please use the [github issue feature](https://github.com/perNyfelt/grade/issues)


# Building and compiling

To build Grade, simply do `mvn clean install`

## 3:rd party software used
Note: only direct dependencies are listed below.

### org.apache.groovy:groovy-all
Makes it possible to run Groovy scripts. 
Apache Software License, Version 2.0.

### tech.tablesaw:tablesaw-core, tablesaw-jsplot etc.
tools for working with tables and columns with the ability to create statistical models and visualizations.
Apache License, Version 2.0

### org.fxmisc.richtext:richtextfx
Used to color (syntax highlight) code.

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
Used to validate and analyse SQL code. Apache Software License, Version 2.0.

### org.apache.maven.shared:maven-invoker,maven-shared-utils
Used to run maven (run pom.xml files). Apache Software License, Version 2.0.

### org.eclipse.jgit:org.eclipse.jgit
Used to provide git support. Eclipse Distribution License v1.0

### io.github.classgraph:classgraph
Very fast class and module loader scanning library. Used for autocompletion among other things.
MIT License

# Contributing
If you are interested in helping out, reporting issues, creating tests or implementing new features
are all warmly welcome. See also [todo](todo.md) for roadmap.
