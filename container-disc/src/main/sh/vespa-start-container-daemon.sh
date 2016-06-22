#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if [ -z "${VESPA_HOME}" ]; then
    echo "Missing VESPA_HOME variable"
    exit 1
fi
if [ -z "${VESPA_SERVICE_NAME}" ]; then
    echo "Missing VESPA_SERVICE_NAME variable"
    exit 1
fi
if [ -z "${VESPA_CONFIG_ID}" ]; then
    echo "Missing VESPA_CONFIG_ID variable"
    exit 1
fi
cd ${VESPA_HOME} || { echo "Cannot cd to ${VESPA_HOME}" 1>&2; exit 1; }

DISCRIMINATOR=`echo ${VESPA_CONFIG_ID} | md5sum | cut -d' ' -f1`
CONTAINER_HOME="${VESPA_HOME}var/jdisc_container/${DISCRIMINATOR}/"

ZOOKEEPER_LOG_FILE="${VESPA_HOME}logs/vespa/zookeeper.${VESPA_SERVICE_NAME}.log"
rm -f $ZOOKEEPER_LOG_FILE*lck

# common setup
export VESPA_LOG_TARGET=file:${VESPA_HOME}logs/vespa/vespa.log
export VESPA_LOG_CONTROL_DIR=${VESPA_HOME}var/db/vespa/logcontrol
export LD_LIBRARY_PATH=${VESPA_HOME}lib64

cfpfile=${CONTAINER_HOME}/jdisc.properties
bundlecachedir=${CONTAINER_HOME}/bundlecache

export JAVAVM_LD_PRELOAD=
unset LD_PRELOAD

# class path
CP="${VESPA_HOME}lib/jars/jdisc_core-jar-with-dependencies.jar"

mkdir -p $bundlecachedir || exit 1
printenv > $cfpfile || exit 1

# ??? TODO ??? XXX ???
# LANG=en_US.utf8
# LC_ALL=C
# later, somewhere:
# export YELL_MA_EURO=INXIGHT

if [ "$PRELOAD" ]; then
    export JAVAVM_LD_PRELOAD="$PRELOAD"
    export LD_PRELOAD="$PRELOAD"
fi

exec java \
	-Xms1536m -Xmx1536m \
	-XX:MaxDirectMemorySize=267m \
	-XX:ThreadStackSize=512 \
	-XX:+UseConcMarkSweepGC \
	-XX:MaxTenuringThreshold=15 \
	-XX:NewRatio=1 \
	-XX:MaxJavaStackTraceDepth=-1 \
	-Dconfig.id="${VESPA_CONFIG_ID}" \
	-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath="${VESPA_HOME}var/crash" \
	-XX:OnOutOfMemoryError='kill -9 %p' \
	-Djava.library.path="${VESPA_HOME}lib64" \
	-Djava.awt.headless=true \
	-Dsun.rmi.dgc.client.gcInterval=3600000 \
	-Dsun.net.client.defaultConnectTimeout=5000 -Dsun.net.client.defaultReadTimeout=60000 \
	-Djavax.net.ssl.keyStoreType=JKS \
	-Djdisc.config.file="$cfpfile" \
	-Djdisc.export.packages= \
	-Djdisc.cache.path="$bundlecachedir" \
	-Djdisc.debug.resources=false \
	-Djdisc.bundle.path="${VESPA_HOME}lib/jars" \
	-Djdisc.logger.enabled=true \
	-Djdisc.logger.level=ALL \
	-Djdisc.logger.tag="${VESPA_CONFIG_ID}" \
	-Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.Jdk14Logger \
	-Dvespa.log.control.dir="${VESPA_LOG_CONTROL_DIR}" \
	-Dfile.encoding=UTF-8 \
	-Dzookeeperlogfile="${ZOOKEEPER_LOG_FILE}" \
	-cp "$CP" \
	com.yahoo.jdisc.core.StandaloneMain file:${VESPA_HOME}lib/jars/container-disc-jar-with-dependencies.jar
