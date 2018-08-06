#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

export SOURCE_DIR=/source
export NUM_THREADS=6
export MALLOC_ARENA_MAX=1
export MAVEN_OPTS="-Xms128m -Xmx2g"
source /etc/profile.d/devtoolset-7.sh || true

ccache --max-size=1250M
ccache --set-config=compression=true
ccache --print-config

cd ${SOURCE_DIR}
sh ./bootstrap.sh java
mvn install --no-snapshot-updates --batch-mode --threads ${NUM_THREADS} -Dmaven.javadoc.skip=true -Dmaven.test.skip=true
for i in $(seq 1 30); do
    echo -e "\n\n\n--> RUN ${i}"
    mvn --no-snapshot-updates --batch-mode --threads ${NUM_THREADS} -pl node-repository test '-Dtest=PeriodicApplicationMaintainerTest#application_deploy_inhibits_redeploy_for_a_while'
    echo -e "\n\n\n"
done
