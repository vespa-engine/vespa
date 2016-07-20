#!/bin/bash


if [ $# -ne 1 ]; then
  echo "Usage: $0 <install prefix>"
  exit 1
fi

declare -r PREFIX="$1"
declare -r INSTALLPATH="$DESTDIR/$PREFIX"

# BEGIN - Put this in post install script called by make install
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

mkdir -p $DESTDIR/$PREFIX/conf/configserver/
mkdir -p $DESTDIR/$PREFIX/conf/configserver-app/
mkdir -p $DESTDIR/$PREFIX/conf/configserver-app/config-models/
mkdir -p $DESTDIR/$PREFIX/conf/configserver-app/components/
mkdir -p $DESTDIR/$PREFIX/conf/filedistributor/
mkdir -p $DESTDIR/$PREFIX/conf/node-admin-app/
mkdir -p $DESTDIR/$PREFIX/conf/node-admin-app/components/
mkdir -p $DESTDIR/$PREFIX/conf/zookeeper/
mkdir -p $DESTDIR/$PREFIX/libexec/jdisc_core/
mkdir -p $DESTDIR/$PREFIX/libexec/vespa/modelplugins/
mkdir -p $DESTDIR/$PREFIX/libexec/vespa/plugins/qrs/
mkdir -p $DESTDIR/$PREFIX/libexec/yjava_daemon/bin/
mkdir -p $DESTDIR/$PREFIX/logs/jdisc_core/
mkdir -p $DESTDIR/$PREFIX/logs/vespa/
mkdir -p $DESTDIR/$PREFIX/logs/vespa/
mkdir -p $DESTDIR/$PREFIX/logs/vespa/configserver/
mkdir -p $DESTDIR/$PREFIX/logs/vespa/search/
mkdir -p $DESTDIR/$PREFIX/logs/vespa/qrs/
mkdir -p $DESTDIR/$PREFIX/share/vespa/
mkdir -p $DESTDIR/$PREFIX/share/vespa/schema/version/6.x/schema/
mkdir -p $DESTDIR/$PREFIX/tmp/vespa/
mkdir -p $DESTDIR/$PREFIX/var/db/jdisc/logcontrol/
mkdir -p $DESTDIR/$PREFIX/var/db/vespa/
mkdir -p $DESTDIR/$PREFIX/var/db/vespa/config_server/serverdb/configs/
mkdir -p $DESTDIR/$PREFIX/var/db/vespa/config_server/serverdb/configs/application/
mkdir -p $DESTDIR/$PREFIX/var/db/vespa/config_server/serverdb/applications/
mkdir -p $DESTDIR/$PREFIX/var/db/vespa/logcontrol/
mkdir -p $DESTDIR/$PREFIX/var/jdisc_container/
mkdir -p $DESTDIR/$PREFIX/var/jdisc_core/
mkdir -p $DESTDIR/$PREFIX/var/run/
mkdir -p $DESTDIR/$PREFIX/var/spool/vespa/
mkdir -p $DESTDIR/$PREFIX/var/spool/master/inbox/
mkdir -p $DESTDIR/$PREFIX/var/vespa/bundlecache/
mkdir -p $DESTDIR/$PREFIX/var/vespa/cache/config/
mkdir -p $DESTDIR/$PREFIX/var/vespa/cmdlines/
mkdir -p $DESTDIR/$PREFIX/var/zookeeper/version-2/

ln -s $PREFIX/lib/jars/config-model-fat.jar $DESTDIR/$PREFIX/conf/configserver-app/components/config-model-fat.jar
ln -s $PREFIX/lib/jars/configserver-jar-with-dependencies.jar $DESTDIR/$PREFIX/conf/configserver-app/components/configserver.jar
ln -s $PREFIX/lib/jars/orchestrator-jar-with-dependencies.jar $DESTDIR/$PREFIX/conf/configserver-app/components/orchestrator.jar
ln -s $PREFIX/lib/jars/node-repository-jar-with-dependencies.jar $DESTDIR/$PREFIX/conf/configserver-app/components/node-repository.jar
ln -s $PREFIX/lib/jars/zkfacade-jar-with-dependencies.jar $DESTDIR/$PREFIX/conf/configserver-app/components/zkfacade.jar
ln -s $PREFIX/conf/configserver-app/components $DESTDIR/$PREFIX/lib/jars/config-models
ln -s storaged-bin $DESTDIR/$PREFIX/sbin/distributord-bin




