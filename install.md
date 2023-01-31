

# The env settings file
env.cmd in windows or env.sh in linux can be used to set additional configuration in Gade

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
