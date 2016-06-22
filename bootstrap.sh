#!/bin/bash -e
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

FULL=false

if [ "$1" = "full" ]; then
    FULL=true
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
  parent
  annotations
  scalalib
  bundle-plugin
  "

for module in $MODULES; do
    (cd $module && mvn_install)
done

mvn_install -am -pl config-class-plugin -rf configgen
if $FULL; then
    # Build all java modules required by C++ testing
    mvn_install -am -pl filedistributionmanager,jrt,linguistics,messagebus -rf yolean
else
    # Build minimal set of java modules required to run cmake
    mvn_install -am -pl filedistributionmanager -rf yolean
fi
