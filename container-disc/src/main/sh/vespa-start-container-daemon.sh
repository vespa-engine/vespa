#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#set -x

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

. libexec/vespa/common-env.sh

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

# class path
CP="${VESPA_HOME}lib/jars/jdisc_core-jar-with-dependencies.jar"

mkdir -p $bundlecachedir || exit 1
printenv > $cfpfile || exit 1

# ??? TODO ??? XXX ???
# LANG=en_US.utf8
# LC_ALL=C


getconfig() {
    qrstartcfg=""
    case "${VESPA_CONFIG_ID}" in
	dir:*)
            config_dir=${VESPA_CONFIG_ID#dir:}
	    qrstartcfg="`cat ${config_dir}/qr-start.cfg`"
	    ;;
	*)
            qrstartcfg="`getvespaconfig -w 10 -n search.config.qr-start -i ${VESPA_CONFIG_ID}`"
	    ;;
    esac
    cmds=`echo "$qrstartcfg" | perl -ne 's/^(\w+)\.(\w+) (.*)/$1_$2=$3/ && print'`
    eval "$cmds"
}

configure_memory() {
    consider_fallback jvm_heapsize 1536
    consider_fallback jvm_stacksize 512
    consider_fallback jvm_baseMaxDirectMemorySize 75
    consider_fallback jvm_directMemorySizeCache 0

    if (( jvm_heapSizeAsPercentageOfPhysicalMemory > 0 && jvm_heapSizeAsPercentageOfPhysicalMemory < 100 )); then
	available=`free -m | grep Mem | tr -s ' ' | cut -f2 -d' '`
	jvm_heapsize=$[available * jvm_heapSizeAsPercentageOfPhysicalMemory / 100]
	if (( jvm_heapsize < 1024 )); then
            jvm_heapsize=1024
	fi
    fi
    maxDirectMemorySize=$(( ${jvm_baseMaxDirectMemorySize} + ${jvm_heapsize}/8 + ${jvm_directMemorySizeCache} ))

    memory_options="-Xms${jvm_heapsize}m -Xmx${jvm_heapsize}m"
    memory_options="${memory_options} -XX:ThreadStackSize=${jvm_stacksize}"
    memory_options="${memory_options} -XX:MaxDirectMemorySize=${maxDirectMemorySize}m"    

    if [ "${VESPA_USE_HUGEPAGES}" ]; then
	memory_options="${memory_options} -XX:+UseLargePages"
    fi
}

configure_gcopts() {
    consider_fallback jvm_gcopts "-XX:+UseConcMarkSweepGC -XX:MaxTenuringThreshold=15 -XX:NewRatio=1"
    if [ "$jvm_verbosegc" = "true" ]; then
	jvm_gcopts="${jvm_gcopts} -verbose:gc"
    fi
}

configure_env_vars() {
    if [ "$qrs_env" ]; then
	for setting in ${qrs_env} ; do
	    case $setting in
		*"="*)
		    eval "$setting";
		    export ${setting%%=*}
		    ;;
		*)
		    echo "warning	ignoring invalid qrs_env setting '$setting' from '$qrs_env'"
		    ;;
	    esac
	done
    fi
}

configure_classpath () {
    if [ "${jdisc_classpath_extra}" ]; then
	CP="${CP}:${jdisc_classpath_extra}"
    fi
}

configure_preload () {
    export JAVAVM_LD_PRELOAD=
    unset LD_PRELOAD
    if [ "$PRELOAD" ]; then
	export JAVAVM_LD_PRELOAD="$PRELOAD"
	export LD_PRELOAD="$PRELOAD"
    fi
}

getconfig
configure_memory
configure_gcopts
configure_env_vars
configure_classpath
# note: should be last thing here:
configure_preload

exec java \
	-Dconfig.id="${VESPA_CONFIG_ID}" \
        ${memory_options} \
        ${jvm_gcopts} \
	-XX:MaxJavaStackTraceDepth=-1 \
	-XX:+HeapDumpOnOutOfMemoryError \
        -XX:HeapDumpPath="${VESPA_HOME}var/crash" \
	-XX:OnOutOfMemoryError='kill -9 %p' \
	-Djava.library.path="${VESPA_HOME}lib64" \
	-Djava.awt.headless=true \
	-Djavax.net.ssl.keyStoreType=JKS \
	-Dsun.rmi.dgc.client.gcInterval=3600000 \
	-Dsun.net.client.defaultConnectTimeout=5000 -Dsun.net.client.defaultReadTimeout=60000 \
	-Djdisc.config.file="$cfpfile" \
	-Djdisc.export.packages=${jdisc_export_packages} \
	-Djdisc.cache.path="$bundlecachedir" \
	-Djdisc.debug.resources=false \
	-Djdisc.bundle.path="${VESPA_HOME}lib/jars" \
	-Djdisc.logger.enabled=true \
	-Djdisc.logger.level=ALL \
	-Djdisc.logger.tag="${VESPA_CONFIG_ID}" \
	-Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.Jdk14Logger \
	-Dvespa.log.control.dir="${VESPA_LOG_CONTROL_DIR}" \
	-Dzookeeperlogfile="${ZOOKEEPER_LOG_FILE}" \
	-Dfile.encoding=UTF-8 \
	-cp "$CP" \
        "$@" \
	com.yahoo.jdisc.core.StandaloneMain file:${VESPA_HOME}lib/jars/container-disc-jar-with-dependencies.jar
