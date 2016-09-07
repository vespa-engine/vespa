#!/bin/sh


if [ -z "$CLASSPATH" ]; then
    CLASSPATH=/home/y/lib/jars/node-admin-maintenance-jar-with-dependencies.jar:/home/y/lib/jars/docker-api-jar-with-dependencies.jar
fi

java \
        -cp $CLASSPATH \
        com.yahoo.vespa.hosted.node.maintenance.Maintainer "$@"