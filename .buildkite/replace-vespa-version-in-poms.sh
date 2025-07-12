#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: $(basename "$0") <Vespa version> <path>"
    exit 1
fi

readonly VESPA_VERSION=$1
readonly DIR=$2

# Fail if DIR does not exist or is not a directory
if [[ ! -d "$DIR" ]]; then
    echo "Directory $DIR does not exist or is not a directory."
    exit 1
fi

if [[ -z $(find -L "$DIR" -name "pom.xml") ]]; then
    echo "No pom.xml files found in $DIR"
    exit 0
fi

if [[ "$(uname)" == "Darwin" ]]; then
    SED_INPLACE="sed -i ''"
else
    SED_INPLACE="sed -i"
fi

find -L "$DIR" -name "pom.xml" -exec $SED_INPLACE \
     -e "s,<version>.*SNAPSHOT.*</version>,<version>$VESPA_VERSION</version>," \
     -e "s,<vespaversion>.*project.version.*</vespaversion>,<vespaversion>$VESPA_VERSION</vespaversion>," \
     -e "s,<test-framework.version>.*project.version.*</test-framework.version>,<test-framework.version>$VESPA_VERSION</test-framework.version>," \
     {} \;
