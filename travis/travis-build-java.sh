#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

source /source/travis/prelude.sh

cd "${SOURCE_DIR}"
export MAVEN_OPTS="-Xms128m -Xmx1g"
sh ./bootstrap.sh java
mvn install --no-snapshot-updates --batch-mode --threads ${NUM_THREADS}
