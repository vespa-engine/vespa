#!/bin/bash

if [ $# -ne 1 ]; then
  echo "Usage: $0 <install prefix>"
  exit 1
fi

declare -r PREFIX="$1"
declare -r INSTALLPATH="$DESTDIR/$PREFIX"

# Rewrite config def file names
for path in $INSTALLPATH/var/db/vespa/config_server/serverdb/classes/*.def; do
    dir=$(dirname $path)
    filename=$(basename $path)
    namespace=$(grep '^ *namespace *=' $path | sed 's/ *namespace *= *//')
    if [ "$namespace" ]; then
        case $filename in
            $namespace.*)
                ;;
            *)
                mv $path $dir/$namespace.$filename ;;
        esac
    fi
done

mkdir -p $INSTALLDIR/conf/configserver/
mkdir -p $INSTALLDIR/conf/configserver-app/
mkdir -p $INSTALLDIR/conf/configserver-app/config-models/
mkdir -p $INSTALLDIR/conf/configserver-app/components/
mkdir -p $INSTALLDIR/conf/filedistributor/
mkdir -p $INSTALLDIR/conf/node-admin-app/
mkdir -p $INSTALLDIR/conf/node-admin-app/components/
mkdir -p $INSTALLDIR/conf/zookeeper/
mkdir -p $INSTALLDIR/libexec/jdisc_core/
mkdir -p $INSTALLDIR/libexec/vespa/modelplugins/
mkdir -p $INSTALLDIR/libexec/vespa/plugins/qrs/
mkdir -p $INSTALLDIR/libexec/yjava_daemon/bin/
mkdir -p $INSTALLDIR/logs/jdisc_core/
mkdir -p $INSTALLDIR/logs/vespa/
mkdir -p $INSTALLDIR/logs/vespa/
mkdir -p $INSTALLDIR/logs/vespa/configserver/
mkdir -p $INSTALLDIR/logs/vespa/search/
mkdir -p $INSTALLDIR/logs/vespa/qrs/
mkdir -p $INSTALLDIR/share/vespa/
mkdir -p $INSTALLDIR/share/vespa/schema/version/6.x/schema/
mkdir -p $INSTALLDIR/tmp/vespa/
mkdir -p $INSTALLDIR/var/db/jdisc/logcontrol/
mkdir -p $INSTALLDIR/var/db/vespa/
mkdir -p $INSTALLDIR/var/db/vespa/config_server/serverdb/configs/
mkdir -p $INSTALLDIR/var/db/vespa/config_server/serverdb/configs/application/
mkdir -p $INSTALLDIR/var/db/vespa/config_server/serverdb/applications/
mkdir -p $INSTALLDIR/var/db/vespa/logcontrol/
mkdir -p $INSTALLDIR/var/jdisc_container/
mkdir -p $INSTALLDIR/var/jdisc_core/
mkdir -p $INSTALLDIR/var/run/
mkdir -p $INSTALLDIR/var/spool/vespa/
mkdir -p $INSTALLDIR/var/spool/master/inbox/
mkdir -p $INSTALLDIR/var/vespa/bundlecache/
mkdir -p $INSTALLDIR/var/vespa/cache/config/
mkdir -p $INSTALLDIR/var/vespa/cmdlines/
mkdir -p $INSTALLDIR/var/zookeeper/version-2/

ln -s $PREFIX/lib/jars/config-model-fat.jar $INSTALLDIR/conf/configserver-app/components/config-model-fat.jar
ln -s $PREFIX/lib/jars/configserver-jar-with-dependencies.jar $INSTALLDIR/conf/configserver-app/components/configserver.jar
ln -s $PREFIX/lib/jars/orchestrator-jar-with-dependencies.jar $INSTALLDIR/conf/configserver-app/components/orchestrator.jar
ln -s $PREFIX/lib/jars/node-repository-jar-with-dependencies.jar $INSTALLDIR/conf/configserver-app/components/node-repository.jar
ln -s $PREFIX/lib/jars/zkfacade-jar-with-dependencies.jar $INSTALLDIR/conf/configserver-app/components/zkfacade.jar
ln -s $PREFIX/conf/configserver-app/components $INSTALLDIR/lib/jars/config-models
ln -s storaged-bin $INSTALLDIR/sbin/distributord-bin




