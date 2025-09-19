#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Generates a container tag name based on the provided arguments.

set -o errexit
set -o nounset
set -o pipefail

if [[ -n "${DEBUG:-}" ]]; then
    set -o xtrace
fi

if [[ $# -ne 2 ]]; then
    echo "Usage: $(basename "$0") <Vespa version> <path>"
    exit 1
fi

readonly VESPA_VERSION=$1
readonly DIR=$2

echo "--- 📝 Updating POM versions to $VESPA_VERSION"
# Fail if DIR does not exist or is not a directory
if [[ ! -d "$DIR" ]]; then
    echo "Directory $DIR does not exist or is not a directory."
    exit 1
fi

if [[ -z $(find -L "$DIR" -name "pom.xml") ]]; then
    echo "No pom.xml files found in $DIR"
    exit 0
fi

echo "Updating version strings in POM files..."
if [[ "$(uname)" == "Darwin" ]]; then
    SED_INPLACE=(sed -i '')
else
    SED_INPLACE=(sed -i)
fi

find -L "$DIR" -name "pom.xml" -exec "${SED_INPLACE[@]}" \
     -e "s,<version>.*SNAPSHOT.*</version>,<version>$VESPA_VERSION</version>," \
     -e "s,<vespaversion>.*project.version.*</vespaversion>,<vespaversion>$VESPA_VERSION</vespaversion>," \
     -e "s,<test-framework.version>.*project.version.*</test-framework.version>,<test-framework.version>$VESPA_VERSION</test-framework.version>," \
     {} \;
