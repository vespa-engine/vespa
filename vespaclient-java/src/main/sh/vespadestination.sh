#!/bin/sh
test -z "$VESPA_HOME" && VESPA_HOME=/home/y

. $VESPA_HOME/libexec/vespa/common-env.sh

export MALLOC_ARENA_MAX=1 #Does not need fast allocation
exec java \
-server -enableassertions \
-XX:ThreadStackSize=512 \
-Djava.library.path=${VESPA_HOME}/libexec64/native:${VESPA_HOME}/lib64 \
-XX:MaxDirectMemorySize=32m -Djava.awt.headless=true $(getJavaOptionsIPV46) \
-cp ${VESPA_HOME}/lib/jars/vespaclient-java-jar-with-dependencies.jar:$CLASSPATH com.yahoo.dummyreceiver.DummyReceiver "$@"
