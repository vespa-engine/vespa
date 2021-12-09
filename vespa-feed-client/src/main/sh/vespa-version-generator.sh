#!/usr/bin/env bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Extracts the current version number (V_TAG_COMPONENT) from vtag.map and outputs this into a Java class.
# This replaces vespajlib/../VersionTagger.java as this module cannot depend on that, nor the dependencies
# of the resulting class.
#
# Author: bjorncs

source=$1
destination=$2
destinationDir=$(dirname $destination)

mkdir -p $destinationDir

versionNumber=$(cat $source | grep V_TAG_COMPONENT | awk '{print $2}' )

cat > $destination <<- END
package ai.vespa.feed.client.impl;

class Vespa {
    static final String VERSION = "$versionNumber";
}
END
