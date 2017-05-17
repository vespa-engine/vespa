#!/bin/bash -e
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

usage() {
    echo "Usage: $0 [full | java | default]" >&2
}

if [ $# -eq 0 ]; then
    # Build minimal set of java modules required to run cmake
    MODE=default
elif [ "$1" = "full" ]; then
    # Build all java modules required by C++ testing
    MODE=full
elif [ "$1" = "java" ]; then
    # Build minimal set of java modules, then run mvn install with arguments $2,
    # $3, etc.
    MODE=java
elif [ "$1" = "default" ]; then
    MODE=default
elif [ "$1" = "-h" -o "$1" = "--help" ]; then
    usage
    exit 0
else
    echo "Unknown argument: $1" >&2
    usage
    exit 1
fi

mvn_install() {
    mvn install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true "$@"
}

# Generate vtag map
top=$(dirname $0)
$top/dist/getversion.pl -M $top > $top/dist/vtag.map

# NOTES ON BUILDING JAVA MODULES
#
# When building a random module like orchestrator, mvn lists 2 plugins as
# prerequisites in the Reactor summary: bundle-plugin and config-class-plugin.
#
# It appears mvn is unable to resolve references to a plugin, if the same mvn
# program builds the plugin, e.g. getting missing class errors.
#
# Therefore, we need to manually build all plugins first.
#
# The 'java' mode runs mvn install passing any arguments.  So
# to bootstrap the building of the orchestrator modules and all of its
# dependencies, do: 'bootstrap.sh java -pl orchestrator'.  To build everything
# do 'bootstrap.sh java'.

# must install parent pom first:
mvn_install -N

# and build plugins first:
mvn_install -f maven-plugins

# now everything else should just work with normal maven dependency resolution:

case "$MODE" in
    java)
        shift
        mvn_install -am "$@"
        ;;
    full)
        mvn_install -am -pl filedistributionmanager,jrt,linguistics,messagebus
        ;;
    default)
        mvn_install -am -pl filedistributionmanager
        ;;
esac
