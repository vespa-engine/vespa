#!/bin/sh

java \
        -cp $VESPA_HOME/lib/jars/node-maintainer-jar-with-dependencies.jar \
        -Dvespa.log.target=file:$VESPA_HOME/logs/vespa/maintainer.log \
        "$@"
