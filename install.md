# Installation and configuration

## Installing and Running Gade
Go to the releases tab and expand the assets section at the bottom of the release.
Unzip the zip file gade-platform-version.zip e.g. gade-linux-1.0.0.zip into a directory of choice.

There are icons for windows and Linux in the base folder of the unpacked zip that can be used to create a desktop shortcut/launcher.

Use gade.cmd or gade.sh to start Gade.

If you want to run the bash script from windows git bash (msys), create a shortcut with the comment
`D:\whatever\Git\bin\bash.exe ./gade.sh`
Where D:\whatever is the path to your git for windows installation
Also set the working dir in the shortcut to wherever you installed Gade.


# The env settings file
env.cmd in windows or env.sh in linux can be used to set additional configuration in Gade

On Windows, you can run Gade in java instead of javaw (so you can see the console) by setting the JAVA_CMD variable
in the env.cmd i.e:
```shell
SET JAVA_CMD=java
```

## Java executable
To change the java executable to use
in windows (env.cmd)
```
SET "JAVA_HOME=C:\Program Files\jdk-17-0-6"
SET PATH=%JAVA_HOME&\bin;%PATH%
```

in Linux (env.sh):
```
JAVA_HOME="/usr/local/jdk17"
export PATH="${JAVA_HOME}/bin:${PATH}"
```

## The JAVA_OPTS variable
The JAVA_OPTS variable controls all kinds of configuration you want to pass to Java when gade is 
starting up. It can consist of several settings separated by space.

If you want to override or customize startup options you can create a file called env.sh (or env.cmd) in the base directory
where you installed (unzipped) Gade, e.g:
```shell script
#!/usr/bin/env bash

# Add some additional memory (16 GB)
# Scale the application 200% as we have a Hi-DPI screen, see https://wiki.archlinux.org/index.php/HiDPI#Java_applications
JAVA_OPTS="-Xmx16G -Dglass.gtk.uiScale=200%"
```

### Scaling
The following scales gade to 125% of default

in windows (env.cmd)
```
SET "JAVA_OPTS=-Dglass.win.uiScale=1.25"
```

in Linux (env.sh):
```
JAVA_OPTS="-Dglass.gtk.uiScale=1.25 -Djdk.gtk.version=2"
```

### Additional memory

The following sets the max memory to 16 Gb:

in windows (env.cmd)
```
SET "JAVA_OPTS=-Xmx16G"
```

in Linux (env.sh):
```
JAVA_OPTS="-Xmx16G"
```

## Configuration options
Gade configurations are set in the Meny (Tools -> Global Options). The following configuration can be made:

### Console Max size
This describes how many character that are allowed in the console. After the limit is reaches no console output will
be written.

### Style theme
Gade comes with 3 predefined styles. Blue, Dark and Bright. 

The Bright Theme is more or less the Java FX default theme but most care has gone into making the Blue Theme 
a pleasant coloring theme for Groovy development.

### Use build.gradle classpath
Use a classpath based on the build.gradle (with the system classpath as the parent) for running Groovy code in Gade.

### Add build dir to classpath
Will also add the target/classes and target/test-classes dirs to the classpath

### Restart session after build
Whether to restart the session after each gradle build (so new classes etc. can be picked up)

### GRADLE_HOME
The location of your gradle installation used to run gradle. Will override any system property for GRADLE_HOME.

### Enable git integration
Whether to use git integration (git commands, syntax highlighting in the file view etc.) or not.

### Run global autorun.groovy on session init
Enable to run a file called autorun.groovy (if it exists) from the Gade install dir each time a session (re)starts.
It is just a normal Groovy script enabling you to set up session variables, define functions, read in data files etc.
Note that executing the script will give no output (unless there is some problem with the script) so print etc. will
not produce anything that can be seen.

### Run project autorun.groovy on session init
Run autorun.groovy from the project dir (working dir) each time a session (re)starts. Same as above but for the current project
dir.

### Add imports when running Groovy snippets
In Groovy, imports are not part of the engine session so when executing code line by line or marking an area and run it
will be problematic due to missing imports (Classes will not be found). When checked, Gade will add the import lines to the
code that is selected (or to the single line executed) so that the context for the classes is there. If you do not have this checked,
then you must use fully qualified class names if you want to be able to run single lines or sections of your code.