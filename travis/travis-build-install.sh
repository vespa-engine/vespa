#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

source /source/travis/prelude.sh
source ${SOURCE_DIR}/travis/cpp-prelude.sh

cd ${SOURCE_DIR}
sh ./bootstrap.sh java
mvn install --no-snapshot-updates --batch-mode --threads ${NUM_THREADS} --quiet\
            -Dmaven.test.skip=true -Dmaven.javadoc.skip=true -Dmaven.source.skip=true
bash ${SOURCE_DIR}/bootstrap-cmake.sh ${SOURCE_DIR} "-DEXCLUDE_TESTS_FROM_ALL=TRUE"
make -j ${NUM_THREADS}
make install
