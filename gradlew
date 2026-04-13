#!/bin/sh

APP_HOME=$(cd "${0%/*}" && printf '%s\n' "$PWD")
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

die () {
    echo
    echo "$*"
    echo
    exit 1
}

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD=java
    command -v java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"

