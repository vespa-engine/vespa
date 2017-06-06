#!/bin/sh
test -z "$VESPA_HOME" && VESPA_HOME=/home/y

. $VESPA_HOME/libexec/vespa/common-env.sh

export MALLOC_ARENA_MAX=1 #Does not need fast allocation
exec java \
-server -enableassertions \
-XX:ThreadStackSize=512 \
-XX:MaxJavaStackTraceDepth=-1 \
-Djava.awt.headless=true \
-DVESPA_LOG_LEVEL="all -debug -spam -config -info -event" \
-Xms128m -Xmx1024m $(getJavaOptionsIPV46) \
-cp ${VESPA_HOME}/lib/jars/vespaclient-java-jar-with-dependencies.jar com.yahoo.vespaget.Main "$@"
