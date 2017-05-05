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
    # Build minial set of java modules requires to run mvn install from the source root
    MODE=java
elif [ "$1" = "java-build" ]; then
    # Bootstrap as with "java", then build by passing $2 $3 ... to mvn.
    MODE=java-build
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
# Therefore, we need to manually build all plugins first, then ensure we build
# the rest of module without rebuilding the plugins (mvn's -rf).  To be more
# exact, mvn displays the order of modules it will build in the Reactor summary
# - we need to build all modules up to and including all plugins in that list:
# . (i.e. parent), annotations, scalalib, bundle-plugin, yolean, vespajlib,
# configgen, and config-class-plugin.  By using mvn -rf we can combine the
# building of several of these into fewer commands to save time.
#
# After bootstrapping, the following mvn command must use -rf to avoid building
# the plugins again: java-build helps remembering this.  So to bootstrap the
# building of the orchestrator modules and all of its dependencies, do:
# 'bootstrap.sh java-build -pl orchestrator'.
#
# Why not just 'mvn_install -am -pl bundle-plugin'?  For unknown reasons, we
# need to use -N when building through Screwdriver.
MODULES="
  .
  annotations
  scalalib
  bundle-plugin
  "

for module in $MODULES; do
    (cd $module && mvn_install -N)
done

mvn_install -am -rf configgen -pl config-class-plugin

case "$MODE" in
    java*)
        mvn_install -am -rf yolean -pl vespajlib

        if test "$MODE" == java-build
        then
            shift
            mvn_install -rf config-lib -am "$@"
        fi
        ;;
    full)
        mvn_install -am -pl filedistributionmanager,jrt,linguistics,messagebus -rf yolean
        ;;
    default)
        mvn_install -am -pl filedistributionmanager -rf yolean
        ;;
esac
