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
