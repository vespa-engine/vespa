#!/bin/sh
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

java \
        -cp $VESPA_HOME/lib/jars/node-maintainer-jar-with-dependencies.jar \
        -Dvespa.log.target=file:$VESPA_HOME/logs/vespa/maintainer.log \
        "$@"
