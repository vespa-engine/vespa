#!/bin/bash -e
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Generate vtag map
top=$(dirname $0)
$top/dist/getversion.pl -M $top > $top/dist/vtag.map

# These modules are required to be available to maven for it to calculate the
# Vespa java dependency graph.
MODULES="
  parent
  configgen
  annotations
  scalalib
  bundle-plugin
  config-class-plugin
  yolean
  vespajlib
  filedistributionmanager"

for module in $MODULES; do
    (cd $module && mvn install -DskipTests -Dmaven.javadoc.skip=true)
done
