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
elif [ "$1" = "default" ]; then
    :
elif [ "$1" = "-h" -o "$1" = "--help" ]; then
    usage
    exit 0
else
    echo "Unknown argument: $1" >&2
    usage
    exit 1
fi

mvn_install() {
    mvn install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true $@
}

# Generate vtag map
top=$(dirname $0)
$top/dist/getversion.pl -M $top > $top/dist/vtag.map

# These modules are required to be available to maven for it to calculate the
# Vespa java dependency graph.
MODULES="
  .
  annotations
  scalalib
  bundle-plugin
  "

for module in $MODULES; do
    (cd $module && mvn_install -N)
done

mvn_install -am -pl config-class-plugin -rf configgen

case "$MODE" in
    java)
        ;;
    full)
        mvn_install -am -pl filedistributionmanager,jrt,linguistics,messagebus -rf yolean
        ;;
    default)
        mvn_install -am -pl filedistributionmanager -rf yolean
        ;;
esac
