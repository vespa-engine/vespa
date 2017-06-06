#!/bin/sh
test -z "$VESPA_HOME" && VESPA_HOME=/home/y

. $VESPA_HOME/libexec/vespa/common-env.sh

function help {
    echo "Usage: vds-document-statistics [ category, ... ]"
    echo "  Where category is one or more of: user, group, scheme, namespace"
    echo ""
    echo "vds-document-statistics generates documents counts based on one or more categories."
    exit 0
}
if [ "$1" == "-h" ]; then
  help
fi
if [ "$1" == "" ]; then
  help
fi
export MALLOC_ARENA_MAX=1 #Does not need fast allocation
exec java -Xms32m -Xmx128m $(getJavaOptionsIPV46) -cp ${VESPA_HOME}/lib/jars/vespaclient-java-jar-with-dependencies.jar com.yahoo.vespavisit.Main --statistics "$1"
