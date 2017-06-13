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

mkdir -p $INSTALLPATH/conf/configserver/
mkdir -p $INSTALLPATH/conf/configserver-app/
mkdir -p $INSTALLPATH/conf/configserver-app/config-models/
mkdir -p $INSTALLPATH/conf/configserver-app/components/
mkdir -p $INSTALLPATH/conf/filedistributor/
mkdir -p $INSTALLPATH/conf/node-admin-app/
mkdir -p $INSTALLPATH/conf/node-admin-app/components/
mkdir -p $INSTALLPATH/conf/zookeeper/
mkdir -p $INSTALLPATH/libexec/jdisc_core/
mkdir -p $INSTALLPATH/libexec/vespa/modelplugins/
mkdir -p $INSTALLPATH/libexec/vespa/plugins/qrs/
mkdir -p $INSTALLPATH/libexec/yjava_daemon/bin/
mkdir -p $INSTALLPATH/logs/jdisc_core/
mkdir -p $INSTALLPATH/logs/vespa/
mkdir -p $INSTALLPATH/logs/vespa/
mkdir -p $INSTALLPATH/logs/vespa/configserver/
mkdir -p $INSTALLPATH/logs/vespa/search/
mkdir -p $INSTALLPATH/logs/vespa/qrs/
mkdir -p $INSTALLPATH/share/vespa/
mkdir -p $INSTALLPATH/share/vespa/schema/version/6.x/schema/
mkdir -p $INSTALLPATH/tmp/vespa/
mkdir -p $INSTALLPATH/var/db/jdisc/logcontrol/
mkdir -p $INSTALLPATH/var/db/vespa/
mkdir -p $INSTALLPATH/var/db/vespa/config_server/serverdb/configs/
mkdir -p $INSTALLPATH/var/db/vespa/config_server/serverdb/configs/application/
mkdir -p $INSTALLPATH/var/db/vespa/config_server/serverdb/applications/
mkdir -p $INSTALLPATH/var/db/vespa/logcontrol/
mkdir -p $INSTALLPATH/var/jdisc_container/
mkdir -p $INSTALLPATH/var/jdisc_core/
mkdir -p $INSTALLPATH/var/run/
mkdir -p $INSTALLPATH/var/spool/vespa/
mkdir -p $INSTALLPATH/var/spool/master/inbox/
mkdir -p $INSTALLPATH/var/vespa/bundlecache/
mkdir -p $INSTALLPATH/var/vespa/cache/config/
mkdir -p $INSTALLPATH/var/vespa/cmdlines/
mkdir -p $INSTALLPATH/var/zookeeper/version-2/
mkdir -p $INSTALLPATH/sbin

ln -s $PREFIX/lib/jars/config-model-fat.jar $INSTALLPATH/conf/configserver-app/components/config-model-fat.jar
ln -s $PREFIX/lib/jars/configserver-jar-with-dependencies.jar $INSTALLPATH/conf/configserver-app/components/configserver.jar
ln -s $PREFIX/lib/jars/orchestrator-jar-with-dependencies.jar $INSTALLPATH/conf/configserver-app/components/orchestrator.jar
ln -s $PREFIX/lib/jars/node-repository-jar-with-dependencies.jar $INSTALLPATH/conf/configserver-app/components/node-repository.jar
ln -s $PREFIX/lib/jars/zkfacade-jar-with-dependencies.jar $INSTALLPATH/conf/configserver-app/components/zkfacade.jar
ln -s $PREFIX/conf/configserver-app/components $INSTALLPATH/lib/jars/config-models
ln -s vespa-storaged-bin $INSTALLPATH/sbin/vespa-distributord-bin

# Temporary when renaming binaries in fnet
ln -s vespa-rpc-info $INSTALLPATH/bin/rpc_info
ln -s vespa-rpc-invoke $INSTALLPATH/bin/rpc_invoke
ln -s vespa-rpc-proxy $INSTALLPATH/bin/rpc_proxy

# Temporary when renaming binaries in fsa
ln -s vespa-fsadump $INSTALLPATH/bin/fsadump
ln -s vespa-fsainfo $INSTALLPATH/bin/fsainfo
ln -s vespa-makefsa $INSTALLPATH/bin/makefsa

# Temporary when renaming binaries in searchcore
ln -s vespa-proton-bin $INSTALLPATH/sbin/proton-bin
ln -s vespa-fdispatch-bin $INSTALLPATH/sbin/fdispatch-bin
ln -s vespa-verify-ranksetup-bin $INSTALLPATH/bin/verify_ranksetup-bin
ln -s vespa-proton $INSTALLPATH/sbin/proton
ln -s vespa-fdispatch $INSTALLPATH/sbin/fdispatch
ln -s vespa-verify-ranksetup $INSTALLPATH/bin/verify_ranksetup

# Temporary when renaming binaries in storageserver
ln -s vespa-storaged-bin $INSTALLPATH/sbin/storaged-bin
ln -s vespa-distributord-bin $INSTALLPATH/sbin/distributord-bin
ln -s vespa-storaged $INSTALLPATH/sbin/storaged
ln -s vespa-distributord $INSTALLPATH/sbin/distributord

# Temporary when renaming binaries in vespaclient
ln -s vespa-doclocator $INSTALLPATH/bin/vespadoclocator
ln -s vespa-route $INSTALLPATH/bin/vesparoute
ln -s vespa-spoolmaster $INSTALLPATH/bin/spoolmaster

# Temporary when renaming binaries in vespaclient-java
ln -s vespa-document-statistics $INSTALLPATH/bin/vds-document-statistics
ln -s vespa-stat $INSTALLPATH/bin/vdsstat
ln -s vespa-destination $INSTALLPATH/bin/vespadestination
ln -s vespa-feeder $INSTALLPATH/bin/vespafeeder
ln -s vespa-get $INSTALLPATH/bin/vespaget
ln -s vespa-visit $INSTALLPATH/bin/vespavisit
ln -s vespa-visit-target $INSTALLPATH/bin/vespavisittarget

# Temporary when renaming binaries in vespalog
ln -s vespa-log-conv $INSTALLPATH/bin/vlogconv
ln -s vespa-logctl $INSTALLPATH/bin/logctl
ln -s vespa-logfmt $INSTALLPATH/bin/logfmt
ln -s vespa-log-replay $INSTALLPATH/bin/vlogreplay

# Temporary when renaming binaries in memfilepersistence
ln -s vespa-dump-slotfile $INSTALLPATH/bin/dumpslotfile
ln -s vespa-vds-disktool-bin $INSTALLPATH/bin/vdsdisktool-bin
ln -s vespa-vds-disktool $INSTALLPATH/bin/vdsdisktool

# Temporary when renaming programs in config-model
ln -s vespa-deploy $INSTALLPATH/bin/deploy
ln -s vespa-deploy-application $INSTALLPATH/bin/deploy-application
ln -s vespa-expand-config.pl $INSTALLPATH/bin/expand-config.pl
ln -s vespa-validate-application $INSTALLPATH/bin/validate-application

# Temporary when renaming programs in config-model
ln -s vespa-activate-application $INSTALLPATH/bin/activate-application
ln -s vespa-configproxy-cmd $INSTALLPATH/bin/configproxy-cmd
ln -s vespa-get-config $INSTALLPATH/bin/getvespaconfig
ln -s vespa-get-config-bin $INSTALLPATH/bin/getvespaconfig-bin
ln -s vespa-ping-configproxy $INSTALLPATH/bin/pingproxy

# Temporary when renaming programs in fbench
ln -s vespa-fbench $INSTALLPATH/bin/fbench
ln -s vespa-fbench-filter-file $INSTALLPATH/bin/filterfile
ln -s vespa-geturl $INSTALLPATH/bin/geturl
ln -s vespa-fbench-split-file $INSTALLPATH/bin/splitfile

# Temporary when renaming programs in slobrok
ln -s vespa-slobrok $INSTALLPATH/bin/slobrok
ln -s vespa-slobrok-cmd $INSTALLPATH/bin/sbcmd

# Temporary when renaming programs in configd
ln -s vespa-run-as-vespa-user $INSTALLPATH/bin/run-as-yahoo
ln -s vespa-config-sentinel   $INSTALLPATH/sbin/config-sentinel
