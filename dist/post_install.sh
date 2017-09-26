#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if [ $# -ne 1 ]; then
  echo "Usage: $0 <install prefix>"
  exit 1
fi

declare -r PREFIX="$1"
declare -r INSTALLPATH="$DESTDIR/$PREFIX"

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

ln -sf $PREFIX/lib/jars/config-model-fat.jar $INSTALLPATH/conf/configserver-app/components/config-model-fat.jar
ln -sf $PREFIX/lib/jars/configserver-jar-with-dependencies.jar $INSTALLPATH/conf/configserver-app/components/configserver.jar
ln -sf $PREFIX/lib/jars/orchestrator-jar-with-dependencies.jar $INSTALLPATH/conf/configserver-app/components/orchestrator.jar
ln -sf $PREFIX/lib/jars/node-repository-jar-with-dependencies.jar $INSTALLPATH/conf/configserver-app/components/node-repository.jar
ln -sf $PREFIX/lib/jars/zkfacade-jar-with-dependencies.jar $INSTALLPATH/conf/configserver-app/components/zkfacade.jar
ln -snf $PREFIX/conf/configserver-app/components $INSTALLPATH/lib/jars/config-models

