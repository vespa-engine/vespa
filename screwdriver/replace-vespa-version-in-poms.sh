#!/bin/bash

set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <Vespa version> <path>"
    exit 1
fi

readonly VESPA_VERSION=$1
readonly DIR=$2

find $DIR -name "pom.xml" -exec sed -i \
     -e "s,<version>.*SNAPSHOT.*</version>,<version>$VESPA_VERSION</version>," \
     -e "s,<vespaversion>.*project.version.*</vespaversion>,<vespaversion>$VESPA_VERSION</vespaversion>," \
     -e "s,<test-framework.version>.*project.version.*</test-framework.version>,<test-framework.version>$VESPA_VERSION</test-framework.version>," \
     {} \;
