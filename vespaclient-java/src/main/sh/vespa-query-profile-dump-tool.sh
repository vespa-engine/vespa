#!/bin/sh
test -z "$VESPA_HOME" && VESPA_HOME=/home/y

. $VESPA_HOME/libexec/vespa/common-env.sh

java $(getJavaOptionsIPV46) -cp ${VESPA_HOME}/lib/jars/vespaclient-java-jar-with-dependencies.jar com.yahoo.search.query.profile.DumpTool $@
