#!/bin/sh
test -z "$VESPA_HOME" && VESPA_HOME=/home/y

. $VESPA_HOME/libexec/vespa/common-env.sh

export VESPA_LOG_TARGET=file:/dev/null
export MALLOC_ARENA_MAX=1 # Does not need fast allocation
java \
-server -enableassertions \
-XX:ThreadStackSize=512 \
-XX:MaxJavaStackTraceDepth=-1 \
-Djava.library.path=${VESPA_HOME}/libexec64/native:${VESPA_HOME}/lib64 \
-XX:MaxDirectMemorySize=32m -Djava.awt.headless=true \
-Xms128m -Xmx1024m $(getJavaOptionsIPV46) \
-cp ${VESPA_HOME}/lib/jars/vespaclient-java-jar-with-dependencies.jar com.yahoo.vespasummarybenchmark.VespaSummaryBenchmark "$@"
